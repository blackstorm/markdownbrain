(ns markdownbrain.object-store
  "Object store abstraction layer supporting multiple backends (S3, local filesystem).

   Configuration:
   - STORAGE_TYPE: 's3' or 'local' (default: 'local')
   - LOCAL_STORAGE_PATH: path for local storage (default: './data/storage')
   - S3_* env vars for S3 backend

   Usage:
   1. Call (init-storage!) at application startup
   2. Use put-object!, get-object, delete-object!, etc. for operations"
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [markdownbrain.config :as config]))

;; ============================================================
;; Protocol Definition
;; ============================================================

(defprotocol ObjectStore
  "Protocol for object storage operations."
  (put-object!* [this vault-id object-key content content-type]
    "Store an object. Content can be bytes or string.")
  (get-object* [this vault-id object-key]
    "Retrieve an object. Returns map with :Body (InputStream) and metadata, or nil.")
  (delete-object!* [this vault-id object-key]
    "Delete an object.")
  (head-object* [this vault-id object-key]
    "Get object metadata without body. Returns map or nil.")
  (delete-vault-objects!* [this vault-id]
    "Delete all objects for a vault.")
  (public-url* [this vault-id object-key]
    "Get public URL for an object, or nil if not supported."))

;; ============================================================
;; Singleton Store Instance
;; ============================================================

(defonce ^:private store* (atom nil))

(defn get-store
  "Get the current storage instance. Throws if not initialized."
  []
  (or @store*
      (throw (ex-info "Storage not initialized. Call init-storage! first."
                      {:type :storage-not-initialized}))))

(defn set-store!
  "Set the storage instance. Called by init-storage!."
  [store]
  (reset! store* store))

;; ============================================================
;; Helper Functions (shared across implementations)
;; ============================================================

(defn vault-prefix
  "Returns the object key prefix for a vault: {vault-id}/"
  [vault-id]
  (str (str/replace vault-id "-" "") "/"))

(defn normalize-path
  "Normalize a file path, resolving . and .. components."
  [path]
  (when path
    (let [segments (-> path
                       (str/replace #"\\" "/")  ; Windows backslashes to forward
                       (str/replace #"^/" "")   ; Remove leading slash
                       (str/split #"/"))]
      (->> segments
           (reduce (fn [acc seg]
                     (cond
                       (= seg "..") (if (seq acc) (pop acc) acc)  ; Go up, but not above root
                       (or (= seg ".") (str/blank? seg)) acc      ; Skip . and empty
                       :else (conj acc seg)))                     ; Add normal segment
                   [])
           (str/join "/")))))

(def ^:private content-type-extension-map
  "Map from content-type to file extension."
  {"image/png" "png"
   "image/jpeg" "jpg"
   "image/gif" "gif"
   "image/webp" "webp"
   "image/svg+xml" "svg"
   "application/pdf" "pdf"
   "application/json" "json"
   "text/plain" "txt"
   "text/markdown" "md"
   "text/html" "html"
   "text/css" "css"
   "application/javascript" "js"
   "video/mp4" "mp4"
   "video/webm" "webm"
   "audio/mpeg" "mp3"
   "audio/wav" "wav"})

(defn content-type->extension
  "Convert content-type to file extension. Returns 'bin' for unknown types."
  [content-type]
  (get content-type-extension-map content-type "bin"))

(defn asset-object-key
  "Generate object key for an asset based on client_id and extension."
  [client-id extension]
  (str "assets/" client-id "." extension))

(defn logo-object-key
  "Generate object key for a site logo using content hash and extension."
  [content-hash extension]
  (str "site/logo/" content-hash "." extension))

;; ============================================================
;; Public API (backward compatible, delegates to store instance)
;; ============================================================

(defn put-object!
  "Store an object. Content can be bytes or string."
  [vault-id object-key content content-type]
  (put-object!* (get-store) vault-id object-key content content-type))

(defn get-object
  "Retrieve an object. Returns map with :Body and metadata, or nil."
  [vault-id object-key]
  (get-object* (get-store) vault-id object-key))

(defn delete-object!
  "Delete an object."
  [vault-id object-key]
  (delete-object!* (get-store) vault-id object-key))

(defn head-object
  "Get object metadata without body. Returns map or nil."
  [vault-id object-key]
  (head-object* (get-store) vault-id object-key))

(defn object-exists?
  "Check if an object exists."
  [vault-id object-key]
  (some? (head-object vault-id object-key)))

(defn delete-vault-objects!
  "Delete all objects for a vault."
  [vault-id]
  (delete-vault-objects!* (get-store) vault-id))

(defn public-asset-url
  "Get public URL for an asset, or nil if not supported by the storage backend."
  [vault-id object-key]
  (public-url* (get-store) vault-id object-key))

;; ============================================================
;; Initialization
;; ============================================================

(defn init-storage!
  "Initialize storage backend based on configuration.
   Must be called at application startup."
  []
  (let [storage-type (config/storage-type)]
    (log/info "Initializing storage backend:" (name storage-type))
    (case storage-type
      :s3 (do
            (require 'markdownbrain.object-store.s3)
            (let [create-fn (resolve 'markdownbrain.object-store.s3/create-s3-store)]
              (set-store! (create-fn))
              (log/info "S3 storage initialized successfully")))
      :local (do
               (require 'markdownbrain.object-store.local)
               (let [create-fn (resolve 'markdownbrain.object-store.local/create-local-store)]
                 (set-store! (create-fn))
                 (log/info "Local storage initialized successfully")))
      (throw (ex-info (str "Unknown storage type: " storage-type)
                      {:storage-type storage-type})))))

;; ============================================================
;; Legacy compatibility - ensure-bucket! (now no-op for local)
;; ============================================================

(defn ensure-bucket!
  "Ensure storage is ready. For S3, creates bucket if needed.
   For local, creates directory if needed.
   DEPRECATED: Use init-storage! instead."
  []
  (log/warn "ensure-bucket! is deprecated. Use init-storage! instead.")
  (when-not @store*
    (init-storage!)))
