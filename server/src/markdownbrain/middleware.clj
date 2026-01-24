(ns markdownbrain.middleware
  (:require
   [clojure.string :as str]
   [markdownbrain.config :as config]
   [markdownbrain.db :as db]
   [markdownbrain.response :as resp]
   [ring.middleware.keyword-params :as keyword-params]
   [ring.middleware.multipart-params :as multipart]
   [ring.middleware.params :as params]
   [ring.middleware.session :as session]
   [ring.middleware.session.cookie :as cookie]
   [ring.util.response :as response]))

(def ^:private session-cookie-name
  "markdownbrain-session")

(defn wrap-session-middleware [handler]
  (session/wrap-session
    handler
    {:store (cookie/cookie-store {:key (config/session-secret)})
     :cookie-name session-cookie-name
     :cookie-attrs {:max-age (* 60 60 24 7)
                    :http-only true
                    :same-site :lax
                    :secure (config/production?)}}))

;; 认证中间件（检查管理员登录）
(defn wrap-auth [handler]
  (fn [request]
    (let [uri (:uri request)
          session (:session request)
          user-id (get-in request [:session :user-id])]
      (if (and user-id (not (str/blank? user-id)))
        (do
          (handler request))
        ;; 未登录，重定向到登录页
        (do
          {:status 302
           :headers {"Location" "/console/login"}})))))

;; CORS 中间件
(defn wrap-cors [handler]
  (fn [request]
    (let [uri (:uri request)
          cors-enabled? (str/starts-with? uri "/obsidian/")]
      (if-not cors-enabled?
        (handler request)
        (let [cors-headers {"Access-Control-Allow-Origin" "*"
                            "Access-Control-Allow-Methods" "GET, POST, PUT, DELETE, OPTIONS"
                            "Access-Control-Allow-Headers" "Content-Type, Authorization"}
              response (if (= :options (:request-method request))
                         {:status 200 :body ""}
                         (handler request))]
          (update response :headers (fnil merge {}) cors-headers))))))

;; CSRF 中间件（仅保护 Console 路由）
(defn- get-csrf-token-from-request [request]
  (let [headers (:headers request)
        header-token (get headers "x-csrf-token")
        params (:params request)]
    (or header-token
        (get params "__anti-forgery-token")
        (get params :__anti-forgery-token))))

(defn wrap-console-csrf [handler]
  (fn [request]
    (if-not (str/starts-with? (:uri request) "/console")
      (handler request)
      (let [session (or (:session request) {})
            csrf-token (or (:csrf-token session) (config/generate-random-hex 32))
            request' (-> request
                         (assoc :anti-forgery-token csrf-token)
                         (assoc :session (assoc session :csrf-token csrf-token)))
            state-changing? (contains? #{:post :put :delete :patch} (:request-method request'))
            provided-token (when state-changing? (get-csrf-token-from-request request'))]
        (if (and state-changing? (not= provided-token csrf-token))
          (resp/json-error 403 "CSRF token missing or incorrect")
          (let [response (handler request')
                response-session (:session response)]
            (cond
              ;; Handler explicitly set :session (e.g. login/logout)
              (contains? response :session)
              (cond
                (nil? response-session) response
                (map? response-session) (assoc response :session (assoc response-session :csrf-token csrf-token))
                :else response)

              ;; Handler didn't set :session; persist CSRF token
              :else
              (assoc response :session (:session request')))))))))

;; 初始化检查中间件（检查是否有用户，没有则跳转到初始化页面）
(defn wrap-init-check [handler]
  (fn [request]
    (let [uri (:uri request)
          method (:request-method request)
          has-user (db/has-any-user?)]
      (cond
        ;; 跳过 Obsidian 同步接口、API 路由、静态资源和 favicon
        (or (str/starts-with? uri "/obsidian/")
            (str/starts-with? uri "/api/")
            (str/starts-with? uri "/static/")
            (str/starts-with? uri "/js/")
            (str/starts-with? uri "/css/")
            (= uri "/favicon.ico"))
        (handler request)

        ;; 如果已有用户，禁止访问初始化页面
        (and has-user (= uri "/console/init"))
        (response/redirect "/console/login")

        ;; 如果没有用户，只允许访问初始化页面
        (and (not has-user) (not= uri "/console/init"))
        (response/redirect "/console/init")

        ;; 其他情况正常处理
        :else
        (handler request)))))

;; 完整中间件栈
(defn wrap-middleware [handler]
  (-> handler
      wrap-console-csrf
      wrap-session-middleware
      keyword-params/wrap-keyword-params
      multipart/wrap-multipart-params
      params/wrap-params
      wrap-init-check
      ;; 移除 json/wrap-json-body 和 json/wrap-json-response
      ;; 因为 Reitit 的 muuntaja 中间件会处理 JSON
      wrap-cors))
