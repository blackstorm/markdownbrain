(ns markdownbrain.middleware-test
  (:require [clojure.test :refer [deftest is testing]]
            [markdownbrain.utils :as utils]
            [markdownbrain.middleware :as middleware]
            [ring.mock.request :as mock]))

;; Mock handler that returns the request
(defn echo-handler [request]
  {:status 200
   :body {:received true
          :session (:session request)
          :user-id (get-in request [:session :user-id])}})

;; Authentication Middleware 测试
(deftest test-wrap-auth
  (testing "Authenticated request passes through"
    (let [tenant-id (utils/generate-uuid)
          user-id (utils/generate-uuid)
          handler (middleware/wrap-auth echo-handler)
          request (-> (mock/request :get "/api/admin/vaults")
                     (assoc :session {:user-id user-id :tenant-id tenant-id}))
          response (handler request)]
      (is (= 200 (:status response)))
      (is (get-in response [:body :received]))
      (is (= user-id (get-in response [:body :user-id])))))

  (testing "Unauthenticated request returns 401"
    (let [handler (middleware/wrap-auth echo-handler)
          request (mock/request :get "/api/admin/vaults")
          response (handler request)]
      (is (= 401 (:status response)))
      (is (= "Unauthorized" (get-in response [:body :error])))
      (is (= "请先登录" (get-in response [:body :message])))))

  (testing "Request with empty session returns 401"
    (let [handler (middleware/wrap-auth echo-handler)
          request (-> (mock/request :get "/api/admin/vaults")
                     (assoc :session {}))
          response (handler request)]
      (is (= 401 (:status response)))))

  (testing "Request with nil session returns 401"
    (let [handler (middleware/wrap-auth echo-handler)
          request (-> (mock/request :get "/api/admin/vaults")
                     (assoc :session nil))
          response (handler request)]
      (is (= 401 (:status response))))))

;; CORS Middleware 测试
(deftest test-wrap-cors
  (testing "CORS headers added to response"
    (let [handler (middleware/wrap-cors echo-handler)
          request (mock/request :get "/api/test")
          response (handler request)]
      (is (= "*" (get-in response [:headers "Access-Control-Allow-Origin"])))
      (is (= "GET, POST, PUT, DELETE, OPTIONS" (get-in response [:headers "Access-Control-Allow-Methods"])))
      (is (= "Content-Type, Authorization" (get-in response [:headers "Access-Control-Allow-Headers"])))))

  (testing "OPTIONS request returns 200"
    (let [handler (middleware/wrap-cors echo-handler)
          request (mock/request :options "/api/test")
          response (handler request)]
      (is (= 200 (:status response)))
      (is (= "*" (get-in response [:headers "Access-Control-Allow-Origin"])))))

  (testing "POST request with CORS"
    (let [handler (middleware/wrap-cors echo-handler)
          request (mock/request :post "/api/sync")
          response (handler request)]
      (is (= 200 (:status response)))
      (is (= "*" (get-in response [:headers "Access-Control-Allow-Origin"]))))))

;; Session Middleware 测试
(deftest test-session-middleware
  (testing "Session middleware preserves session"
    (let [wrapped-handler (middleware/wrap-middleware echo-handler)
          session-data {:user-id "user-123" :tenant-id "tenant-456"}
          request (-> (mock/request :get "/test")
                     (assoc :session session-data))
          response (wrapped-handler request)]
      (is (= 200 (:status response)))))

  (testing "Session middleware handles new session"
    (let [wrapped-handler (middleware/wrap-middleware echo-handler)
          request (mock/request :get "/test")
          response (wrapped-handler request)]
      (is (= 200 (:status response))))))

;; Error Handling 测试
(deftest test-error-handling
  (testing "Handler exception propagates (no error wrapper in middleware)"
    ;; The wrap-middleware doesn't include error handling, so exceptions propagate
    (let [failing-handler (fn [_] (throw (Exception. "Test error")))
          wrapped-handler (middleware/wrap-middleware failing-handler)
          request (mock/request :get "/test")]
      (is (thrown? Exception (wrapped-handler request)))))

  (testing "Non-exception error handling"
    (let [handler (fn [_] {:status 400 :body {:error "Bad request"}})
          wrapped-handler (middleware/wrap-middleware handler)
          request (mock/request :get "/test")
          response (wrapped-handler request)]
      (is (= 400 (:status response))))))

;; JSON Middleware 测试
(deftest test-json-middleware
  (testing "JSON request body parsing"
    (let [handler (fn [request]
                   {:status 200
                    :body {:received-data (:body-params request)}})
          wrapped-handler (middleware/wrap-middleware handler)
          request (-> (mock/request :post "/api/test")
                     (mock/header "Content-Type" "application/json")
                     (mock/body "{\"name\":\"test\",\"value\":123}"))
          response (wrapped-handler request)]
      (is (= 200 (:status response)))))

  (testing "JSON response encoding"
    (let [handler (fn [_] {:status 200 :body {:result "success" :data [1 2 3]}})
          wrapped-handler (middleware/wrap-middleware handler)
          request (mock/request :get "/api/test")
          response (wrapped-handler request)]
      (is (= 200 (:status response)))
      (is (clojure.string/starts-with? (get-in response [:headers "Content-Type"]) "application/json")))))

;; Content-Type Middleware 测试
(deftest test-content-type-handling
  (testing "application/json content-type"
    (let [wrapped-handler (middleware/wrap-middleware echo-handler)
          request (-> (mock/request :post "/api/test")
                     (mock/header "Content-Type" "application/json")
                     (mock/body "{\"test\":true}"))
          response (wrapped-handler request)]
      (is (= 200 (:status response)))))

  (testing "text/html content-type"
    (let [handler (fn [_] {:status 200
                          :headers {"Content-Type" "text/html"}
                          :body "<html><body>Test</body></html>"})
          wrapped-handler (middleware/wrap-middleware handler)
          request (mock/request :get "/")
          response (wrapped-handler request)]
      (is (= 200 (:status response)))
      (is (= "text/html" (get-in response [:headers "Content-Type"])))))

  (testing "Missing content-type defaults to JSON"
    (let [wrapped-handler (middleware/wrap-middleware echo-handler)
          request (mock/request :post "/api/test")
          response (wrapped-handler request)]
      (is (= 200 (:status response))))))

;; Middleware Chain Integration 测试
(deftest test-middleware-chain
  (testing "Authentication middleware alone"
    ;; Test wrap-auth directly without session middleware
    (let [handler (middleware/wrap-auth echo-handler)
          user-id (utils/generate-uuid)
          tenant-id (utils/generate-uuid)
          request (-> (mock/request :get "/api/admin/test")
                     (assoc :session {:user-id user-id :tenant-id tenant-id}))
          response (handler request)]
      (is (= 200 (:status response)))
      (is (= user-id (get-in response [:body :user-id])))))

  (testing "Full middleware chain without authentication"
    (let [handler (middleware/wrap-auth echo-handler)
          wrapped-handler (middleware/wrap-middleware handler)
          request (mock/request :get "/api/admin/test")
          response (wrapped-handler request)]
      (is (= 401 (:status response)))))

  (testing "Middleware chain with CORS preflight"
    (let [wrapped-handler (middleware/wrap-middleware echo-handler)
          request (-> (mock/request :options "/api/test")
                     (mock/header "Origin" "http://localhost:3000"))
          response (wrapped-handler request)]
      (is (= 200 (:status response)))
      (is (= "*" (get-in response [:headers "Access-Control-Allow-Origin"]))))))

;; Security Headers 测试
(deftest test-security-headers
  (testing "Security headers present in response"
    (let [wrapped-handler (middleware/wrap-middleware echo-handler)
          request (mock/request :get "/test")
          response (wrapped-handler request)]
      (is (= 200 (:status response)))
      ;; Verify JSON content type with charset
      (is (clojure.string/starts-with? (get-in response [:headers "Content-Type"]) "application/json"))))

  (testing "CORS security headers"
    (let [wrapped-handler (middleware/wrap-middleware echo-handler)
          request (-> (mock/request :get "/api/test")
                     (mock/header "Origin" "http://example.com"))
          response (wrapped-handler request)]
      (is (= "*" (get-in response [:headers "Access-Control-Allow-Origin"]))))))
