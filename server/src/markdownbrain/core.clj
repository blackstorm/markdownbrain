(ns markdownbrain.core
  (:require [ring.adapter.undertow :as undertow]
            [ring.middleware.resource :as resource]
            [markdownbrain.routes :as routes]
            [markdownbrain.middleware :as middleware]
            [markdownbrain.db :as db]
            [markdownbrain.config :as config])
  (:gen-class))

(defn start-server []
  (let [port (config/get-config :server :port)
        host (config/get-config :server :host)]
    (println "Initializing database...")
    (db/init-db!)
    (println "Starting server on" host ":" port)
    (let [server (undertow/run-undertow
                  (-> routes/app
                      (middleware/wrap-middleware)
                      (resource/wrap-resource "public"))
                  {:port port
                   :host host})]
      {:server server
       :stop #(.stop server)})))

(defn -main [& args]
  (start-server))
