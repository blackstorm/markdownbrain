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

;; 键名转换辅助函数
(defn underscore->kebab
  "将下划线转换为连字符 (snake_case -> kebab-case)"
  [k]
  (keyword (str/replace (name k) "_" "-")))

(defn map-keys
  "对map的所有键应用函数f"
  [f m]
  (when m
    (into {} (map (fn [[k v]] [(f k) v]) m))))

(defn db-keys->clojure
  "将数据库键名 (snake_case) 转换为 Clojure 键名 (kebab-case)"
  [m]
  (map-keys underscore->kebab m))

(defn db-keys-coll->clojure
  "将数据库键名集合转换为 Clojure 键名"
  [coll]
  (map db-keys->clojure coll))

(defn execute! [sql-vec]
  (db-keys-coll->clojure
    (jdbc/execute! @datasource sql-vec {:builder-fn rs/as-unqualified-lower-maps})))

(defn execute-one! [sql-vec]
  (db-keys->clojure
    (jdbc/execute-one! @datasource sql-vec {:builder-fn rs/as-unqualified-lower-maps})))

;; 通用辅助函数
(defn insert-with-builder!
  "插入数据，使用统一的 builder 配置"
  [table data]
  (db-keys->clojure
    (sql/insert! @datasource table data {:builder-fn rs/as-unqualified-lower-maps})))

(defn find-by
  "通用的单条记录查询函数"
  [table column value]
  (execute-one! [(str "SELECT * FROM " (name table) " WHERE " (name column) " = ?") value]))

(defn find-all-by
  "通用的多条记录查询函数"
  [table column value & {:keys [order-by]}]
  (let [order-clause (if order-by (str " ORDER BY " (name order-by)) "")]
    (execute! [(str "SELECT * FROM " (name table) " WHERE " (name column) " = ?" order-clause) value])))

;; 初始化数据库
(defn init-db! []
  (let [migrations ["migrations/001-initial-schema.sql"]]
    (doseq [migration migrations]
      (let [schema (slurp (io/resource migration))
            statements (-> schema
                           (clojure.string/split #";")
                           (->> (map clojure.string/trim)
                                (filter #(not (clojure.string/blank? %)))))]
        (println "Running migration:" migration)
        (doseq [stmt statements]
          (jdbc/execute! @datasource [stmt]))))))

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

(defn has-any-user? []
  "检查数据库中是否有任何用户"
  (let [result (execute-one! ["SELECT COUNT(*) as count FROM users"])]
    (> (:count result) 0)))

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

(defn update-vault-root-doc! [vault-id root-doc-id]
  "更新 vault 的首页文档 ID"
  (execute-one! ["UPDATE vaults SET root_doc_id = ? WHERE id = ?" root-doc-id vault-id]))

;; Document 操作
(defn upsert-document! [id tenant-id vault-id path client-id content metadata hash mtime]
  (execute-one!
    ["INSERT INTO documents (id, tenant_id, vault_id, path, client_id, content, metadata, hash, mtime)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(vault_id, client_id) DO UPDATE SET
        path = excluded.path,
        content = excluded.content,
        metadata = excluded.metadata,
        hash = excluded.hash,
        mtime = excluded.mtime,
        updated_at = CURRENT_TIMESTAMP"
     id tenant-id vault-id path client-id content metadata hash mtime]))

(defn delete-document-by-client-id! [vault-id client-id]
  (execute-one! ["DELETE FROM documents WHERE vault_id = ? AND client_id = ?" vault-id client-id]))

(defn delete-document! [vault-id path]
  (execute-one! ["DELETE FROM documents WHERE vault_id = ? AND path = ?" vault-id path]))

(defn get-document [id]
  (find-by :documents :id id))

(defn list-documents-by-vault [vault-id]
  (execute! ["SELECT id, path, hash, mtime FROM documents
              WHERE vault_id = ?
              ORDER BY path ASC" vault-id]))

(defn get-document-by-path [vault-id path]
  (execute-one! ["SELECT * FROM documents WHERE vault_id = ? AND path = ?" vault-id path]))

(defn get-document-by-client-id [vault-id client-id]
  (execute-one! ["SELECT * FROM documents WHERE vault_id = ? AND client_id = ?" vault-id client-id]))

(defn search-documents-by-vault [vault-id query]
  "搜索 vault 中的文档，支持按路径和内容搜索"
  (execute! ["SELECT client_id as clientId, path, content, metadata, mtime
              FROM documents
              WHERE vault_id = ? AND (path LIKE ? OR content LIKE ?)
              ORDER BY path ASC
              LIMIT 50"
             vault-id
             (str "%" query "%")
             (str "%" query "%")]))

;; Document Links 操作
(defn delete-document-links-by-source! [vault-id source-client-id]
  "删除指定源文档的所有链接"
  (log/debug "Deleting all links - vault-id:" vault-id "source-client-id:" source-client-id)
  (let [result (execute-one! ["DELETE FROM document_links WHERE vault_id = ? AND source_client_id = ?"
                              vault-id source-client-id])]
    (log/debug "Delete result:" result)
    result))

(defn delete-document-link-by-target! [vault-id source-client-id target-client-id]
  "删除指定源文档到指定目标文档的链接"
  (log/debug "Deleting specific link - source:" source-client-id "target:" target-client-id)
  (let [result (execute-one! ["DELETE FROM document_links WHERE vault_id = ? AND source_client_id = ? AND target_client_id = ?"
                              vault-id source-client-id target-client-id])]
    (log/debug "Delete result:" result)
    result))

(defn insert-document-link! [vault-id source-client-id target-client-id target-path link-type display-text original]
  "插入文档链接关系"
  (log/debug "Inserting document link:")
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
        (let [result (insert-with-builder! :document_links data)]
          (log/debug "Insert result:" result)
          result)
        (catch Exception e
          (log/error "Failed to insert document link:" (.getMessage e))
          (log/error "SQL Exception details:" e)
          (throw e))))))

(defn get-document-links [vault-id client-id]
  "获取文档的所有链接（出链）"
  (execute! ["SELECT * FROM document_links WHERE vault_id = ? AND source_client_id = ?"
             vault-id client-id]))

(defn get-document-backlinks [vault-id client-id]
  "获取文档的所有反向链接（入链）"
  (execute! ["SELECT * FROM document_links WHERE vault_id = ? AND target_client_id = ?"
             vault-id client-id]))
