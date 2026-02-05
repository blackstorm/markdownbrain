(ns mdbrain.object-store.local
  "Local filesystem object storage implementation."
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [mdbrain.config :as config]
            [mdbrain.object-store :as store])
  (:import [java.io File FileInputStream ByteArrayInputStream]
           [java.nio.file Files Paths]
           [java.nio.file.attribute FileTime]))

;; ============================================================
;; Local Storage Helpers
;; ============================================================

(defn- storage-path
  "Get the base storage path from config."
  []
  (:local-path (config/storage-config)))

(defn- full-path
  "Get full filesystem path for an object."
  [vault-id object-key]
  (let [vault-prefix (store/vault-prefix vault-id)
        relative-path (str vault-prefix object-key)]
    (io/file (storage-path) relative-path)))

(defn- ensure-parent-dirs!
  "Ensure parent directories exist for a file."
  [^File file]
  (let [parent (.getParentFile file)]
    (when (and parent (not (.exists parent)))
      (.mkdirs parent))))

(defn- file->bytes
  "Read file contents as byte array."
  [^File file]
  (Files/readAllBytes (.toPath file)))

(defn- guess-content-type
  "Guess content type from file extension."
  [^String path]
  (let [ext (when path
              (let [idx (.lastIndexOf path ".")]
                (when (pos? idx)
                  (.toLowerCase (.substring path (inc idx))))))]
    (case ext
      "png" "image/png"
      "jpg" "image/jpeg"
      "jpeg" "image/jpeg"
      "gif" "image/gif"
      "webp" "image/webp"
      "svg" "image/svg+xml"
      "bmp" "image/bmp"
      "ico" "image/x-icon"
      "pdf" "application/pdf"
      "md" "text/markdown"
      "txt" "text/plain"
      "json" "application/json"
      "html" "text/html"
      "css" "text/css"
      "js" "application/javascript"
      "ogg" "audio/ogg"
      "application/octet-stream")))

;; ============================================================
;; LocalObjectStore Implementation
;; ============================================================

(defrecord LocalObjectStore [base-path]
  store/ObjectStore
  
  (put-object!* [_ vault-id object-key content content-type]
    (let [file (full-path vault-id object-key)
          body (if (bytes? content) content (.getBytes content "UTF-8"))]
      (try
        (ensure-parent-dirs! file)
        (io/copy (ByteArrayInputStream. body) file)
        (log/debug "Put object:" (.getPath file))
        {:ETag (str (hash body))
         :ContentType content-type}
        (catch Exception e
          (log/error "Failed to put object:" (.getMessage e))
          nil))))
  
  (get-object* [_ vault-id object-key]
    (let [file (full-path vault-id object-key)]
      (if (.exists file)
        (try
          {:Body (FileInputStream. file)
           :ContentLength (.length file)
           :ContentType (guess-content-type object-key)
           :LastModified (FileTime/fromMillis (.lastModified file))}
          (catch Exception e
            (log/error "Failed to get object:" (.getMessage e))
            nil))
        (do
          (log/debug "Object not found:" (.getPath file))
          nil))))
  
  (delete-object!* [_ vault-id object-key]
    (let [file (full-path vault-id object-key)]
      (if (.exists file)
        (try
          (.delete file)
          (log/debug "Deleted object:" (.getPath file))
          {:DeleteMarker true}
          (catch Exception e
            (log/error "Failed to delete object:" (.getMessage e))
            nil))
        (do
          (log/debug "Object not found for delete:" (.getPath file))
          {:DeleteMarker false}))))
  
  (head-object* [_ vault-id object-key]
    (let [file (full-path vault-id object-key)]
      (if (.exists file)
        {:ContentLength (.length file)
         :ContentType (guess-content-type object-key)
         :LastModified (FileTime/fromMillis (.lastModified file))}
        nil)))
  
  (delete-vault-objects!* [_ vault-id]
    (let [vault-prefix (store/vault-prefix vault-id)
          vault-dir (io/file (storage-path) vault-prefix)]
      (when (.exists vault-dir)
        (letfn [(delete-recursively [^File f]
                  (when (.isDirectory f)
                    (doseq [child (.listFiles f)]
                      (delete-recursively child)))
                  (.delete f))]
          (delete-recursively vault-dir)
          (log/info "Deleted vault directory:" (.getPath vault-dir))))))
  
  (public-url* [_ vault-id object-key]
    ;; Local storage serves assets via the /storage/{object-key} route
    (str "/storage/" object-key)))

;; ============================================================
;; Factory Function
;; ============================================================

(defn create-local-store
  "Create and initialize a LocalObjectStore.
   Creates the storage directory if it doesn't exist."
  []
  (let [base-path (storage-path)
        dir (io/file base-path)]
    ;; Create directory if it doesn't exist
    (when-not (.exists dir)
      (log/info "Creating local storage backend at:" base-path)
      (if (.mkdirs dir)
        (log/info "Created storage directory:" base-path)
        (throw (ex-info "Failed to create storage directory"
                        {:path base-path
                         :type :storage-error}))))
    
    ;; Verify directory is writable
    (when-not (.canWrite dir)
      (throw (ex-info "Storage directory is not writable"
                      {:path base-path
                       :type :storage-error})))
    
    (log/info "Local storage ready at:" base-path)
    (->LocalObjectStore base-path)))
