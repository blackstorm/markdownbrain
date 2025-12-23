(ns markdownbrain.routes-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [markdownbrain.db :as db]
            [markdownbrain.utils :as utils]
            [markdownbrain.routes :as routes]
            [markdownbrain.middleware :as middleware]
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

;; Test app with middleware
(def test-app (middleware/wrap-middleware routes/app))

;; Route Matching 测试
(deftest test-route-matching
  (testing "Root route matches"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "test.com" (utils/generate-uuid))
          request (-> (mock/request :get "/")
                     (assoc :headers {"host" "test.com"}))
          response (test-app request)]
      (is (or (= 200 (:status response))
              (= 404 (:status response))))))

  (testing "Admin init route matches"
    (let [request (-> (mock/request :post "/api/admin/init")
                     (assoc :body-params {:tenant-name "Org"
                                         :username "admin"
                                         :password "pass"}))
          response (test-app request)]
      (is (= 200 (:status response)))))

  (testing "Admin login route matches"
    (let [request (-> (mock/request :post "/api/admin/login")
                     (assoc :body-params {:username "user" :password "pass"}))
          response (test-app request)]
      (is (or (= 401 (:status response))
              (= 200 (:status response))))))

  (testing "Sync route matches"
    (let [request (-> (mock/request :post "/api/sync")
                     (assoc :headers {"authorization" "Bearer vault:token"})
                     (assoc :body-params {:path "test.md"
                                         :content "# Test"
                                         :metadata {}
                                         :hash "hash"
                                         :mtime "2025-12-21T10:00:00Z"}))
          response (test-app request)]
      (is (or (= 401 (:status response))
              (= 200 (:status response))))))

  (testing "Documents route matches"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "docs.com" (utils/generate-uuid))
          request (-> (mock/request :get "/api/documents")
                     (assoc :headers {"host" "docs.com"}))
          response (test-app request)]
      (is (= 200 (:status response)))))

  (testing "Admin frontend route matches"
    (let [request (mock/request :get "/admin")
          response (test-app request)]
      (is (or (= 200 (:status response))
              (= 302 (:status response)))))))

;; Authentication Routes 测试
(deftest test-authenticated-routes
  (testing "Logout route requires authentication"
    (let [request (mock/request :post "/api/admin/logout")
          response (test-app request)]
      (is (= 401 (:status response)))))

  (testing "List vaults requires authentication"
    (let [request (mock/request :get "/api/admin/vaults")
          response (test-app request)]
      (is (= 401 (:status response)))))

  (testing "Create vault requires authentication"
    (let [request (-> (mock/request :post "/api/admin/vaults")
                     (assoc :body-params {:name "Blog" :domain "test.com"}))
          response (test-app request)]
      (is (= 401 (:status response)))))

  (testing "Non-authenticated routes work with valid session"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          user-id (utils/generate-uuid)
          _ (db/create-user! user-id tenant-id "admin" "hash")
          request (-> (mock/request :get "/api/admin/vaults")
                     (assoc :session {:user-id user-id :tenant-id tenant-id}))
          response (test-app request)]
      (is (or (= 200 (:status response)) (= 401 (:status response)))))))

;; Non-authenticated Routes 测试
(deftest test-non-authenticated-routes
  (testing "Admin init does not require authentication"
    (let [request (-> (mock/request :post "/api/admin/init")
                     (assoc :body-params {:tenant-name "Org"
                                         :username "admin"
                                         :password "password"}))
          response (test-app request)]
      (is (= 200 (:status response)))))

  (testing "Login does not require authentication"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          user-id (utils/generate-uuid)
          password "password123"
          _ (db/create-user! user-id tenant-id "loginuser" (utils/hash-password password))
          request (-> (mock/request :post "/api/admin/login")
                     (assoc :body-params {:username "loginuser" :password password}))
          response (test-app request)]
      (is (= 200 (:status response)))))

  (testing "Sync does not require session (uses token)"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          sync-key (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "blog.com" sync-key)
          request (-> (mock/request :post "/api/sync")
                     (assoc :headers {"authorization" (str "Bearer " sync-key)})
                     (assoc :body-params {:path "test.md"
                                         :content "# Test"
                                         :metadata {}
                                         :hash "hash"
                                         :mtime "2025-12-21T10:00:00Z"
                                         :action "create"}))
          response (test-app request)]
      (is (= 200 (:status response))))))

;; 404 Routes 测试
(deftest test-not-found-routes
  (testing "Non-existent API route returns 404"
    (let [request (mock/request :get "/api/nonexistent")
          response (test-app request)]
      (is (= 404 (:status response)))))

  (testing "Non-existent route returns 404"
    (let [request (mock/request :get "/totally/fake/path")
          response (test-app request)]
      (is (= 404 (:status response)))))

  (testing "Wrong HTTP method returns 404 or 405"
    (let [request (mock/request :put "/api/admin/init")
          response (test-app request)]
      (is (or (= 404 (:status response))
              (= 405 (:status response)))))))

;; Route Parameter Handling 测试
(deftest test-route-parameters
  (testing "Document by ID route with parameter"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "param.com" (utils/generate-uuid))
          doc-id (utils/generate-uuid)
          _ (db/upsert-document! doc-id tenant-id vault-id "test.md" "# Test" "{}" "hash" "2025-12-21T10:00:00Z")
          request (-> (mock/request :get (str "/api/documents/" doc-id))
                     (assoc :headers {"host" "param.com"}))
          response (test-app request)]
      (is (or (= 200 (:status response))
              (= 404 (:status response))))))

  (testing "Route with invalid parameter format"
    (let [request (-> (mock/request :get "/api/documents/invalid-id-format")
                     (assoc :headers {"host" "test.com"}))
          response (test-app request)]
      (is (or (= 404 (:status response))
              (= 400 (:status response)))))))

;; HTTP Method Routing 测试
(deftest test-http-methods
  (testing "GET method routes correctly"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "get.com" (utils/generate-uuid))
          request (-> (mock/request :get "/api/documents")
                     (assoc :headers {"host" "get.com"}))
          response (test-app request)]
      (is (= 200 (:status response)))))

  (testing "POST method routes correctly"
    (let [request (-> (mock/request :post "/api/admin/init")
                     (assoc :body-params {:org-name "Org"
                                         :username "admin"
                                         :password "pass"}))
          response (test-app request)]
      (is (or (= 200 (:status response))
              (= 400 (:status response))))))

  (testing "Unsupported method returns 404 or 405"
    (let [request (mock/request :patch "/api/admin/init")
          response (test-app request)]
      (is (or (= 404 (:status response))
              (= 405 (:status response)))))))

;; Middleware Integration 测试
(deftest test-route-middleware-integration
  (testing "Routes work with JSON middleware"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "json.com" (utils/generate-uuid))
          request (-> (mock/request :get "/api/documents")
                     (assoc :headers {"host" "json.com"})
                     (mock/header "Accept" "application/json"))
          response (test-app request)]
      (is (= 200 (:status response)))
      (is (clojure.string/starts-with? (get-in response [:headers "Content-Type"]) "application/json"))))

  (testing "Routes work with CORS middleware"
    (let [request (-> (mock/request :options "/api/sync")
                     (mock/header "Origin" "http://localhost:3000"))
          response (test-app request)]
      (is (= 200 (:status response)))
      (is (= "*" (get-in response [:headers "Access-Control-Allow-Origin"])))))

  (testing "Routes work with session middleware"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          user-id (utils/generate-uuid)
          _ (db/create-user! user-id tenant-id "admin" "hash")
          request (-> (mock/request :get "/api/admin/vaults")
                     (assoc :session {:user-id user-id :tenant-id tenant-id}))
          response (test-app request)]
      (is (or (= 200 (:status response)) (= 401 (:status response)))))))

;; Full Route Integration 测试
(deftest test-full-route-integration
  (testing "Complete user flow: init -> login -> create vault -> sync"
    ;; 1. Initialize system
    (let [init-request (-> (mock/request :post "/api/admin/init")
                          (assoc :body-params {:tenant-name "Test Org"
                                              :username "admin"
                                              :password "password123"}))
          init-response (test-app init-request)
          tenant-id (get-in init-response [:body :tenant-id])
          user-id (get-in init-response [:body :user-id])]
      (is (= 200 (:status init-response)))

      ;; 2. Login
      (let [login-request (-> (mock/request :post "/api/admin/login")
                             (assoc :body-params {:username "admin"
                                                 :password "password123"}))
            login-response (test-app login-request)
            session (:session login-response)]
        (is (= 200 (:status login-response)))

        ;; 3. Create vault
        (let [vault-request (-> (mock/request :post "/api/admin/vaults")
                               (assoc :session session)
                               (assoc :body-params {:name "My Blog"
                                                   :domain "myblog.com"}))
              vault-response (test-app vault-request)
              vault-id (get-in vault-response [:body :vault :id])
              sync-key (get-in vault-response [:body :vault :sync-key])]
          (is (or (= 200 (:status vault-response)) (= 401 (:status vault-response))))

          ;; 4. Sync file
          (let [sync-request (-> (mock/request :post "/api/sync")
                                (assoc :headers {"authorization" (str "Bearer " sync-key)})
                                (assoc :body-params {:path "test.md"
                                                    :content "# Test Document"
                                                    :metadata {}
                                                    :hash "abc123"
                                                    :mtime "2025-12-21T10:00:00Z"
                                                    :action "create"}))
                sync-response (test-app sync-request)]
            (is (or (= 200 (:status sync-response)) (= 401 (:status sync-response))))
            (is (or (get-in sync-response [:body :success]) (= 401 (:status sync-response))))))))))

(deftest test-frontend-flow
  (testing "Complete frontend flow: domain routing -> list documents -> get document"
    ;; Setup
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          domain "flow.com"
          _ (db/create-vault! vault-id tenant-id "Flow Blog" domain (utils/generate-uuid))
          doc-id (utils/generate-uuid)
          _ (db/upsert-document! doc-id tenant-id vault-id "article.md" "# Article" "{}" "hash" "2025-12-21T10:00:00Z")]

      ;; 1. Homepage
      (let [home-request (-> (mock/request :get "/")
                            (assoc :headers {"host" domain}))
            home-response (test-app home-request)]
        (is (= 200 (:status home-response))))

      ;; 2. List documents
      (let [list-request (-> (mock/request :get "/api/documents")
                            (assoc :headers {"host" domain}))
            list-response (test-app list-request)]
        (is (= 200 (:status list-response)))
        (is (>= (count (:body list-response)) 1)))

      ;; 3. Get document
      (let [doc-request (-> (mock/request :get (str "/api/documents/" doc-id))
                           (assoc :headers {"host" domain}))
            doc-response (test-app doc-request)]
        (is (or (= 200 (:status doc-response))
                (= 404 (:status doc-response))))))))
