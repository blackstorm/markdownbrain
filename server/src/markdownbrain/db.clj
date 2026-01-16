(ns markdownbrain.db
  (:require [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [next.jdbc.result-set :as rs]
            [markdownbrain.config :as config]
            [markdownbrain.utils :as utils]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]))

(def datasource
  (delay
    (jdbc/get-datasource (config/get-config :database))))

(defn underscore->kebab
  [k]
  (keyword (str/replace (name k) "_" "-")))

(defn map-keys
  [f m]
  (when m
    (into {} (map (fn [[k v]] [(f k) v]) m))))

(defn db-keys->clojure
  [m]
  (map-keys underscore->kebab m))

(defn db-keys-coll->clojure
  [coll]
  (map db-keys->clojure coll))

(defn execute! [sql-vec]
  (db-keys-coll->clojure
    (jdbc/execute! @datasource sql-vec {:builder-fn rs/as-unqualified-lower-maps})))

(defn execute-one! [sql-vec]
  (db-keys->clojure
    (jdbc/execute-one! @datasource sql-vec {:builder-fn rs/as-unqualified-lower-maps})))

(defn insert-with-builder!
  [table data]
  (db-keys->clojure
    (sql/insert! @datasource table data {:builder-fn rs/as-unqualified-lower-maps})))

(defn find-by
  [table column value]
  (execute-one! [(str "SELECT * FROM " (name table) " WHERE " (name column) " = ?") value]))

(defn find-all-by
  [table column value & {:keys [order-by]}]
  (let [order-clause (if order-by (str " ORDER BY " (name order-by)) "")]
    (execute! [(str "SELECT * FROM " (name table) " WHERE " (name column) " = ?" order-clause) value])))

(defn- extract-db-path-from-jdbc-url
  "Extract database file path from JDBC URL.
   e.g., jdbc:sqlite:data/markdownbrain.db?journal_mode=WAL -> data/markdownbrain.db"
  [jdbc-url]
  (when jdbc-url
    (-> jdbc-url
        (str/replace #"^jdbc:sqlite:" "")
        (str/replace #"\?.*$" ""))))

(defn- ensure-db-directory!
  "Ensure the parent directory of the database file exists."
  []
  (let [jdbc-url (config/get-config :database :jdbcUrl)
        db-path (extract-db-path-from-jdbc-url jdbc-url)
        db-file (io/file db-path)
        parent-dir (.getParentFile db-file)]
    (when (and parent-dir (not (.exists parent-dir)))
      (log/info "Creating database directory:" (.getPath parent-dir))
      (.mkdirs parent-dir))))

(defn init-db! []
  (ensure-db-directory!)
  (let [schema (slurp (io/resource "migrations/001-initial-schema.sql"))
        statements (-> schema
                       (clojure.string/split #";")
                       (->> (map clojure.string/trim)
                            (filter #(not (clojure.string/blank? %)))))]
    (log/info "Running schema migration")
    (doseq [stmt statements]
      (jdbc/execute! @datasource [stmt]))))

;; Tenant 操作
(defn create-tenant! [id name]
  (insert-with-builder! :tenants {:id id :name name}))

(defn get-tenant [id]
  (find-by :tenants :id id))

;; User 操作
(defn create-user! [id tenant-id username password-hash]
  (insert-with-builder! :users
                       {:id id
                        :tenant_id tenant-id
                        :username username
                        :password_hash password-hash}))

(defn get-user-by-username [username]
  (find-by :users :username username))

(defn get-user-by-id [id]
  (find-by :users :id id))

(def ^:private has-user-cache (atom nil))

(defn has-any-user? []
  (if-let [cached @has-user-cache]
    cached
    (let [result (> (:count (execute-one! ["SELECT COUNT(*) as count FROM users"])) 0)]
      (when result (reset! has-user-cache true))
      result)))

;; Vault 操作
(defn create-vault! [id tenant-id name domain sync-key]
  (insert-with-builder! :vaults
                       {:id id
                        :tenant_id tenant-id
                        :name name
                        :domain domain
                        :sync_key sync-key}))

(defn get-vault-by-id [id]
  (find-by :vaults :id id))

(defn get-vault-by-domain [domain]
  (find-by :vaults :domain domain))

(defn get-vault-by-sync-key [sync-key]
  (find-by :vaults :sync_key sync-key))

(defn list-vaults-by-tenant [tenant-id]
  (find-all-by :vaults :tenant_id tenant-id :order-by :created_at))

(defn delete-vault! [id]
  (execute-one! ["DELETE FROM vaults WHERE id = ?" id]))

(defn update-vault-root-note! [vault-id root-note-id]
  (execute-one! ["UPDATE vaults SET root_note_id = ? WHERE id = ?" root-note-id vault-id]))

(defn update-vault! [vault-id name domain]
  (execute-one! ["UPDATE vaults SET name = ?, domain = ? WHERE id = ?"
                 name domain vault-id]))

(defn update-vault-sync-key! [vault-id new-sync-key]
  "Update the sync key for a vault."
  (execute-one! ["UPDATE vaults SET sync_key = ? WHERE id = ?"
                 new-sync-key vault-id]))

;; Note 操作
(defn upsert-note! [id tenant-id vault-id path client-id content metadata hash mtime]
  (execute-one!
    ["INSERT INTO notes (id, tenant_id, vault_id, path, client_id, content, metadata, hash, mtime)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(vault_id, client_id) DO UPDATE SET
        path = excluded.path,
        content = excluded.content,
        metadata = excluded.metadata,
        hash = excluded.hash,
        mtime = excluded.mtime,
        updated_at = CURRENT_TIMESTAMP"
     id tenant-id vault-id path client-id content metadata hash mtime]))

(defn delete-note-by-client-id! [vault-id client-id]
  (execute-one! ["DELETE FROM notes WHERE vault_id = ? AND client_id = ?" vault-id client-id]))

(defn update-note-path! [vault-id client-id new-path]
  (execute-one! ["UPDATE notes SET path = ?, updated_at = CURRENT_TIMESTAMP
                  WHERE vault_id = ? AND client_id = ?"
                 new-path vault-id client-id]))

(defn delete-note! [vault-id path]
  (execute-one! ["DELETE FROM notes WHERE vault_id = ? AND path = ?" vault-id path]))

(defn get-note [id]
  (find-by :notes :id id))

(defn list-notes-by-vault [vault-id]
  (execute! ["SELECT client_id, path, hash, mtime FROM notes
              WHERE vault_id = ?
              ORDER BY path ASC" vault-id]))

(defn get-notes-for-link-resolution
  [vault-id]
  (execute! ["SELECT client_id, path FROM notes WHERE vault_id = ?" vault-id]))

(defn get-note-by-path [vault-id path]
  (execute-one! ["SELECT * FROM notes WHERE vault_id = ? AND path = ?" vault-id path]))

(defn get-note-by-client-id [vault-id client-id]
  (execute-one! ["SELECT * FROM notes WHERE vault_id = ? AND client_id = ?" vault-id client-id]))

(defn get-note-for-frontend
  [vault-id client-id]
  (execute-one! ["SELECT * FROM notes WHERE vault_id = ? AND client_id = ?" vault-id client-id]))

(defn search-notes-by-vault [vault-id query]
  (execute! ["SELECT id, client_id, path, content, metadata, mtime
              FROM notes
              WHERE vault_id = ? AND (path LIKE ? OR content LIKE ?)
              ORDER BY path ASC
              LIMIT 50"
             vault-id
             (str "%" query "%")
             (str "%" query "%")]))

;; Note Links 操作
(defn delete-note-links-by-source! [vault-id source-client-id]
  (log/debug "Deleting all links from source:" source-client-id)
  (let [result (execute-one! ["DELETE FROM note_links WHERE vault_id = ? AND source_client_id = ?"
                              vault-id source-client-id])]
    (log/debug "Delete result:" result)
    result))

(defn delete-note-link-by-target! [vault-id source-client-id target-client-id]
  (log/debug "Deleting specific link - source:" source-client-id "target:" target-client-id)
  (let [result (execute-one! ["DELETE FROM note_links WHERE vault_id = ? AND source_client_id = ? AND target_client_id = ?"
                              vault-id source-client-id target-client-id])]
    (log/debug "Delete result:" result)
    result))

(defn insert-note-link! [vault-id source-client-id target-client-id target-path link-type display-text original]
  (log/debug "Inserting note link:")
  (log/debug "  vault-id:" vault-id)
  (log/debug "  source-client-id:" source-client-id)
  (log/debug "  target-client-id:" target-client-id)
  (log/debug "  target-path:" target-path)
  (log/debug "  link-type:" link-type)
  (log/debug "  display-text:" display-text)
  (log/debug "  original:" original)
  (let [id (utils/generate-uuid)]
    (log/debug "Generated link id:" id)
    (let [data {:id id
                :vault_id vault-id
                :source_client_id source-client-id
                :target_client_id target-client-id
                :target_path target-path
                :link_type link-type
                :display_text display-text
                :original original}]
      (log/debug "Insert data:" data)
      (try
        (let [result (insert-with-builder! :note_links data)]
          (log/debug "Insert result:" result)
          result)
        (catch Exception e
          (log/error "Failed to insert note link:" (.getMessage e))
          (log/error "SQL Exception details:" e)
          (throw e))))))

(defn get-note-links [vault-id client-id]
  (execute! ["SELECT * FROM note_links WHERE vault_id = ? AND source_client_id = ?"
             vault-id client-id]))

(defn get-backlinks-with-notes [vault-id client-id]
  (execute! ["SELECT n.*, nl.display_text as link_display_text, nl.link_type
              FROM note_links nl
              INNER JOIN notes n ON n.vault_id = nl.vault_id AND n.client_id = nl.source_client_id
              WHERE nl.vault_id = ? AND nl.target_client_id = ?
              ORDER BY n.path ASC"
             vault-id client-id]))

;; Full Sync - 孤儿笔记清理操作
(defn list-note-client-ids-by-vault
  [vault-id]
  (execute! ["SELECT client_id FROM notes WHERE vault_id = ?" vault-id]))

(defn delete-notes-not-in-list!
  "删除不在列表中的笔记（孤儿笔记清理）
   采用服务端对比方案：
   1. 查询服务端所有 client_ids
   2. 计算差集找出孤儿
   3. 分批删除（避免 SQLite 参数限制）"
  [vault-id client-ids]
  (if (empty? client-ids)
    (do
      (log/warn "Full sync empty list - deleting ALL notes in vault:" vault-id)
      (execute-one! ["DELETE FROM notes WHERE vault_id = ?" vault-id]))
    (let [client-id-set (set client-ids)
          server-ids (map :client-id (list-note-client-ids-by-vault vault-id))
          orphan-ids (remove client-id-set server-ids)
          batch-size 500]
      (log/info "Deleting orphan notes - vault-id:" vault-id 
                "server:" (count server-ids) 
                "client:" (count client-ids) 
                "orphans:" (count orphan-ids))
      (when (seq orphan-ids)
        (doseq [batch (partition-all batch-size orphan-ids)]
          (let [placeholders (str/join "," (repeat (count batch) "?"))
                sql (str "DELETE FROM notes WHERE vault_id = ? AND client_id IN (" placeholders ")")]
            (execute-one! (into [sql vault-id] batch)))))
      {:update-count (count orphan-ids)})))

(defn delete-orphan-links!
  [vault-id]
  (log/info "Deleting orphan links - vault-id:" vault-id)
  (execute-one!
    ["DELETE FROM note_links
      WHERE vault_id = ?
      AND NOT EXISTS (
        SELECT 1 FROM notes
        WHERE notes.vault_id = note_links.vault_id
        AND notes.client_id = note_links.target_client_id
      )" vault-id]))

;; Vault Logo 操作
(defn update-vault-logo! [vault-id logo-object-key]
  (execute-one! ["UPDATE vaults SET logo_object_key = ? WHERE id = ?" logo-object-key vault-id]))

;; Asset 操作
(defn upsert-asset!
  [id tenant-id vault-id client-id path object-key size-bytes content-type sha256]
  (execute-one!
    ["INSERT INTO assets (id, tenant_id, vault_id, client_id, path, object_key, size_bytes, content_type, sha256, deleted_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)
      ON CONFLICT(vault_id, client_id) DO UPDATE SET
        path = excluded.path,
        size_bytes = excluded.size_bytes,
        content_type = excluded.content_type,
        sha256 = excluded.sha256,
        deleted_at = NULL,
        updated_at = strftime('%s', 'now')"
     id tenant-id vault-id client-id path object-key size-bytes content-type sha256]))

(defn soft-delete-asset! [vault-id client-id]
  (execute-one!
    ["UPDATE assets SET deleted_at = strftime('%s', 'now'), updated_at = strftime('%s', 'now')
      WHERE vault_id = ? AND client_id = ? AND deleted_at IS NULL"
     vault-id client-id]))

(defn get-asset-by-client-id [vault-id client-id]
  (execute-one!
    ["SELECT * FROM assets WHERE vault_id = ? AND client_id = ? AND deleted_at IS NULL"
     vault-id client-id]))

(defn get-asset-by-path [vault-id path]
  (execute-one!
    ["SELECT * FROM assets WHERE vault_id = ? AND path = ? AND deleted_at IS NULL"
     vault-id path]))

(defn get-asset-by-filename [vault-id filename]
  (execute-one!
    ["SELECT * FROM assets WHERE vault_id = ? AND path LIKE ? AND deleted_at IS NULL LIMIT 1"
     vault-id (str "%" filename)]))

(defn find-asset
  [vault-id path]
  (or (get-asset-by-path vault-id path)
      (get-asset-by-filename vault-id (str "/" path))))

(defn list-assets-by-vault [vault-id]
  (execute!
    ["SELECT client_id, path, sha256, size_bytes FROM assets
      WHERE vault_id = ? AND deleted_at IS NULL
      ORDER BY path ASC"
     vault-id]))

(defn delete-assets-not-in-list!
  [vault-id client-ids]
  (if (empty? client-ids)
    (do
      (log/warn "Full asset sync empty list - soft deleting ALL assets in vault:" vault-id)
      (execute-one!
        ["UPDATE assets SET deleted_at = strftime('%s', 'now'), updated_at = strftime('%s', 'now')
          WHERE vault_id = ? AND deleted_at IS NULL"
         vault-id]))
    (let [client-id-set (set client-ids)
          server-assets (list-assets-by-vault vault-id)
          server-client-ids (map :client-id server-assets)
          orphan-client-ids (remove client-id-set server-client-ids)
          batch-size 500]
      (log/info "Soft deleting orphan assets - vault-id:" vault-id
                "server:" (count server-client-ids)
                "client:" (count client-ids)
                "orphans:" (count orphan-client-ids))
      (when (seq orphan-client-ids)
        (doseq [batch (partition-all batch-size orphan-client-ids)]
          (let [placeholders (str/join "," (repeat (count batch) "?"))
                sql (str "UPDATE assets SET deleted_at = strftime('%s', 'now'), updated_at = strftime('%s', 'now')
                          WHERE vault_id = ? AND client_id IN (" placeholders ") AND deleted_at IS NULL")]
            (execute-one! (into [sql vault-id] batch)))))
      {:update-count (count orphan-client-ids)})))

(defn get-vault-storage-size
  [vault-id]
  (let [result (execute-one!
                 ["SELECT COALESCE(SUM(size_bytes), 0) as total_bytes
                   FROM assets
                   WHERE vault_id = ? AND deleted_at IS NULL"
                  vault-id])]
    (:total-bytes result)))

;; Note Asset Refs 操作
(defn upsert-note-asset-ref! [vault-id note-client-id asset-client-id]
  (let [id (utils/generate-uuid)]
    (execute-one!
      ["INSERT INTO note_asset_refs (id, vault_id, note_client_id, asset_client_id)
        VALUES (?, ?, ?, ?)
        ON CONFLICT(vault_id, note_client_id, asset_client_id) DO NOTHING"
       id vault-id note-client-id asset-client-id])))

(defn delete-note-asset-refs-by-note! [vault-id note-client-id]
  (execute-one!
    ["DELETE FROM note_asset_refs WHERE vault_id = ? AND note_client_id = ?"
     vault-id note-client-id]))

(defn get-asset-refs-by-note [vault-id note-client-id]
  (execute!
    ["SELECT * FROM note_asset_refs WHERE vault_id = ? AND note_client_id = ?"
     vault-id note-client-id]))

(defn get-asset-refs-by-asset [vault-id asset-client-id]
  (execute!
    ["SELECT * FROM note_asset_refs WHERE vault_id = ? AND asset_client_id = ?"
     vault-id asset-client-id]))

(defn count-asset-refs [vault-id asset-client-id]
  (let [result (execute-one!
                 ["SELECT COUNT(*) as count FROM note_asset_refs 
                   WHERE vault_id = ? AND asset_client_id = ?"
                  vault-id asset-client-id])]
    (:count result)))

(defn update-note-asset-refs!
  [vault-id note-client-id new-asset-client-ids]
  (delete-note-asset-refs-by-note! vault-id note-client-id)
  (doseq [asset-client-id new-asset-client-ids]
    (upsert-note-asset-ref! vault-id note-client-id asset-client-id)))

(defn mark-orphan-assets!
  [vault-id]
  (execute-one!
    ["UPDATE assets 
      SET deleted_at = strftime('%s', 'now'), updated_at = strftime('%s', 'now')
      WHERE vault_id = ? 
        AND deleted_at IS NULL
        AND client_id NOT IN (
          SELECT DISTINCT asset_client_id FROM note_asset_refs WHERE vault_id = ?
        )"
     vault-id vault-id]))
