(ns markdownbrain.handlers.frontend-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [markdownbrain.db :as db]
            [markdownbrain.utils :as utils]
            [markdownbrain.handlers.frontend :as frontend]
            [markdownbrain.handlers.admin :as admin]
            [next.jdbc :as jdbc]
            [ring.mock.request :as mock]
            [selmer.parser :as selmer]))

;; 测试数据库（临时文件 SQLite）
(defn setup-test-db [f]
  (let [temp-file (java.io.File/createTempFile "test-db" ".db")
        _ (.deleteOnExit temp-file)
        test-ds (delay (jdbc/get-datasource {:dbtype "sqlite" :dbname (.getPath temp-file)}))]
    (with-redefs [db/datasource test-ds
                  selmer/render-file (fn [_ data] (str "Mocked template: " data))]
      (db/init-db!)
      (f)
      (.delete temp-file))))

(use-fixtures :each setup-test-db)

;; Get Vault by Domain 测试
(deftest test-get-vault-by-domain
  (testing "Get vault with valid domain from Host header"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          domain "myblog.com"
          _ (db/create-vault! vault-id tenant-id "My Blog" domain (utils/generate-uuid))
          request (-> (mock/request :get "/")
                     (assoc :headers {"host" domain}))
          response (frontend/get-doc request)]
      (is (= 200 (:status response)))
      (is (string? (:body response)))
      (is (clojure.string/includes? (:body response) "My Blog"))))

  (testing "Get vault with port in Host header"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          domain "localhost"
          _ (db/create-vault! vault-id tenant-id "Local Blog" domain (utils/generate-uuid))
          request (-> (mock/request :get "/")
                     (assoc :headers {"host" "localhost:3000"}))
          response (frontend/get-doc request)]
      (is (= 200 (:status response)))
      (is (string? (:body response)))))

  (testing "Non-existent domain returns 404"
    (let [request (-> (mock/request :get "/")
                     (assoc :headers {"host" "nonexistent.com"}))
          response (frontend/get-doc request)]
      (is (= 404 (:status response)))
      (is (string? (:body response)))
      (is (clojure.string/includes? (:body response) "未找到站点"))))

  (testing "Missing Host header"
    (let [request (mock/request :get "/")
          response (frontend/get-doc request)]
      ;; Depending on database state, could be 404 or 200
      (is (or (= 404 (:status response))
              (= 200 (:status response)))))))

;; Admin Pages 测试
(deftest test-admin-pages
  (testing "Admin home page renders"
    (let [request (mock/request :get "/admin")
          response (admin/admin-home request)]
      (is (= 200 (:status response)))
      (is (string? (:body response)))))

  (testing "Login page renders"
    (let [request (mock/request :get "/admin/login")
          response (admin/login-page request)]
      (is (= 200 (:status response)))
      (is (string? (:body response))))))

;; Domain Parsing 测试
(deftest test-domain-parsing
  (testing "Parse domain without port"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          domain "example.com"
          _ (db/create-vault! vault-id tenant-id "Site" domain (utils/generate-uuid))
          request (-> (mock/request :get "/")
                     (assoc :headers {"host" "example.com"}))
          response (frontend/get-doc request)]
      (is (= 200 (:status response)))))

  (testing "Parse domain with port"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          domain "exampleport.com"
          _ (db/create-vault! vault-id tenant-id "Site" domain (utils/generate-uuid))
          request (-> (mock/request :get "/")
                     (assoc :headers {"host" "exampleport.com:8080"}))
          response (frontend/get-doc request)]
      (is (= 200 (:status response)))))

  (testing "Parse IPv4 address"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          domain "192.168.1.1"
          _ (db/create-vault! vault-id tenant-id "Site" domain (utils/generate-uuid))
          request (-> (mock/request :get "/")
                     (assoc :headers {"host" "192.168.1.1:3000"}))
          response (frontend/get-doc request)]
      (is (= 200 (:status response)))))

  (testing "Parse localhost"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          domain "localhost"
          _ (db/create-vault! vault-id tenant-id "Local" domain (utils/generate-uuid))
          request (-> (mock/request :get "/")
                     (assoc :headers {"host" "localhost:3000"}))
          response (frontend/get-doc request)]
      (is (= 200 (:status response))))))
