(ns markdownbrain.handlers.admin-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [markdownbrain.db :as db]
            [markdownbrain.utils :as utils]
            [markdownbrain.handlers.admin :as admin]
            [markdownbrain.middleware :as middleware]
            [next.jdbc :as jdbc]
            [ring.mock.request :as mock]))

;; 测试数据库（临时文件 SQLite）
(def test-db-file (atom nil))

(defn setup-test-db [f]
  (let [temp-file (java.io.File/createTempFile "test-db" ".db")
        _ (.deleteOnExit temp-file)
        test-ds (delay (jdbc/get-datasource {:dbtype "sqlite" :dbname (.getPath temp-file)}))]
    (reset! test-db-file temp-file)
    (with-redefs [db/datasource test-ds]
      (db/init-db!)
      (f)
      (.delete temp-file))))

(use-fixtures :each setup-test-db)

;; Helper function to create authenticated request
(defn authenticated-request
  [method uri tenant-id user-id & {:keys [body]}]
  (let [req (mock/request method uri)]
    (cond-> req
      true (assoc :session {:tenant-id tenant-id :user-id user-id})
      body (assoc :body-params body))))

;; Admin Init 测试
(deftest test-admin-init
  (testing "Initialize system with first admin user"
    (let [request (-> (mock/request :post "/api/admin/init")
                     (assoc :body-params {:tenant-name "Test Org"
                                         :username "admin"
                                         :password "password123"}))
          response (admin/init-admin request)]
      (is (= 200 (:status response)))
      (is (get-in response [:body :success]))
      (is (string? (get-in response [:body :tenant-id])))
      (is (string? (get-in response [:body :user-id])))))

  (testing "Cannot initialize with existing username"
    (let [_ (admin/init-admin (-> (mock/request :post "/api/admin/init")
                                   (assoc :body-params {:tenant-name "First Org"
                                                       :username "admin1"
                                                       :password "pass1"})))
          request (-> (mock/request :post "/api/admin/init")
                     (assoc :body-params {:tenant-name "Second Org"
                                         :username "admin1"
                                         :password "pass2"}))
          response (admin/init-admin request)]
      (is (= 400 (:status response)))
      (is (false? (get-in response [:body :success])))
      (is (= "用户名已存在" (get-in response [:body :error])))))

  (testing "Missing required fields"
    (let [request (-> (mock/request :post "/api/admin/init")
                     (assoc :body-params {:tenant-name "Test Org"}))
          response (admin/init-admin request)]
      (is (= 400 (:status response)))
      (is (false? (get-in response [:body :success]))))))

;; Admin Login 测试
(deftest test-admin-login
  (testing "Successful login"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          user-id (utils/generate-uuid)
          password "password123"
          password-hash (utils/hash-password password)
          _ (db/create-user! user-id tenant-id "admin" password-hash)
          request (-> (mock/request :post "/api/admin/login")
                     (assoc :body-params {:username "admin"
                                         :password password}))
          response (admin/login request)]
      (is (= 200 (:status response)))
      (is (get-in response [:body :success]))
      (is (= user-id (get-in response [:session :user-id])))
      (is (= tenant-id (get-in response [:session :tenant-id])))))

  (testing "Login with wrong password"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          user-id (utils/generate-uuid)
          password-hash (utils/hash-password "correct-password")
          _ (db/create-user! user-id tenant-id "user1" password-hash)
          request (-> (mock/request :post "/api/admin/login")
                     (assoc :body-params {:username "user1"
                                         :password "wrong-password"}))
          response (admin/login request)]
      (is (= 401 (:status response)))
      (is (false? (get-in response [:body :success])))
      (is (= "用户名或密码错误" (get-in response [:body :error])))))

  (testing "Login with non-existent user"
    (let [request (-> (mock/request :post "/api/admin/login")
                     (assoc :body-params {:username "nonexistent"
                                         :password "password"}))
          response (admin/login request)]
      (is (= 401 (:status response)))
      (is (false? (get-in response [:body :success]))))))

;; Admin Logout 测试
(deftest test-admin-logout
  (testing "Successful logout"
    (let [request (-> (mock/request :post "/api/admin/logout")
                     (assoc :session {:user-id "user-123" :tenant-id "tenant-123"}))
          response (admin/logout request)]
      (is (= 200 (:status response)))
      (is (get-in response [:body :success]))
      (is (nil? (get-in response [:session :user-id])))))

  (testing "Logout without session"
    (let [request (mock/request :post "/api/admin/logout")
          response (admin/logout request)]
      (is (= 200 (:status response)))
      (is (get-in response [:body :success])))))

;; List Vaults 测试
(deftest test-list-vaults
  (testing "List vaults for authenticated user"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          user-id (utils/generate-uuid)
          _ (db/create-user! user-id tenant-id "admin" "hash")
          vault-id-1 (utils/generate-uuid)
          vault-id-2 (utils/generate-uuid)
          _ (db/create-vault! vault-id-1 tenant-id "Blog 1" "blog1.com" (utils/generate-uuid))
          _ (db/create-vault! vault-id-2 tenant-id "Blog 2" "blog2.com" (utils/generate-uuid))
          request (authenticated-request :get "/api/admin/vaults" tenant-id user-id)
          response (admin/list-vaults request)]
      (is (= 200 (:status response)))
      (is (vector? (:body response)))
      (is (= 2 (count (:body response))))))

  (testing "Empty vault list"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          user-id (utils/generate-uuid)
          _ (db/create-user! user-id tenant-id "admin2" "hash")
          request (authenticated-request :get "/api/admin/vaults" tenant-id user-id)
          response (admin/list-vaults request)]
      (is (= 200 (:status response)))
      (is (vector? (:body response)))
      (is (= 0 (count (:body response))))))

;; Create Vault 测试
(deftest test-create-vault
  (testing "Create vault successfully"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          user-id (utils/generate-uuid)
          _ (db/create-user! user-id tenant-id "admin" "hash")
          request (authenticated-request :post "/api/admin/vaults"
                                        tenant-id user-id
                                        :body {:name "My Blog"
                                              :domain "myblog.com"})
          response (admin/create-vault request)]
      (is (= 200 (:status response)))
      (is (get-in response [:body :success]))
      (is (string? (get-in response [:body :vault :id])))
      (is (= "My Blog" (get-in response [:body :vault :name])))
      (is (= "myblog.com" (get-in response [:body :vault :domain])))
      (is (string? (get-in response [:body :vault :sync-key])))))

  (testing "Create vault with missing fields"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          user-id (utils/generate-uuid)
          _ (db/create-user! user-id tenant-id "admin" "hash")
          request (authenticated-request :post "/api/admin/vaults"
                                        tenant-id user-id
                                        :body {:name "Blog Only"})
          response (admin/create-vault request)]
      (is (= 400 (:status response)))
      (is (false? (get-in response [:body :success]))))))

)
