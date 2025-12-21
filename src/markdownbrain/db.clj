(ns markdownbrain.db
  (:require [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [next.jdbc.result-set :as rs]
            [markdownbrain.config :as config]
            [clojure.java.io :as io]
            [clojure.string :as str]))

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
  (let [schema (slurp (io/resource "migrations/001-initial-schema.sql"))
        statements (-> schema
                       (clojure.string/split #";")
                       (->> (map clojure.string/trim)
                            (filter #(not (clojure.string/blank? %)))))]
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

;; Document 操作
(defn upsert-document! [id tenant-id vault-id path content metadata hash mtime]
  (execute-one!
    ["INSERT INTO documents (id, tenant_id, vault_id, path, content, metadata, hash, mtime)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(vault_id, path) DO UPDATE SET
        content = excluded.content,
        metadata = excluded.metadata,
        hash = excluded.hash,
        mtime = excluded.mtime,
        updated_at = CURRENT_TIMESTAMP"
     id tenant-id vault-id path content metadata hash mtime]))

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
