(ns markdownbrain.config
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn])
  (:import [java.security MessageDigest SecureRandom]))

(defn string->16-bytes [s]
  (let [md (MessageDigest/getInstance "MD5")
        bytes (.digest md (.getBytes s "UTF-8"))]
    bytes))

(defn generate-random-hex [n]
  (let [bytes (byte-array n)
        _ (.nextBytes (SecureRandom.) bytes)]
    (apply str (map #(format "%02x" %) bytes))))

(defn- secrets-file []
  (let [db-path (or (System/getenv "DB_PATH") "markdownbrain.db")
        data-dir (.getParent (io/file db-path))]
    (if data-dir
      (io/file data-dir ".secrets.edn")
      (io/file ".secrets.edn"))))

(defn- load-secrets []
  (let [f (secrets-file)]
    (when (.exists f)
      (edn/read-string (slurp f)))))

(defn- save-secrets [secrets]
  (let [f (secrets-file)]
    (io/make-parents f)
    (spit f (pr-str secrets))))

(defn- get-or-generate-secrets []
  (let [existing (load-secrets)
        session-secret (or (System/getenv "SESSION_SECRET")
                           (:session-secret existing)
                           (generate-random-hex 16))
        internal-token (or (System/getenv "INTERNAL_TOKEN")
                           (:internal-token existing)
                           (generate-random-hex 32))
        secrets {:session-secret session-secret
                 :internal-token internal-token}]
    (when (not= secrets existing)
      (save-secrets secrets))
    secrets))

(def ^:private secrets (delay (get-or-generate-secrets)))

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

   :environment
   (keyword (or (System/getenv "ENVIRONMENT") "development"))})

(defn get-config [& path]
  (get-in config path))

(defn session-secret []
  (string->16-bytes (:session-secret @secrets)))

(defn internal-token []
  (:internal-token @secrets))

(defn production? []
  (= :production (get-config :environment)))

(defn development? []
  (= :development (get-config :environment)))
