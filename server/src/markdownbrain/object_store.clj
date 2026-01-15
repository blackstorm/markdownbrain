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
            [markdownbrain.config :as config]
            [markdownbrain.image-processing :as image-processing]))

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
;; Logo Thumbnail Cache
;; ============================================================

(declare object-exists?)

(def ^:private max-cache-entries 1000)

(def ^:private logo-thumbnail-cache
  "Atom storing cache of which thumbnail sizes exist for each logo.
   Format: {logo-object-key #{512 256 128 ...}}"
  (atom {}))

(defn- prune-cache-if-needed
  "Remove oldest entries if cache exceeds max size.
   This prevents memory leaks from unlimited cache growth.

   NOTE: This uses simple truncation, not LRU eviction. In high-traffic
   scenarios with many logos, frequently-accessed entries may be evicted.
   Consider using clojure.core.cache/lru-cache if this becomes an issue."
  []
  (swap! logo-thumbnail-cache
    (fn [cache]
      (if (> (count cache) max-cache-entries)
        (let [entries (seq cache)]
          (into {} (take (quot max-cache-entries 2) entries)))
        cache))))

(defn clear-logo-cache!
  "Clear cache for a specific logo or all logos."
  ([]
   (reset! logo-thumbnail-cache {}))
  ([logo-object-key]
   (swap! logo-thumbnail-cache dissoc logo-object-key)))

(defn thumbnail-object-key-for-logo
  "Generate object key for a thumbnail based on the logo object key.
   Example: site/logo/abc.png -> site/logo/abc@256x256.png
   Handles filenames with multiple dots correctly."
  [logo-object-key size]
  (let [last-dot-index (.lastIndexOf logo-object-key ".")
        base (subs logo-object-key 0 last-dot-index)
        extension (subs logo-object-key (inc last-dot-index))
        size-str (str size "x" size)]
    (str base "@" size-str "." extension)))

(defn get-available-thumbnail-size
  "Get the largest available thumbnail size <= requested size.
   Checks cache first, then storage, updating cache as needed.
   Returns the size (integer) or nil if no thumbnail exists.
   Always returns at least the original logo key as fallback.

   Thread-safe: Uses atomic swap! to prevent race conditions when
   multiple requests check for the same thumbnail size simultaneously."
  [vault-id logo-object-key requested-size]
  (let [sizes image-processing/thumbnail-sizes
        ;; Check sizes in order (largest to smallest, <= requested)
        sizes-to-check (filter #(<= % requested-size) sizes)]
    (loop [sizes sizes-to-check]
      (when-let [s (first sizes)]
        (let [thumb-key (thumbnail-object-key-for-logo logo-object-key s)
              ;; Atomically get cached sizes for this logo
              cached (get @logo-thumbnail-cache logo-object-key)]
          (cond
            ;; Check cache first (thread-safe: cached is immutable snapshot)
            (and cached (contains? cached s))
            s

            ;; Check storage and atomically update cache
            ;; This prevents race conditions: only one thread wins the check-and-set
            (object-exists? vault-id thumb-key)
            (do
              ;; Atomic check-and-update: only add if not already present
              (swap! logo-thumbnail-cache
                     update logo-object-key
                     (fn [existing]
                       (let [sizes (or existing #{})]
                         (if (contains? sizes s)
                           sizes
                           (conj sizes s)))))
              ;; Prune cache if needed to prevent memory leaks
              (prune-cache-if-needed)
              s)

            ;; Try next smaller size
            :else
            (recur (rest sizes))))))))

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
