(ns markdownbrain.middleware
  (:require
   [clojure.string :as str]
   [markdownbrain.config :as config]
   [markdownbrain.db :as db]
   [ring.middleware.keyword-params :as keyword-params]
   [ring.middleware.params :as params]
   [ring.middleware.session :as session]
   [ring.middleware.session.cookie :as cookie]
   [ring.util.response :as response]))

(defn wrap-session-middleware [handler]
  (session/wrap-session
    handler
    {:store (cookie/cookie-store {:key (config/session-secret)})
     :cookie-name "mdbrain-session"
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
           :headers {"Location" "/admin/login"}})))))

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
        ;; 跳过 Obsidian 同步接口、API 路由、静态资源和 favicon
        (or (str/starts-with? uri "/obsidian/")
            (str/starts-with? uri "/api/")
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
