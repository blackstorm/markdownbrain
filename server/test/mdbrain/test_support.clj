(ns mdbrain.test-support
  (:require [mdbrain.db :as db]
            [mdbrain.utils :as utils]
            [next.jdbc :as jdbc]
            [ring.mock.request :as mock]
            [selmer.parser :as selmer]))

;; 测试数据库设置

(defn create-temp-db-fixture
  "创建临时 SQLite 数据库 fixture"
  []
  (fn [f]
    (let [temp-file (java.io.File/createTempFile "test-db" ".db")
          _ (.deleteOnExit temp-file)
          test-ds (delay (jdbc/get-datasource {:dbtype "sqlite" :dbname (.getPath temp-file)}))]
      (with-redefs [db/datasource test-ds
                    selmer/render-file (fn [_ data] (str "Mocked template: " data))]
        (db/init-db!)
        (f)
        (.delete temp-file)))))

(defn create-temp-db-fixture-no-selmer
  "创建临时 SQLite 数据库 fixture (不模拟 Selmer)"
  []
  (fn [f]
    (let [temp-file (java.io.File/createTempFile "test-db" ".db")
          _ (.deleteOnExit temp-file)
          test-ds (delay (jdbc/get-datasource {:dbtype "sqlite" :dbname (.getPath temp-file)}))]
      (with-redefs [db/datasource test-ds]
        (db/init-db!)
        (f)
        (.delete temp-file)))))

;; 测试请求构建辅助函数

(defn authenticated-request
  "构建带认证的请求"
  [method uri tenant-id user-id & {:keys [body]}]
  (let [req (mock/request method uri)]
    (cond-> req
      true (assoc :session {:tenant-id tenant-id :user-id user-id})
      body (assoc :body-params body))))

;; 测试数据创建辅助函数

(defn create-test-tenant!
  "创建测试租户"
  ([]
   (let [tenant-id (utils/generate-uuid)]
     (db/create-tenant! tenant-id "Test Org")
     tenant-id))
  ([name]
   (let [tenant-id (utils/generate-uuid)]
     (db/create-tenant! tenant-id name)
     tenant-id)))

(defn create-test-user!
  "创建测试用户"
  ([tenant-id username password]
   (let [user-id (utils/generate-uuid)
         password-hash (utils/hash-password password)]
     (db/create-user! user-id tenant-id username password-hash)
     user-id))
  ([tenant-id username]
   (create-test-user! tenant-id username "password123")))

(defn create-test-vault!
  "创建测试 vault"
  ([tenant-id name domain]
   (let [vault-id (utils/generate-uuid)
         sync-key (utils/generate-uuid)]
     (db/create-vault! vault-id tenant-id name domain sync-key)
     {:vault-id vault-id
      :sync-key sync-key}))
  ([tenant-id domain]
   (create-test-vault! tenant-id "Test Vault" domain)))

 
