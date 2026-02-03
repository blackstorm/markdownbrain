(ns markdownbrain.handlers.console.common
  "Shared utilities for console handlers."
  (:require
   [clojure.string :as str]
   [markdownbrain.db :as db]
   [markdownbrain.object-store :as object-store]
   [markdownbrain.response :as resp]
   [markdownbrain.utils.stream :as utils.stream]))

(defn console-asset-url
  "Generate console storage URL for an asset.
   Returns URL like /console/storage/{vault-id}/{object-key}"
  [vault-id object-key]
  (str "/console/storage/" vault-id "/" object-key))

(defn serve-console-asset
  "Serve assets from storage for console panel.
   Route: GET /console/storage/:id/*path
   
   Unlike app's /storage/* which uses Host header for vault resolution,
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
          (let [body (utils.stream/input-stream->bytes (:Body result))
                content-type (or (:ContentType result) "application/octet-stream")]
            {:status 200
             :headers {"Content-Type" content-type
                       "Content-Length" (count body)
                       "Cache-Control" "public, max-age=31536000, immutable"}
             :body body})
          (resp/json-error 404 "Not found"))))))
