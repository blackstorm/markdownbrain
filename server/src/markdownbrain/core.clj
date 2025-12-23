(ns markdownbrain.core
  (:require [ring.adapter.jetty :as jetty]
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
    (jetty/run-jetty
      (-> routes/app
          (middleware/wrap-middleware)
          (resource/wrap-resource "public"))
      {:port port
       :host host
       :join? false})))

(defn -main [& args]
  (start-server))
