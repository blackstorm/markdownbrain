(ns markdownbrain.middleware
  (:require [ring.middleware.session :as session]
            [ring.middleware.session.cookie :as cookie]
            [ring.middleware.params :as params]
            [ring.middleware.json :as json]
            [ring.middleware.keyword-params :as keyword-params]
            [ring.util.response :as response]
            [markdownbrain.config :as config]))

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

;; 完整中间件栈
(defn wrap-middleware [handler]
  (-> handler
      wrap-session-middleware
      keyword-params/wrap-keyword-params
      params/wrap-params
      json/wrap-json-response
      (json/wrap-json-body {:keywords? true})
      wrap-cors))
