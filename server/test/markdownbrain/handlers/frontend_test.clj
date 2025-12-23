(ns markdownbrain.handlers.frontend-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [markdownbrain.db :as db]
            [markdownbrain.utils :as utils]
            [markdownbrain.handlers.frontend :as frontend]
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
          response (frontend/home request)]
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
          response (frontend/home request)]
      (is (= 200 (:status response)))
      (is (string? (:body response)))))

  (testing "Non-existent domain returns 404"
    (let [request (-> (mock/request :get "/")
                     (assoc :headers {"host" "nonexistent.com"}))
          response (frontend/home request)]
      (is (= 404 (:status response)))
      (is (string? (:body response)))
      (is (clojure.string/includes? (:body response) "未找到站点"))))

  (testing "Missing Host header"
    (let [request (mock/request :get "/")
          response (frontend/home request)]
      ;; Depending on database state, could be 404 or 200
      (is (or (= 404 (:status response))
              (= 200 (:status response)))))))

;; List Documents 测试
(deftest test-list-documents
  (testing "List documents for valid domain"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          domain "blog.com"
          _ (db/create-vault! vault-id tenant-id "Blog" domain (utils/generate-uuid))
          _ (db/upsert-document! (utils/generate-uuid) tenant-id vault-id "doc1.md" "# Doc 1" "{}" "hash1" "2025-12-21T10:00:00Z")
          _ (db/upsert-document! (utils/generate-uuid) tenant-id vault-id "doc2.md" "# Doc 2" "{}" "hash2" "2025-12-21T11:00:00Z")
          request (-> (mock/request :get "/api/documents")
                     (assoc :headers {"host" domain}))
          response (frontend/get-documents request)]
      (is (= 200 (:status response)))
      (is (= 2 (count (:body response))))
      (is (every? #(contains? % :path) (:body response)))))

  (testing "Empty document list"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          domain "empty.com"
          _ (db/create-vault! vault-id tenant-id "Empty" domain (utils/generate-uuid))
          request (-> (mock/request :get "/api/documents")
                     (assoc :headers {"host" domain}))
          response (frontend/get-documents request)]
      (is (= 200 (:status response)))
      (is (= 0 (count (:body response))))))

  (testing "List documents for non-existent domain"
    (let [request (-> (mock/request :get "/api/documents")
                     (assoc :headers {"host" "nonexistent.com"}))
          response (frontend/get-documents request)]
      (is (= 404 (:status response)))
      (is (contains? (:body response) :error)))))

;; Get Document by ID 测试
(deftest test-get-document-by-id
  (testing "Get document with valid ID"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          domain "blog.com"
          _ (db/create-vault! vault-id tenant-id "Blog" domain (utils/generate-uuid))
          doc-id (utils/generate-uuid)
          content "# Document Content"
          _ (db/upsert-document! doc-id tenant-id vault-id "doc.md" content "{}" "hash" "2025-12-21T10:00:00Z")
          request (-> (mock/request :get (str "/api/documents/id/" doc-id))
                     (assoc :headers {"host" domain})
                     (assoc :path-params {:id doc-id}))
          response (frontend/get-document request)]
      (is (= 200 (:status response)))
      (is (= doc-id (:id (:body response))))
      (is (= content (:content (:body response))))))

  (testing "Get document with invalid ID"
    (let [request (-> (mock/request :get "/api/documents/id/invalid-id")
                     (assoc :headers {"host" "blog.com"})
                     (assoc :path-params {:id "invalid-id"}))
          response (frontend/get-document request)]
      (is (= 404 (:status response)))
      (is (contains? (:body response) :error)))))

;; Admin Pages 测试
(deftest test-admin-pages
  (testing "Admin home page renders"
    (let [request (mock/request :get "/admin")
          response (frontend/admin-home request)]
      (is (= 200 (:status response)))
      (is (string? (:body response)))))

  (testing "Login page renders"
    (let [request (mock/request :get "/admin/login")
          response (frontend/login-page request)]
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
          response (frontend/home request)]
      (is (= 200 (:status response)))))

  (testing "Parse domain with port"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          domain "exampleport.com"
          _ (db/create-vault! vault-id tenant-id "Site" domain (utils/generate-uuid))
          request (-> (mock/request :get "/")
                     (assoc :headers {"host" "exampleport.com:8080"}))
          response (frontend/home request)]
      (is (= 200 (:status response)))))

  (testing "Parse IPv4 address"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          domain "192.168.1.1"
          _ (db/create-vault! vault-id tenant-id "Site" domain (utils/generate-uuid))
          request (-> (mock/request :get "/")
                     (assoc :headers {"host" "192.168.1.1:3000"}))
          response (frontend/home request)]
      (is (= 200 (:status response)))))

  (testing "Parse localhost"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          domain "localhost"
          _ (db/create-vault! vault-id tenant-id "Local" domain (utils/generate-uuid))
          request (-> (mock/request :get "/")
                     (assoc :headers {"host" "localhost:3000"}))
          response (frontend/home request)]
      (is (= 200 (:status response))))))
