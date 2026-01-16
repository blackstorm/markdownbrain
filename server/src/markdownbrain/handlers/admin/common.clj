(ns markdownbrain.handlers.admin.common
  "Shared utilities for admin handlers."
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [markdownbrain.db :as db]
   [markdownbrain.object-store :as object-store]
   [markdownbrain.response :as resp]))

(defn format-storage-size
  "Format bytes into human-readable size string."
  [bytes]
  (cond
    (nil? bytes) "0 B"
    (< bytes 1024) (str bytes " B")
    (< bytes (* 1024 1024)) (format "%.1f KB" (/ bytes 1024.0))
    (< bytes (* 1024 1024 1024)) (format "%.1f MB" (/ bytes (* 1024.0 1024)))
    :else (format "%.2f GB" (/ bytes (* 1024.0 1024 1024)))))

(defn admin-asset-url
  "Generate admin storage URL for an asset.
   Returns URL like /admin/storage/{vault-id}/{object-key}"
  [vault-id object-key]
  (str "/admin/storage/" vault-id "/" object-key))

(defn input-stream->bytes
  "Convert InputStream to byte array."
  [is]
  (let [baos (java.io.ByteArrayOutputStream.)]
    (io/copy is baos)
    (.toByteArray baos)))

(defn serve-admin-asset
  "Serve assets from storage for admin panel.
   Route: GET /admin/storage/:id/*path
   
   Unlike frontend's /storage/* which uses Host header for vault resolution,
   this route takes vault-id explicitly and verifies tenant isolation via session."
  [request]
  (let [tenant-id (get-in request [:session :tenant-id])
        vault-id (get-in request [:path-params :id])
        path (get-in request [:path-params :path])
        vault (db/get-vault-by-id vault-id)]
    (cond
      (nil? vault)
      (resp/json-error 404 "Vault not found")

      (not= (:tenant-id vault) tenant-id)
      (resp/json-error 403 "Permission denied")

      (or (nil? path) (str/blank? path))
      (resp/json-error 400 "Missing path")

      :else
      (let [result (object-store/get-object vault-id path)]
        (if result
          (let [body (input-stream->bytes (:Body result))
                content-type (or (:ContentType result) "application/octet-stream")]
            {:status 200
             :headers {"Content-Type" content-type
                       "Content-Length" (count body)
                       "Cache-Control" "public, max-age=31536000, immutable"}
             :body body})
          (resp/json-error 404 "Not found"))))))
