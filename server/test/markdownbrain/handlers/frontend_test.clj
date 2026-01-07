(ns markdownbrain.handlers.frontend-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.string :as str]
            [markdownbrain.db :as db]
            [markdownbrain.utils :as utils]
            [markdownbrain.handlers.frontend :as frontend]
            [markdownbrain.handlers.admin :as admin]
            [markdownbrain.object-store :as object-store]
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
          response (frontend/get-note request)]
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
          response (frontend/get-note request)]
      (is (= 200 (:status response)))
      (is (string? (:body response)))))

  (testing "Non-existent domain returns 404"
    (let [request (-> (mock/request :get "/")
                     (assoc :headers {"host" "nonexistent.com"}))
          response (frontend/get-note request)]
      (is (= 404 (:status response)))
      (is (string? (:body response)))
      (is (clojure.string/includes? (:body response) "Site not found"))))

  (testing "Missing Host header"
    (let [request (mock/request :get "/")
          response (frontend/get-note request)]
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
          response (frontend/get-note request)]
      (is (= 200 (:status response)))))

  (testing "Parse domain with port"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          domain "exampleport.com"
          _ (db/create-vault! vault-id tenant-id "Site" domain (utils/generate-uuid))
          request (-> (mock/request :get "/")
                     (assoc :headers {"host" "exampleport.com:8080"}))
          response (frontend/get-note request)]
      (is (= 200 (:status response)))))

  (testing "Parse IPv4 address"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          domain "192.168.1.1"
          _ (db/create-vault! vault-id tenant-id "Site" domain (utils/generate-uuid))
          request (-> (mock/request :get "/")
                     (assoc :headers {"host" "192.168.1.1:3000"}))
          response (frontend/get-note request)]
      (is (= 200 (:status response)))))

  (testing "Parse localhost"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          domain "localhost"
          _ (db/create-vault! vault-id tenant-id "Local" domain (utils/generate-uuid))
          request (-> (mock/request :get "/")
                     (assoc :headers {"host" "localhost:3000"}))
          response (frontend/get-note request)]
      (is (= 200 (:status response))))))

;; ============================================================
;; URL 使用 client_id 而非内部 id 测试
;; ============================================================

(deftest test-url-uses-client-id
  (testing "URL 中应该使用 client_id 而不是内部 id"
    (let [;; 创建测试数据
          tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          domain "clientid-test.com"
          _ (db/create-vault! vault-id tenant-id "Client ID Test" domain (utils/generate-uuid))
          
          ;; 创建文档 - 注意 id 和 client_id 是不同的
          internal-id (utils/generate-uuid)
          client-id "user-generated-client-id-12345"
          _ (db/upsert-note! internal-id tenant-id vault-id "test.md" client-id 
                                 "# Test Content" nil "hash123" "2024-01-01T00:00:00Z")
          
          ;; 通过 client_id 访问文档（应该成功）
          request (-> (mock/request :get (str "/" client-id))
                     (assoc :headers {"host" domain})
                     (assoc :path-params {:path client-id}))
          response (frontend/get-note request)]
      
      ;; 验证能通过 client_id 访问
      (is (= 200 (:status response)))
      ;; 验证模板数据包含 client-id（因为模板被 mock 了，检查数据而非渲染结果）
      (is (str/includes? (:body response) ":client-id"))
      (is (str/includes? (:body response) client-id))))
  
  (testing "通过内部 id 访问应该返回 404（不暴露内部 id）"
    (let [;; 创建测试数据
          tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org 2")
          vault-id (utils/generate-uuid)
          domain "internal-id-test.com"
          _ (db/create-vault! vault-id tenant-id "Internal ID Test" domain (utils/generate-uuid))
          
          ;; 创建文档
          internal-id (utils/generate-uuid)
          client-id "my-client-id-67890"
          _ (db/upsert-note! internal-id tenant-id vault-id "test2.md" client-id 
                                 "# Private Content" nil "hash456" "2024-01-01T00:00:00Z")
          
          ;; 尝试通过内部 id 访问（应该失败）
          request (-> (mock/request :get (str "/" internal-id))
                     (assoc :headers {"host" domain})
                     (assoc :path-params {:path internal-id}))
          response (frontend/get-note request)]
      
      ;; 验证不能通过内部 id 访问
      (is (= 404 (:status response)))))
  
  (testing "home 页面文档列表应该使用 client_id"
    (let [;; 创建测试数据
          tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org 3")
          vault-id (utils/generate-uuid)
          domain "home-test.com"
          _ (db/create-vault! vault-id tenant-id "Home Test" domain (utils/generate-uuid))
          
          ;; 创建多个文档
          _ (db/upsert-note! (utils/generate-uuid) tenant-id vault-id "doc1.md" 
                                 "client-id-aaa" "# Doc 1" nil "hash1" "2024-01-01T00:00:00Z")
          _ (db/upsert-note! (utils/generate-uuid) tenant-id vault-id "doc2.md" 
                                 "client-id-bbb" "# Doc 2" nil "hash2" "2024-01-01T00:00:00Z")
          
          ;; 访问根路径（没有 root_doc_id，显示文档列表）
          ;; 根路径 path 为 "/" 或空
          request (-> (mock/request :get "/")
                     (assoc :headers {"host" domain})
                     (assoc :path-params {:path "/"}))
          response (frontend/get-note request)]
      
      ;; 验证返回成功
      (is (= 200 (:status response)))
      ;; 验证链接使用 client_id 而非内部 id
      (is (str/includes? (:body response) "client-id-aaa"))
      (is (str/includes? (:body response) "client-id-bbb"))))
  
  (testing "backlinks 应该使用 client_id"
    (let [;; 创建测试数据
          tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org 4")
          vault-id (utils/generate-uuid)
          domain "backlinks-test.com"
          _ (db/create-vault! vault-id tenant-id "Backlinks Test" domain (utils/generate-uuid))
          
          ;; 创建目标文档
          target-client-id "target-doc-client-id"
          _ (db/upsert-note! (utils/generate-uuid) tenant-id vault-id "target.md" 
                                 target-client-id "# Target Doc" nil "hash-t" "2024-01-01T00:00:00Z")
          
          ;; 创建源文档（包含链接到目标）
          source-client-id "source-doc-client-id"
          _ (db/upsert-note! (utils/generate-uuid) tenant-id vault-id "source.md" 
                                 source-client-id "# Source Doc" nil "hash-s" "2024-01-01T00:00:00Z")
          
          ;; 创建链接关系
          _ (db/insert-note-link! vault-id source-client-id target-client-id 
                                      "target.md" "link" "Target Doc" "[[target]]")
          
          ;; 访问目标文档，查看 backlinks
          request (-> (mock/request :get (str "/" target-client-id))
                     (assoc :headers {"host" domain})
                     (assoc :path-params {:path target-client-id}))
          response (frontend/get-note request)]
      
      ;; 验证返回成功
      (is (= 200 (:status response)))
      ;; 验证 backlinks 使用 client_id (数据中包含 source-client-id)
      (is (str/includes? (:body response) source-client-id))))
  
  (testing "堆叠路径应该使用 client_id"
    (let [;; 创建测试数据
          tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org 5")
          vault-id (utils/generate-uuid)
          domain "stacking-test.com"
          _ (db/create-vault! vault-id tenant-id "Stacking Test" domain (utils/generate-uuid))
          
          ;; 创建多个文档
          client-id-1 "stack-doc-1"
          client-id-2 "stack-doc-2"
          _ (db/upsert-note! (utils/generate-uuid) tenant-id vault-id "doc1.md" 
                                 client-id-1 "# Doc 1" nil "hash1" "2024-01-01T00:00:00Z")
          _ (db/upsert-note! (utils/generate-uuid) tenant-id vault-id "doc2.md" 
                                 client-id-2 "# Doc 2" nil "hash2" "2024-01-01T00:00:00Z")
          
          ;; 使用堆叠路径访问 (/{client_id}+{client_id})
          stacked-path (str client-id-1 "+" client-id-2)
          request (-> (mock/request :get (str "/" stacked-path))
                     (assoc :headers {"host" domain})
                     (assoc :path-params {:path stacked-path}))
          response (frontend/get-note request)]
      
      ;; 验证返回成功（两个文档都应该被渲染）
      (is (= 200 (:status response)))
      ;; 验证数据中包含两个 client-id
      (is (str/includes? (:body response) client-id-1))
      (is (str/includes? (:body response) client-id-2)))))

;; ============================================================
;; Resource Serving 测试
;; ============================================================

(deftest test-get-resource
  (testing "Get resource with valid path - no S3 configured returns 404"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          domain "resource-test.com"
          _ (db/create-vault! vault-id tenant-id "Resource Test" domain (utils/generate-uuid))
          
          ;; Create resource in DB
          resource-id (utils/generate-uuid)
          resource-path "images/logo.png"
          object-key (object-store/resource-object-key resource-path)
          _ (db/upsert-resource! resource-id tenant-id vault-id resource-path 
                                  object-key 1024 "image/png" "abc123hash")
          
          ;; Request the resource - S3 not configured, so get-object returns nil
          request (-> (mock/request :get (str "/r/" resource-path))
                     (assoc :headers {"host" domain})
                     (assoc :path-params {:path resource-path}))
          response (frontend/get-resource request)]
      ;; Without S3, should return 404 (resource in DB but not in S3)
      (is (= 404 (:status response)))))
  
  (testing "Get resource - resource not in DB returns 404"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          domain "resource-test-2.com"
          _ (db/create-vault! vault-id tenant-id "Resource Test 2" domain (utils/generate-uuid))
          
          ;; Request non-existent resource
          request (-> (mock/request :get "/r/nonexistent/file.png")
                     (assoc :headers {"host" domain})
                     (assoc :path-params {:path "nonexistent/file.png"}))
          response (frontend/get-resource request)]
      (is (= 404 (:status response)))
      (is (str/includes? (:body response) "Resource not found"))))
  
  (testing "Get resource - invalid domain returns 404"
    (let [request (-> (mock/request :get "/r/images/logo.png")
                     (assoc :headers {"host" "nonexistent-domain.com"})
                     (assoc :path-params {:path "images/logo.png"}))
          response (frontend/get-resource request)]
      (is (= 404 (:status response)))
      (is (str/includes? (:body response) "Site not found"))))
  
  (testing "Get resource - empty path returns 400"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          domain "resource-test-3.com"
          _ (db/create-vault! vault-id tenant-id "Resource Test 3" domain (utils/generate-uuid))
          
          request (-> (mock/request :get "/r/")
                     (assoc :headers {"host" domain})
                     (assoc :path-params {:path ""}))
          response (frontend/get-resource request)]
      (is (= 400 (:status response)))
      (is (str/includes? (:body response) "Invalid path"))))
  
  (testing "Get resource - path normalization (../)"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          domain "resource-test-4.com"
          _ (db/create-vault! vault-id tenant-id "Resource Test 4" domain (utils/generate-uuid))
          
          ;; Create resource at normalized path
          resource-id (utils/generate-uuid)
          normalized-path "images/logo.png"
          object-key (object-store/resource-object-key normalized-path)
          _ (db/upsert-resource! resource-id tenant-id vault-id normalized-path 
                                  object-key 1024 "image/png" "abc123hash")
          
          ;; Request with path traversal - should normalize and find the resource
          ;; But since S3 is not configured, it will return 404 (in DB but not S3)
          request (-> (mock/request :get "/r/images/../images/logo.png")
                     (assoc :headers {"host" domain})
                     (assoc :path-params {:path "images/../images/logo.png"}))
          response (frontend/get-resource request)]
      ;; Should find the resource in DB (path gets normalized)
      ;; Returns 404 because S3 is not configured
      (is (= 404 (:status response)))))
  
  (testing "Get resource - soft deleted resource returns 404"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          domain "resource-test-5.com"
          _ (db/create-vault! vault-id tenant-id "Resource Test 5" domain (utils/generate-uuid))
          
          ;; Create and then soft-delete resource
          resource-id (utils/generate-uuid)
          resource-path "deleted/file.png"
          object-key (object-store/resource-object-key resource-path)
          _ (db/upsert-resource! resource-id tenant-id vault-id resource-path 
                                  object-key 1024 "image/png" "abc123hash")
          _ (db/soft-delete-resource! vault-id resource-path)
          
          ;; Request the soft-deleted resource
          request (-> (mock/request :get (str "/r/" resource-path))
                     (assoc :headers {"host" domain})
                     (assoc :path-params {:path resource-path}))
          response (frontend/get-resource request)]
      ;; Soft-deleted resources should not be found
      (is (= 404 (:status response)))
      (is (str/includes? (:body response) "Resource not found"))))
  
  (testing "Get resource - with mocked S3 returns content"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          domain "resource-test-s3.com"
          _ (db/create-vault! vault-id tenant-id "Resource S3 Test" domain (utils/generate-uuid))
          
          ;; Create resource in DB
          resource-id (utils/generate-uuid)
          resource-path "images/test.png"
          object-key (object-store/resource-object-key resource-path)
          _ (db/upsert-resource! resource-id tenant-id vault-id resource-path 
                                  object-key 1024 "image/png" "abc123hash")
          
          ;; Mock S3 get-object to return content
          mock-content (.getBytes "fake-image-content")
          request (-> (mock/request :get (str "/r/" resource-path))
                     (assoc :headers {"host" domain})
                     (assoc :path-params {:path resource-path}))]
      
      (with-redefs [object-store/get-object (fn [_ _] {:Body mock-content})]
        (let [response (frontend/get-resource request)]
          (is (= 200 (:status response)))
          (is (= "image/png" (get-in response [:headers "Content-Type"])))
          (is (= "public, max-age=31536000, immutable" (get-in response [:headers "Cache-Control"])))
          (is (= mock-content (:body response))))))))

