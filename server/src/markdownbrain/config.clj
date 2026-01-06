(ns markdownbrain.config
  (:require [clojure.java.io :as io])
  (:import [java.security MessageDigest]))

(defn string->16-bytes
  "Convert a string to exactly 16 bytes for session cookie"
  [s]
  (let [md (MessageDigest/getInstance "MD5")
        bytes (.digest md (.getBytes s "UTF-8"))]
    bytes))

(def config
  {:server
   {:frontend
    {:port (or (some-> (System/getenv "FRONTEND_PORT") Integer/parseInt) 8080)
     :host (or (System/getenv "HOST") "0.0.0.0")}
    :admin
    {:port (or (some-> (System/getenv "ADMIN_PORT") Integer/parseInt) 9090)
     :host (or (System/getenv "HOST") "0.0.0.0")}}

   :database
   {:dbtype "sqlite"
    :dbname (or (System/getenv "DB_PATH") "markdownbrain.db")}

   :session
   {:secret (string->16-bytes (or (System/getenv "SESSION_SECRET") "change-me-in-production-very-secret"))
    :cookie-name "mdbrain-session"
    :max-age (* 60 60 24 7)}

   :internal-token
   (System/getenv "INTERNAL_TOKEN")

   :environment
   (keyword (or (System/getenv "ENVIRONMENT") "development"))

   :server-ip
   (or (System/getenv "SERVER_IP") "123.45.67.89")})

(defn get-config [& path]
  (get-in config path))

(defn production?
  "判断是否为生产环境"
  []
  (= :production (get-config :environment)))

(defn development?
  "判断是否为开发环境"
  []
  (= :development (get-config :environment)))
