(ns markdownbrain.handlers.sync-test-utils
  "Shared test utilities for sync handler tests"
  (:require [markdownbrain.db :as db]
            [next.jdbc :as jdbc]
            [ring.mock.request :as mock]))

;; 测试数据库 fixture（临时文件 SQLite，启用外键）
(defn setup-test-db [f]
  (let [temp-file (java.io.File/createTempFile "test-db" ".db")
        _ (.deleteOnExit temp-file)
        db-path (.getPath temp-file)
        test-ds (delay (jdbc/get-datasource {:jdbcUrl (str "jdbc:sqlite:" db-path "?foreign_keys=ON")}))]
    (with-redefs [db/datasource test-ds]
      (db/init-db!)
      (f)
      (.delete temp-file))))

;; Helper: 创建带 Bearer token 的 note 同步请求
(defn sync-request-with-header
  [sync-key & {:keys [body]}]
  (-> (mock/request :post "/api/sync")
      (assoc :headers {"authorization" (str "Bearer " sync-key)})
      (assoc :body-params body)))

;; Helper: 创建带 Bearer token 的 asset 同步请求
(defn asset-sync-request
  [sync-key & {:keys [body]}]
  (-> (mock/request :post "/obsidian/assets/sync")
      (assoc :headers {"authorization" (str "Bearer " sync-key)})
      (assoc :body-params body)))

;; Helper: 创建带 Bearer token 的 full-sync 请求
(defn full-sync-request
  [sync-key & {:keys [body]}]
  (-> (mock/request :post "/obsidian/full-sync")
      (assoc :headers {"authorization" (str "Bearer " sync-key)})
      (assoc :body-params body)))
