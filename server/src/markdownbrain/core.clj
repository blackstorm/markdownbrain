(ns markdownbrain.core
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [markdownbrain.config :as config]
   [markdownbrain.db :as db]
   [markdownbrain.lucide-icon :as lucide-icon]
   [markdownbrain.middleware :as middleware]
   [markdownbrain.object-store :as object-store]
   [markdownbrain.routes :as routes]
   [ring.adapter.undertow :as undertow]
   [ring.util.response :as response]
   [ring.util.mime-type :as mime])
  (:gen-class))

(defn wrap-resource-with-context
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
            (let [resp (response/resource-response resource-path {:root resource-root})
                  content-type (mime/ext-mime-type resource-path)]
              (if content-type
                (response/content-type resp content-type)
                resp))
            (handler request)))
        (handler request)))))

(defn wrap-frontend-reserved-paths
  "Frontend server (8080) must not serve Console (9090) pages or Publish API routes.
   Return 404 instead of letting the frontend router treat them as note paths."
  [handler]
  (fn [request]
    (let [uri (:uri request)]
      (if (or (str/starts-with? uri "/console")
              (str/starts-with? uri "/obsidian"))
        {:status 404 :body "Not found"}
        (handler request)))))

(defn start-frontend-server []
  (let [port (config/get-config :server :frontend :port)
        host (config/get-config :server :frontend :host)]
    (log/info "Starting Frontend server on" host ":" port)
    (let [server (undertow/run-undertow
                  (-> routes/frontend-app
                      (wrap-frontend-reserved-paths)
                      (middleware/wrap-frontend-host-binding)
                      (middleware/wrap-middleware)
                      (wrap-resource-with-context "/publics/frontend" "publics/frontend")
                      (wrap-resource-with-context "/publics/shared" "publics/shared"))
                  {:port port
                   :host host})]
      {:server server
       :port port
       :type :frontend
       :stop #(.stop server)})))

(defn start-console-server []
  (let [port (config/get-config :server :console :port)
        host (config/get-config :server :console :host)]
    (log/info "Starting Console server on" host ":" port)
    (let [server (undertow/run-undertow
                  (-> routes/console-app
                      (middleware/wrap-middleware)
                      (wrap-resource-with-context "/publics/console" "publics/console")
                      (wrap-resource-with-context "/publics/shared" "publics/shared"))
                  {:port port
                   :host host})]
      {:server server
       :port port
       :type :console
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

  (lucide-icon/init!)

  (log/info "Initializing database...")
  (db/init-db!)

  (log/info "Initializing storage...")
  (object-store/init-storage!)

  (log/info "Initializing health token...")
  (config/health-token)

  (let [frontend (start-frontend-server)
        console (start-console-server)]
    (log/info "=== MarkdownBrain Servers Started ===")
    (log/info (str "Frontend: http://localhost:" (:port frontend)))
    (log/info (str "Console:    http://localhost:" (:port console)))
    (log/info "=====================================")
    {:frontend frontend
     :console console
     :stop (fn []
             (log/info "Stopping servers...")
             ((:stop frontend))
             ((:stop console))
             (log/info "All servers stopped"))}))

(defn -main [& args]
  (let [servers (start-servers)]
    (.addShutdownHook
     (Runtime/getRuntime)
     (Thread. ^Runnable (fn []
                          (log/info "Shutdown signal received, stopping servers...")
                          ((:stop servers)))))
    @(promise)))
