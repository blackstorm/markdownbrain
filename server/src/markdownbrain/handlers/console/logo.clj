(ns markdownbrain.handlers.console.logo
  "Vault logo upload and serving handlers."
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [markdownbrain.db :as db]
   [markdownbrain.handlers.console.common :as common]
   [markdownbrain.image-processing :as image-processing]
   [markdownbrain.object-store :as object-store]
   [markdownbrain.response :as resp]
   [markdownbrain.utils :as utils]
   [markdownbrain.utils.stream :as utils.stream]))

(def ^:private allowed-logo-types
  #{"image/png" "image/jpeg"})

(def ^:private logo-extension-map
  {"image/png" "png"
   "image/jpeg" "jpg"})

(def ^:private min-logo-dimension 128)

(defn upload-vault-logo
  "Upload a logo image for a vault.
   Expects multipart form data with 'logo' file field.
   Validates: vault ownership, file type (png/jpg only), max size (2MB), min dimensions (128x128).
   Stores original logo (for website) and generates 32x32 favicon."
  [request]
  (letfn [(error [status message]
            {:status status
             :body {:success false
                    :error message}})
          (rollback! [vault-id object-keys]
            (doseq [object-key object-keys]
              (try
                (object-store/delete-object! vault-id object-key)
                (catch Exception e
                  (log/debug "Rollback delete failed:" (.getMessage e))))))]
    (let [tenant-id (get-in request [:session :tenant-id])
          vault-id (get-in request [:path-params :id])
          vault (db/get-vault-by-id vault-id)
          file (or (get-in request [:multipart-params "logo"])
                   (get-in request [:params :logo]))]
      (cond
        (nil? vault)
        (error 404 "Vault not found")

        (not= (:tenant-id vault) tenant-id)
        (error 403 "Permission denied")

        (nil? file)
        (error 400 "No file uploaded")

        (not (contains? allowed-logo-types (:content-type file)))
        (error 400 (str "Invalid file type. Allowed: PNG, JPEG. Got: " (:content-type file)))

        (or (zero? (:size file)) (> (:size file) (* 2 1024 1024)))
        (error 400 (if (zero? (:size file))
                     "File is empty. Please upload a valid image file."
                     "File too large. Maximum size is 2MB."))

        :else
        (let [extension (get logo-extension-map (:content-type file) "png")
              content-type (:content-type file)
              content (with-open [in (io/input-stream (:tempfile file))]
                        (let [baos (java.io.ByteArrayOutputStream.)]
                          (io/copy in baos)
                          (.toByteArray baos)))
              dims (image-processing/get-image-dimensions content)]
          (cond
            (nil? dims)
            (error 400 "Invalid image file")

            (< (min (first dims) (second dims)) min-logo-dimension)
            (error 400 (format "Image too small. Minimum size is %dx%d."
                               min-logo-dimension min-logo-dimension))

            :else
            (let [content-hash (utils/sha256-hex content 16)
                  logo-object-key (object-store/logo-object-key content-hash extension)
                  favicon (image-processing/generate-favicon content content-type content-hash extension)]
              (if-not favicon
                (error 500 "Failed to generate favicon")
                (let [favicon-object-key (:object-key favicon)
                      logo-put (object-store/put-object! vault-id logo-object-key content content-type)]
                  (if-not logo-put
                    (error 500 "Failed to store logo")
                    (let [favicon-put (object-store/put-object! vault-id favicon-object-key
                                                               (:bytes favicon) content-type)]
                      (if-not favicon-put
                        (do
                          (rollback! vault-id [logo-object-key])
                          (error 500 "Failed to store favicon"))
                        (try
                          (db/update-vault-logo! vault-id logo-object-key)

                          (when-let [old-key (:logo-object-key vault)]
                            (when (not= old-key logo-object-key)
                              (let [old-extension (last (str/split old-key #"\."))]
                                (try
                                  (object-store/delete-object! vault-id (str old-key "@favicon." old-extension))
                                  (catch Exception e
                                    (log/debug "Failed to delete old favicon for vault" vault-id ":" (.getMessage e))))
                                (try
                                  (object-store/delete-object! vault-id old-key)
                                  (catch Exception e
                                    (log/debug "Failed to delete old logo for vault" vault-id ":" (.getMessage e)))))))

                          (resp/success {:message "Logo uploaded successfully"
                                         :logo-url (common/console-asset-url vault-id logo-object-key)})
                          (catch Exception e
                            (rollback! vault-id [logo-object-key favicon-object-key])
                            (log/error "Failed to update vault logo:" (.getMessage e))
                            (error 500 "Failed to update vault logo")))))))))))))))

(defn delete-vault-logo
  "Remove the logo and favicon for a vault."
  [request]
  (let [tenant-id (get-in request [:session :tenant-id])
        vault-id (get-in request [:path-params :id])
        vault (db/get-vault-by-id vault-id)]
    (cond
      (nil? vault)
      {:status 404
       :body {:success false :error "Vault not found"}}

      (not= (:tenant-id vault) tenant-id)
      {:status 403
       :body {:success false :error "Permission denied"}}

      (str/blank? (:logo-object-key vault))
      {:status 200
       :body {:success true :message "No logo to delete"}}

      :else
      (let [logo-key (:logo-object-key vault)
            extension (last (str/split logo-key #"\."))]
        ;; Delete favicon from storage
        (try
          (object-store/delete-object! vault-id (str logo-key "@favicon." extension))
          (catch Exception e
            (log/debug "Failed to delete favicon for vault" vault-id ":" (.getMessage e))))
        ;; Delete original logo from storage
        (object-store/delete-object! vault-id logo-key)
        ;; Clear DB reference
        (db/update-vault-logo! vault-id nil)
        (resp/success {:message "Logo deleted successfully"})))))

(defn serve-vault-logo
  "Serve vault logo (original image for website display).
   Route: GET /console/vaults/:id/logo
   Returns 404 if no logo is set."
  [request]
  (let [tenant-id (get-in request [:session :tenant-id])
        vault-id (get-in request [:path-params :id])
        vault (db/get-vault-by-id vault-id)]
    (cond
      (nil? vault)
      (resp/json-error 404 "Vault not found")

      (not= (:tenant-id vault) tenant-id)
      (resp/json-error 403 "Permission denied")

      (str/blank? (:logo-object-key vault))
      (resp/json-error 404 "Logo not found")

      :else
      (let [result (object-store/get-object vault-id (:logo-object-key vault))]
        (if result
          (let [body (utils.stream/input-stream->bytes (:Body result))
                content-type (or (:ContentType result) "application/octet-stream")]
            {:status 200
             :headers {"Content-Type" content-type
                       "Content-Length" (count body)
                       "Cache-Control" "public, max-age=31536000, immutable"}
             :body body})
          (resp/json-error 404 "Logo not found"))))))

(defn serve-vault-favicon
  "Serve vault favicon (32x32).
   Route: GET /console/vaults/:id/favicon
   Falls back to original logo if favicon doesn't exist.
   Returns 404 if no logo is set."
  [request]
  (let [tenant-id (get-in request [:session :tenant-id])
        vault-id (get-in request [:path-params :id])
        vault (db/get-vault-by-id vault-id)]
    (cond
      (nil? vault)
      (resp/json-error 404 "Vault not found")

      (not= (:tenant-id vault) tenant-id)
      (resp/json-error 403 "Permission denied")

      (str/blank? (:logo-object-key vault))
      (resp/json-error 404 "Logo not found")

      :else
      (let [logo-key (:logo-object-key vault)
            extension (last (str/split logo-key #"\."))
            favicon-key (str logo-key "@favicon." extension)
            ;; Try favicon first, fallback to original logo
            result (or (object-store/get-object vault-id favicon-key)
                       (object-store/get-object vault-id logo-key))]
        (if result
          (let [body (utils.stream/input-stream->bytes (:Body result))
                content-type (or (:ContentType result) "application/octet-stream")]
            {:status 200
             :headers {"Content-Type" content-type
                       "Content-Length" (count body)
                       "Cache-Control" "public, max-age=31536000, immutable"}
             :body body})
          (resp/json-error 404 "Logo not found"))))))
