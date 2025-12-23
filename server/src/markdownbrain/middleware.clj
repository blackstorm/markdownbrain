(ns markdownbrain.middleware
  (:require [ring.middleware.session :as session]
            [ring.middleware.session.cookie :as cookie]
            [ring.middleware.params :as params]
            [ring.middleware.json :as json]
            [ring.middleware.keyword-params :as keyword-params]
            [ring.util.response :as response]
            [markdownbrain.config :as config]
            [markdownbrain.db :as db]
            [clojure.string :as str]))

;; Session 配置
(defn wrap-session-middleware [handler]
  (session/wrap-session
    handler
    {:store (cookie/cookie-store {:key (config/get-config :session :secret)})
     :cookie-name (config/get-config :session :cookie-name)
     :cookie-attrs {:max-age (config/get-config :session :max-age)
                    :http-only true
                    :same-site :lax}}))

;; 认证中间件（检查管理员登录）
(defn wrap-auth [handler]
  (fn [request]
    (if (get-in request [:session :user-id])
      (handler request)
      {:status 401
       :headers {"Content-Type" "application/json"}
       :body {:error "Unauthorized" :message "请先登录"}})))

;; CORS 中间件
(defn wrap-cors [handler]
  (fn [request]
    (let [response (handler request)]
      (-> response
          (assoc-in [:headers "Access-Control-Allow-Origin"] "*")
          (assoc-in [:headers "Access-Control-Allow-Methods"] "GET, POST, PUT, DELETE, OPTIONS")
          (assoc-in [:headers "Access-Control-Allow-Headers"] "Content-Type, Authorization")))))

;; 初始化检查中间件（检查是否有用户，没有则跳转到初始化页面）
(defn wrap-init-check [handler]
  (fn [request]
    (let [uri (:uri request)
          method (:request-method request)
          has-user (db/has-any-user?)]
      (cond
        ;; 跳过 API 路由、静态资源和 favicon
        (or (str/starts-with? uri "/api/")
            (str/starts-with? uri "/static/")
            (str/starts-with? uri "/js/")
            (str/starts-with? uri "/css/")
            (= uri "/favicon.ico"))
        (handler request)

        ;; 如果已有用户，禁止访问初始化页面
        (and has-user (= uri "/admin/init"))
        (response/redirect "/admin/login")

        ;; 如果没有用户，只允许访问初始化页面
        (and (not has-user) (not= uri "/admin/init"))
        (response/redirect "/admin/init")

        ;; 其他情况正常处理
        :else
        (handler request)))))

;; 完整中间件栈
(defn wrap-middleware [handler]
  (-> handler
      wrap-init-check
      wrap-session-middleware
      keyword-params/wrap-keyword-params
      params/wrap-params
      ;; 移除 json/wrap-json-body 和 json/wrap-json-response
      ;; 因为 Reitit 的 muuntaja 中间件会处理 JSON
      wrap-cors))
