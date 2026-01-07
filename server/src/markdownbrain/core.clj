(ns markdownbrain.core
  (:require
   [markdownbrain.config :as config]
   [markdownbrain.db :as db]
   [markdownbrain.middleware :as middleware]
   [markdownbrain.object-store :as object-store]
   [markdownbrain.routes :as routes]
   [ring.adapter.undertow :as undertow]
   [ring.middleware.resource :as resource]
   [clojure.tools.logging :as log])
  (:gen-class))

(defn start-frontend-server []
  "启动 Frontend 服务器 (端口 8080) - 公开文档展示"
  (let [port (config/get-config :server :frontend :port)
        host (config/get-config :server :frontend :host)]
    (log/info "Starting Frontend server on" host ":" port)
    (let [server (undertow/run-undertow
                  (-> routes/frontend-app
                      (middleware/wrap-middleware)
                      (resource/wrap-resource "public"))
                  {:port port
                   :host host})]
      {:server server
       :port port
       :type :frontend
       :stop #(.stop server)})))

(defn start-admin-server []
  "启动 Admin 服务器 (端口 9090) - 管理后台 + Obsidian 同步"
  (let [port (config/get-config :server :admin :port)
        host (config/get-config :server :admin :host)]
    (log/info "Starting Admin server on" host ":" port)
    (let [server (undertow/run-undertow
                  (-> routes/admin-app
                      (middleware/wrap-middleware)
                      (resource/wrap-resource "public"))
                  {:port port
                   :host host})]
      {:server server
       :port port
       :type :admin
       :stop #(.stop server)})))

(defn start-servers []
  "启动所有服务器"
  ;; 验证必填配置
  (try
    (config/validate-required-config!)
    (catch clojure.lang.ExceptionInfo e
      (log/error "Configuration error:")
      (doseq [err (:errors (ex-data e))]
        (log/error "  -" err))
      (System/exit 1)))
  
  (log/info "Initializing database...")
  (db/init-db!)
  
  (log/info "Initializing S3 storage...")
  (object-store/ensure-bucket!)

  (let [frontend (start-frontend-server)
        admin (start-admin-server)]
    (log/info "=== MarkdownBrain Servers Started ===")
    (log/info "Frontend: http://localhost:" (:port frontend))
    (log/info "Admin:    http://localhost:" (:port admin))
    (log/info "=====================================")
    {:frontend frontend
     :admin admin
     :stop (fn []
             (log/info "Stopping servers...")
             ((:stop frontend))
             ((:stop admin))
             (log/info "All servers stopped"))}))

(defn -main [& args]
  (start-servers)
  ;; 保持主线程运行
  @(promise))
