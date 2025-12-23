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
   {:port (or (some-> (System/getenv "PORT") Integer/parseInt) 8080)
    :host (or (System/getenv "HOST") "0.0.0.0")}

   :database
   {:dbtype "sqlite"
    :dbname (or (System/getenv "DB_PATH") "markdownbrain.db")}

   :session
   {:secret (string->16-bytes (or (System/getenv "SESSION_SECRET") "change-me-in-production-very-secret"))
    :cookie-name "markdownbrain-session"
    :max-age (* 60 60 24 7)} ; 7 days

   :server-ip
   (or (System/getenv "SERVER_IP") "123.45.67.89")})

(defn get-config [& path]
  (get-in config path))
