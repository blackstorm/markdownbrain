(ns markdownbrain.core
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [markdownbrain.config :as config]
   [markdownbrain.db :as db]
   [markdownbrain.middleware :as middleware]
   [markdownbrain.object-store :as object-store]
   [markdownbrain.routes :as routes]
   [ring.adapter.undertow :as undertow]
   [ring.util.response :as response])
  (:gen-class))

(defn wrap-resource-with-context
  "Serve static resources from classpath at a specific URL context path.
   Example: (wrap-resource-with-context handler \"/publics/admin\" \"publics/admin\")
   This will serve files from classpath:publics/admin/* at URL /publics/admin/*"
  [handler context-path resource-root]
  (fn [request]
    (let [uri (:uri request)]
      (if (str/starts-with? uri context-path)
        (let [resource-path (subs uri (count context-path))
              resource-path (if (str/starts-with? resource-path "/")
                              (subs resource-path 1)
                              resource-path)
              full-path (str resource-root "/" resource-path)
              resource (io/resource full-path)]
          (if resource
            (response/resource-response resource-path {:root resource-root})
            (handler request)))
        (handler request)))))

(defn start-frontend-server []
  (let [port (config/get-config :server :frontend :port)
        host (config/get-config :server :frontend :host)]
    (log/info "Starting Frontend server on" host ":" port)
    (let [server (undertow/run-undertow
                  (-> routes/frontend-app
                      (middleware/wrap-middleware)
                      (wrap-resource-with-context "/publics/frontend" "publics/frontend")
                      (wrap-resource-with-context "/publics/shared" "publics/shared"))
                  {:port port
                   :host host})]
      {:server server
       :port port
       :type :frontend
       :stop #(.stop server)})))

(defn start-admin-server []
  (let [port (config/get-config :server :admin :port)
        host (config/get-config :server :admin :host)]
    (log/info "Starting Admin server on" host ":" port)
    (let [server (undertow/run-undertow
                  (-> routes/admin-app
                      (middleware/wrap-middleware)
                      (wrap-resource-with-context "/publics/admin" "publics/admin")
                      (wrap-resource-with-context "/publics/shared" "publics/shared"))
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

  (log/info "Initializing storage...")
  (object-store/init-storage!)

  (let [frontend (start-frontend-server)
        admin (start-admin-server)]
    (log/info "=== MarkdownBrain Servers Started ===")
    (log/info (str "Frontend: http://localhost:" (:port frontend)))
    (log/info (str "Admin:    http://localhost:" (:port admin)))
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
