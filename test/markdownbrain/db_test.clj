(ns markdownbrain.db-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [markdownbrain.utils :as utils]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [next.jdbc.result-set :as rs]
            [clojure.string :as str]))

;; 测试数据库（内存 SQLite）
;; For :memory: SQLite, we need to keep the same connection alive
(def ^:dynamic *conn* nil)

(defn create-tables! [conn]
  (jdbc/execute! conn
                 ["CREATE TABLE tenants (
                     id TEXT PRIMARY KEY,
                     name TEXT NOT NULL,
                     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"])
  (jdbc/execute! conn
                 ["CREATE TABLE users (
                     id TEXT PRIMARY KEY,
                     tenant_id TEXT NOT NULL,
                     username TEXT UNIQUE NOT NULL,
                     password_hash TEXT NOT NULL,
                     role TEXT DEFAULT 'admin',
                     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                     FOREIGN KEY (tenant_id) REFERENCES tenants(id))"])
  (jdbc/execute! conn
                 ["CREATE TABLE vaults (
                     id TEXT PRIMARY KEY,
                     tenant_id TEXT NOT NULL,
                     name TEXT NOT NULL,
                     domain TEXT UNIQUE,
                     sync_token TEXT UNIQUE NOT NULL,
                     domain_record TEXT,
                     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                     FOREIGN KEY (tenant_id) REFERENCES tenants(id))"])
  (jdbc/execute! conn
                 ["CREATE INDEX idx_vaults_domain ON vaults(domain)"])
  (jdbc/execute! conn
                 ["CREATE TABLE documents (
                     id TEXT PRIMARY KEY,
                     tenant_id TEXT NOT NULL,
                     vault_id TEXT NOT NULL,
                     path TEXT NOT NULL,
                     content TEXT,
                     metadata TEXT,
                     hash TEXT,
                     mtime TIMESTAMP,
                     updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                     UNIQUE(vault_id, path),
                     FOREIGN KEY (tenant_id) REFERENCES tenants(id),
                     FOREIGN KEY (vault_id) REFERENCES vaults(id) ON DELETE CASCADE)"])
  (jdbc/execute! conn
                 ["CREATE INDEX idx_documents_vault ON documents(vault_id)"])
  (jdbc/execute! conn
                 ["CREATE INDEX idx_documents_mtime ON documents(mtime)"]))

(defn setup-test-db [f]
  (let [db (jdbc/get-datasource {:dbtype "sqlite" :dbname ":memory:"})
        conn (jdbc/get-connection db)]
    (create-tables! conn)
    (binding [*conn* conn]
      (f))))

(use-fixtures :each setup-test-db)

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

;; 辅助函数
(defn execute-one! [sql-vec]
  (db-keys->clojure
    (jdbc/execute-one! *conn* sql-vec {:builder-fn rs/as-unqualified-lower-maps})))

(defn get-tenant [id]
  (execute-one! ["SELECT * FROM tenants WHERE id = ?" id]))

(defn create-tenant! [id name]
  (sql/insert! *conn* :tenants {:id id :name name} {:builder-fn rs/as-unqualified-lower-maps})
  ;; Return the created tenant
  (get-tenant id))

(defn get-user-by-username [username]
  (execute-one! ["SELECT * FROM users WHERE username = ?" username]))

(defn get-user-by-id [id]
  (execute-one! ["SELECT * FROM users WHERE id = ?" id]))

(defn create-user! [id tenant-id username password-hash]
  (sql/insert! *conn* :users
               {:id id :tenant_id tenant-id :username username :password_hash password-hash}
               {:builder-fn rs/as-unqualified-lower-maps})
  ;; Return the created user
  (get-user-by-id id))

(defn get-vault-by-id [id]
  (execute-one! ["SELECT * FROM vaults WHERE id = ?" id]))

(defn get-vault-by-domain [domain]
  (execute-one! ["SELECT * FROM vaults WHERE domain = ?" domain]))

(defn get-vault-by-sync-token [token]
  (execute-one! ["SELECT * FROM vaults WHERE sync_token = ?" token]))

(defn list-vaults-by-tenant [tenant-id]
  (jdbc/execute! *conn* ["SELECT * FROM vaults WHERE tenant_id = ?" tenant-id]
                 {:builder-fn rs/as-unqualified-lower-maps}))

(defn create-vault! [id tenant-id name domain sync-token domain-record]
  (sql/insert! *conn* :vaults
               {:id id :tenant_id tenant-id :name name :domain domain
                :sync_token sync-token :domain_record domain-record}
               {:builder-fn rs/as-unqualified-lower-maps})
  ;; Return the created vault
  (get-vault-by-id id))

(defn get-document [id]
  (execute-one! ["SELECT * FROM documents WHERE id = ?" id]))

(defn get-document-by-path [vault-id path]
  (execute-one! ["SELECT * FROM documents WHERE vault_id = ? AND path = ?" vault-id path]))

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
    id tenant-id vault-id path content metadata hash mtime])
  ;; Return the document
  (get-document-by-path vault-id path))

(defn delete-document! [vault-id path]
  (execute-one! ["DELETE FROM documents WHERE vault_id = ? AND path = ?" vault-id path]))

(defn list-documents-by-vault [vault-id]
  (jdbc/execute! *conn* ["SELECT * FROM documents WHERE vault_id = ?" vault-id]
                 {:builder-fn rs/as-unqualified-lower-maps}))

;; Tenant 测试
(deftest test-create-tenant
  (testing "Create tenant successfully"
    (let [tenant-id (utils/generate-uuid)
          tenant-name "Test Org"
          result (create-tenant! tenant-id tenant-name)]
      (is (map? result))
      (is (= tenant-id (:id result)))
      (is (= tenant-name (:name result))))))

;; User 测试
(deftest test-create-user
  (testing "Create user successfully"
    (let [tenant-id (utils/generate-uuid)
          _ (create-tenant! tenant-id "Test Org")
          user-id (utils/generate-uuid)
          username "admin"
          password-hash "hashed-password"
          result (create-user! user-id tenant-id username password-hash)]
      (is (map? result))
      (is (= user-id (:id result)))
      (is (= username (:username result)))))

  (testing "Get user by username"
    (let [tenant-id (utils/generate-uuid)
          _ (create-tenant! tenant-id "Test Org")
          user-id (utils/generate-uuid)
          username "testuser"
          _ (create-user! user-id tenant-id username "hash")
          result (get-user-by-username username)]
      (is (map? result))
      (is (= username (:username result)))))

  (testing "Get user by ID"
    (let [tenant-id (utils/generate-uuid)
          _ (create-tenant! tenant-id "Test Org")
          user-id (utils/generate-uuid)
          _ (create-user! user-id tenant-id "user" "hash")
          result (get-user-by-id user-id)]
      (is (map? result))
      (is (= user-id (:id result))))))

;; Vault 测试
(deftest test-create-vault
  (testing "Create vault successfully"
    (let [tenant-id (utils/generate-uuid)
          _ (create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          vault-name "My Blog"
          domain "blog.test.com"
          sync-token (utils/generate-uuid)
          dns-record "DNS info"
          result (create-vault! vault-id tenant-id vault-name domain sync-token dns-record)]
      (is (map? result))
      (is (= vault-id (:id result)))
      (is (= vault-name (:name result)))
      (is (= domain (:domain result)))))

  (testing "Get vault by ID"
    (let [tenant-id (utils/generate-uuid)
          _ (create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          sync-token (utils/generate-uuid)
          _ (create-vault! vault-id tenant-id "Blog" "blog-id.com" sync-token "dns")
          result (get-vault-by-id vault-id)]
      (is (map? result))
      (is (= vault-id (:id result)))))

  (testing "Get vault by domain"
    (let [tenant-id (utils/generate-uuid)
          _ (create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          domain "unique.test.com"
          sync-token (utils/generate-uuid)
          _ (create-vault! vault-id tenant-id "Blog" domain sync-token "dns")
          result (get-vault-by-domain domain)]
      (is (map? result))
      (is (= domain (:domain result)))))

  (testing "Get vault by sync token"
    (let [tenant-id (utils/generate-uuid)
          _ (create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          sync-token (utils/generate-uuid)
          _ (create-vault! vault-id tenant-id "Blog" "blog-sync.com" sync-token "dns")
          result (get-vault-by-sync-token sync-token)]
      (is (map? result))
      (is (= sync-token (:sync-token result)))))

  (testing "List vaults by tenant"
    (let [tenant-id (utils/generate-uuid)
          _ (create-tenant! tenant-id "Test Org")
          vault-id-1 (utils/generate-uuid)
          vault-id-2 (utils/generate-uuid)
          sync-token-1 (utils/generate-uuid)
          sync-token-2 (utils/generate-uuid)
          _ (create-vault! vault-id-1 tenant-id "Blog 1" "blog1.com" sync-token-1 "dns1")
          _ (create-vault! vault-id-2 tenant-id "Blog 2" "blog2.com" sync-token-2 "dns2")
          results (list-vaults-by-tenant tenant-id)]
      (is (= 2 (count results)))
      (is (every? map? results)))))

;; Document 测试
(deftest test-upsert-document
  (testing "Insert new document"
    (let [tenant-id (utils/generate-uuid)
          _ (create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          sync-token (utils/generate-uuid)
          _ (create-vault! vault-id tenant-id "Blog" "blog-insert.com" sync-token "dns")
          doc-id (utils/generate-uuid)
          path "test.md"
          content "# Test"
          metadata "{\"tags\":[]}"
          hash "abc123"
          mtime "2025-12-21T10:00:00Z"
          result (upsert-document! doc-id tenant-id vault-id path content metadata hash mtime)]
      (is (map? result))
      (is (= path (:path result)))))

  (testing "Update existing document"
    (let [tenant-id (utils/generate-uuid)
          _ (create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          sync-token (utils/generate-uuid)
          _ (create-vault! vault-id tenant-id "Blog" "blog-update.com" sync-token "dns")
          doc-id (utils/generate-uuid)
          path "test.md"
          _ (upsert-document! doc-id tenant-id vault-id path "# Old" "{}" "old" "2025-12-21T10:00:00Z")
          new-content "# New"
          result (upsert-document! (utils/generate-uuid) tenant-id vault-id path new-content "{}" "new" "2025-12-21T11:00:00Z")
          fetched (get-document-by-path vault-id path)]
      (is (= new-content (:content fetched))))))

(deftest test-delete-document
  (testing "Delete document"
    (let [tenant-id (utils/generate-uuid)
          _ (create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          sync-token (utils/generate-uuid)
          _ (create-vault! vault-id tenant-id "Blog" "blog-delete.com" sync-token "dns")
          doc-id (utils/generate-uuid)
          path "to-delete.md"
          _ (upsert-document! doc-id tenant-id vault-id path "# Delete Me" "{}" "hash" "2025-12-21T10:00:00Z")
          _ (delete-document! vault-id path)
          result (get-document-by-path vault-id path)]
      (is (nil? result)))))

(deftest test-list-documents
  (testing "List documents by vault"
    (let [tenant-id (utils/generate-uuid)
          _ (create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          sync-token (utils/generate-uuid)
          _ (create-vault! vault-id tenant-id "Blog" "blog-list.com" sync-token "dns")
          _ (upsert-document! (utils/generate-uuid) tenant-id vault-id "doc1.md" "# Doc 1" "{}" "hash1" "2025-12-21T10:00:00Z")
          _ (upsert-document! (utils/generate-uuid) tenant-id vault-id "doc2.md" "# Doc 2" "{}" "hash2" "2025-12-21T11:00:00Z")
          results (list-documents-by-vault vault-id)]
      (is (= 2 (count results)))
      (is (every? #(contains? % :path) results)))))

(deftest test-get-document
  (testing "Get document by ID"
    (let [tenant-id (utils/generate-uuid)
          _ (create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          sync-token (utils/generate-uuid)
          _ (create-vault! vault-id tenant-id "Blog" "blog-get.com" sync-token "dns")
          doc-id (utils/generate-uuid)
          path "test.md"
          content "# Test Document"
          _ (upsert-document! doc-id tenant-id vault-id path content "{}" "hash" "2025-12-21T10:00:00Z")
          result (get-document doc-id)]
      (is (map? result))
      (is (= doc-id (:id result)))
      (is (= content (:content result)))))

  (testing "Get non-existent document"
    (let [result (get-document "non-existent-id")]
      (is (nil? result)))))
