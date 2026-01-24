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

(defn- sanitize-extension
  "Keep extension URL/key safe (letters, digits, '-', '_', '+')."
  [extension]
  (when extension
    (let [s (-> (str extension)
                (str/trim)
                (str/lower-case))
          s (if (str/starts-with? s ".") (subs s 1) s)
          filtered (->> s
                        (filter (fn [ch]
                                  (or (Character/isLetterOrDigit ^char ch)
                                      (= ch \-)
                                      (= ch \_)
                                      (= ch \+))))
                        (apply str))
          trimmed (when (seq filtered)
                    (subs filtered 0 (min 32 (count filtered))))]
      (when (seq trimmed) trimmed))))

(defn extension-from-path
  "Extract a safe extension from a path (no dot, lowercase) or nil.
   Example: \"assets/photo.PNG\" -> \"png\"."
  [path]
  (when path
    (let [p (str path)
          slash-idx (.lastIndexOf p "/")
          filename (if (neg? slash-idx) p (subs p (inc slash-idx)))
          dot-idx (.lastIndexOf filename ".")]
      (when (and (pos? dot-idx) (< (inc dot-idx) (count filename)))
        (sanitize-extension (subs filename (inc dot-idx)))))))

(defn asset-object-key
  "Generate object key for an asset based on client_id and optional extension."
  ([client-id]
   (str "assets/" client-id))
  ([client-id extension]
   (if-let [ext (sanitize-extension extension)]
     (str "assets/" client-id "." ext)
     (asset-object-key client-id))))

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
