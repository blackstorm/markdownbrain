(ns markdownbrain.handlers.admin.auth-test
  "Tests for admin authentication handlers."
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [markdownbrain.db :as db]
   [markdownbrain.handlers.admin.auth :as auth]
   [markdownbrain.handlers.admin.test-utils :as test-utils]
   [markdownbrain.utils :as utils]
   [ring.mock.request :as mock]))

(use-fixtures :each test-utils/setup-test-db)

(deftest test-admin-init-success
  (testing "Initialize system with first admin user"
    (let [request (-> (mock/request :post "/api/console/init")
                      (assoc :body-params {:tenant-name "Test Org"
                                           :username "admin"
                                           :password "password123"}))
          response (auth/init-admin request)]
      (is (= 200 (:status response)))
      (is (get-in response [:body :success]))
      (is (string? (get-in response [:body :tenant-id])))
      (is (string? (get-in response [:body :user-id]))))))

(deftest test-admin-init-already-initialized
  (testing "Cannot initialize when system already initialized"
    (let [_ (auth/init-admin (-> (mock/request :post "/api/console/init")
                                 (assoc :body-params {:tenant-name "First Org"
                                                      :username "admin1"
                                                      :password "pass1"})))
          request (-> (mock/request :post "/api/console/init")
                      (assoc :body-params {:tenant-name "Second Org"
                                           :username "admin2"
                                           :password "pass2"}))
          response (auth/init-admin request)]
      (is (= 403 (:status response)))
      (is (false? (get-in response [:body :success])))
      (is (= "System already initialized" (get-in response [:body :error]))))))

(deftest test-admin-init-missing-fields
  (testing "Missing required fields returns 200 with error"
    (let [request (-> (mock/request :post "/api/console/init")
                      (assoc :body-params {:tenant-name "Test Org"}))
          response (auth/init-admin request)]
      (is (= 200 (:status response)))
      (is (false? (get-in response [:body :success]))))))

(deftest test-admin-login
  (testing "Successful login"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          user-id (utils/generate-uuid)
          password "password123"
          password-hash (utils/hash-password password)
          _ (db/create-user! user-id tenant-id "login-test-admin" password-hash)
          request (-> (mock/request :post "/api/console/login")
                      (assoc :body-params {:username "login-test-admin"
                                           :password password}))
          response (auth/login request)]
      (is (= 200 (:status response)))
      (is (get-in response [:body :success]))
      (is (= user-id (get-in response [:session :user-id])))
      (is (= tenant-id (get-in response [:session :tenant-id])))))

  (testing "Login with wrong password returns 200 with error"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org 2")
          user-id (utils/generate-uuid)
          password-hash (utils/hash-password "correct-password")
          _ (db/create-user! user-id tenant-id "wrong-pass-user" password-hash)
          request (-> (mock/request :post "/api/console/login")
                      (assoc :body-params {:username "wrong-pass-user"
                                           :password "wrong-password"}))
          response (auth/login request)]
      (is (= 200 (:status response)))
      (is (false? (get-in response [:body :success])))
      (is (= "Invalid username or password" (get-in response [:body :error])))))

  (testing "Login with non-existent user returns 200 with error"
    (let [request (-> (mock/request :post "/api/console/login")
                      (assoc :body-params {:username "nonexistent"
                                           :password "password"}))
          response (auth/login request)]
      (is (= 200 (:status response)))
      (is (false? (get-in response [:body :success]))))))

(deftest test-admin-logout
  (testing "Successful logout redirects to login page"
    (let [request (-> (mock/request :post "/api/console/logout")
                      (assoc :session {:user-id "user-123" :tenant-id "tenant-123"}))
          response (auth/logout request)]
      (is (= 302 (:status response)))
      (is (nil? (:session response)))
      (is (= "/console/login" (get-in response [:headers "Location"])))))

  (testing "Logout without session also redirects"
    (let [request (mock/request :post "/api/console/logout")
          response (auth/logout request)]
      (is (= 302 (:status response)))
      (is (nil? (:session response))))))
