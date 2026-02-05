(ns mdbrain.config
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [cprop.source :refer [from-env from-env-file]])
  (:import
   [java.security MessageDigest SecureRandom]))

;; ============================================================
;; Configuration loading (System env + .env file)
;; ============================================================

(def ^:private env-config
  (delay
    (let [env-file (io/file ".env")
          sources (cond-> [(from-env)]
                    (.exists env-file)
                    (conj (from-env-file ".env")))]
      (apply merge sources))))

(defn- env-name->keyword
  "Convert environment variable name to cprop keyword format.
   e.g., S3_ENDPOINT -> :s3-endpoint"
  [name]
  (-> name
      str/lower-case
      (str/replace "_" "-")
      keyword))

(defn getenv
  "Get environment variable. Checks system env and .env file."
  [name]
  (get @env-config (env-name->keyword name)))

;; ============================================================
;; Secrets management
;; ============================================================

(defn string->16-bytes [s]
  (let [md (MessageDigest/getInstance "MD5")
        bytes (.digest md (.getBytes s "UTF-8"))]
    bytes))

(defn generate-random-hex [n]
  (let [bytes (byte-array n)
        _ (.nextBytes (SecureRandom.) bytes)]
    (apply str (map #(format "%02x" %) bytes))))

(defn- data-path []
  (or (getenv "DATA_PATH") "data"))

(defn- secrets-file []
  (io/file (data-path) ".secrets.edn"))

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
        env-session-secret (getenv "SESSION_SECRET")
        session-secret (or env-session-secret
                           (:session-secret existing)
                           (do (log/info "Generated new SESSION_SECRET (saved to .secrets.edn)")
                               (generate-random-hex 16)))
        secrets {:session-secret session-secret}]
    (when (not= secrets existing)
      (save-secrets secrets))
    secrets))

(def ^:private secrets (delay (get-or-generate-secrets)))

(defn- health-token-file []
  (io/file (data-path) ".health-token"))

(defn- load-health-token []
  (let [f (health-token-file)]
    (when (.exists f)
      (let [token (str/trim (slurp f))]
        (when-not (str/blank? token)
          token)))))

(defn- save-health-token [token]
  (let [f (health-token-file)]
    (io/make-parents f)
    (spit f token)))

(defn- get-or-generate-health-token []
  (let [existing (load-health-token)
        token (or existing
                  (do
                    (log/info "Generated new health token (saved to .health-token)")
                    (generate-random-hex 32)))]
    (when (not= token existing)
      (save-health-token token))
    token))

(def ^:private health-token-delay (delay (get-or-generate-health-token)))

(defn- ->int
  "Convert value to int. Handles String, Long, Integer, or nil."
  [v default]
  (cond
    (nil? v) default
    (integer? v) (int v)
    (string? v) (Integer/parseInt v)
    :else default))

(def config
  {:server
   {:app
    {:port (->int (getenv "APP_PORT") 8080)
     :host (or (getenv "HOST") "0.0.0.0")}
    :console
    {:port (->int (getenv "CONSOLE_PORT") 9090)
     :host (or (getenv "HOST") "0.0.0.0")}}

   :database
   {:jdbcUrl (str "jdbc:sqlite:" 
                  (.getPath (io/file (data-path) "mdbrain.db"))
                  "?journal_mode=WAL&foreign_keys=ON")}

   :s3
   {:endpoint (getenv "S3_ENDPOINT")
    :access-key (getenv "S3_ACCESS_KEY")
    :secret-key (getenv "S3_SECRET_KEY")
    :region (or (getenv "S3_REGION") "us-east-1")
    :bucket (or (getenv "S3_BUCKET") "mdbrain")
    :public-url (getenv "S3_PUBLIC_URL")}

   :storage
   {:type (keyword (or (getenv "STORAGE_TYPE") "local"))
    :local-path (or (getenv "LOCAL_STORAGE_PATH")
                    (.getPath (io/file (data-path) "storage")))}

   :environment
   (keyword (or (getenv "ENVIRONMENT") "development"))})

(defn get-config [& path]
  (get-in config path))

(defn session-secret []
  (string->16-bytes (:session-secret @secrets)))

(defn health-token []
  @health-token-delay)

(defn production? []
  (= :production (get-config :environment)))

(defn development? []
  (= :development (get-config :environment)))

(defn s3-config []
  (get-config :s3))

(defn s3-enabled? []
  (some? (:endpoint (s3-config))))

(defn storage-config []
  (get-config :storage))

(defn storage-type []
  (:type (storage-config)))

(defn storage-enabled?
  "Returns true if storage is configured and valid.
   For S3: requires endpoint to be set.
   For local: always enabled (uses default path if not set)."
  []
  (case (storage-type)
    :s3 (s3-enabled?)
    :local true
    false))

(defn on-demand-tls-enabled?
  "Returns true if on-demand TLS is enabled.
   When enabled, the /console/domain-check endpoint is registered for Caddy to verify domains."
  []
  (= "true" (getenv "CADDY_ON_DEMAND_TLS_ENABLED")))

(defn validate-required-config!
  "Validate required configuration. Returns nil if valid, throws exception if invalid."
  []
  (let [storage-type (storage-type)
        s3 (s3-config)
        errors (case storage-type
                 :s3 (cond-> []
                       (nil? (:endpoint s3))
                       (conj "Missing S3_ENDPOINT: required when STORAGE_TYPE=s3")
                       
                       (nil? (:access-key s3))
                       (conj "Missing S3_ACCESS_KEY: required when STORAGE_TYPE=s3")
                       
                       (nil? (:secret-key s3))
                       (conj "Missing S3_SECRET_KEY: required when STORAGE_TYPE=s3")

                       (str/blank? (:public-url s3))
                       (conj "Missing S3_PUBLIC_URL: required when STORAGE_TYPE=s3 (assets must be publicly reachable)"))
                 :local []
                 ;; Unknown storage type
                 [(str "Unknown STORAGE_TYPE: " storage-type ". Supported: s3, local")])]
    (when (seq errors)
      (throw (ex-info "Missing required configuration"
                      {:errors errors})))))
