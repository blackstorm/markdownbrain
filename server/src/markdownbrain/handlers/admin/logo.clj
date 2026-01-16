(ns markdownbrain.handlers.admin.logo
  "Vault logo upload and serving handlers."
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [markdownbrain.db :as db]
   [markdownbrain.handlers.admin.common :as common]
   [markdownbrain.image-processing :as image-processing]
   [markdownbrain.object-store :as object-store]
   [markdownbrain.response :as resp]
   [markdownbrain.utils :as utils]))

(def ^:private allowed-logo-types
  #{"image/png" "image/jpeg"})

(def ^:private logo-extension-map
  {"image/png" "png"
   "image/jpeg" "jpg"})

(defn upload-vault-logo
  "Upload a logo image for a vault.
   Expects multipart form data with 'logo' file field.
   Validates: vault ownership, file type (png/jpg only), max size (2MB), min dimensions (128x128).
   Stores original logo (for website) and generates 32x32 favicon."
  [request]
  (let [tenant-id (get-in request [:session :tenant-id])
        vault-id (get-in request [:path-params :id])
        vault (db/get-vault-by-id vault-id)
        file (or (get-in request [:multipart-params "logo"])
                 (get-in request [:params :logo]))]
    (cond
      (nil? vault)
      {:status 404
       :body {:success false :error "Vault not found"}}

      (not= (:tenant-id vault) tenant-id)
      {:status 403
       :body {:success false :error "Permission denied"}}

      (nil? file)
      {:status 400
       :body {:success false :error "No file uploaded"}}

      (not (contains? allowed-logo-types (:content-type file)))
      {:status 400
       :body {:success false
              :error (str "Invalid file type. Allowed: PNG, JPEG. Got: " (:content-type file))}}

      (or (zero? (:size file)) (> (:size file) (* 2 1024 1024)))
      {:status 400
       :body {:success false :error (if (zero? (:size file))
                                      "File is empty. Please upload a valid image file."
                                      "File too large. Maximum size is 2MB.")}}

      :else
      (let [extension (get logo-extension-map (:content-type file) "png")
            content (with-open [in (io/input-stream (:tempfile file))]
                      (let [baos (java.io.ByteArrayOutputStream.)]
                        (io/copy in baos)
                        (.toByteArray baos)))
            content-hash (utils/sha256-hex content)
            content-type (:content-type file)
            logo-object-key (object-store/logo-object-key content-hash extension)]

        ;; Delete old logo and favicon if exists
        (when-let [old-key (:logo-object-key vault)]
          (when (not= old-key logo-object-key)
            (let [old-extension (last (str/split old-key #"\."))]
              (try
                (object-store/delete-object! vault-id (str old-key "@favicon." old-extension))
                (catch Exception e
                  (log/debug "Failed to delete old favicon for vault" vault-id ":" (.getMessage e))))
              (object-store/delete-object! vault-id old-key))))

        ;; Upload original logo
        (object-store/put-object! vault-id logo-object-key content content-type)

        ;; Generate and upload favicon (optional, fails gracefully)
        (try
          (when-let [favicon (image-processing/generate-favicon
                               content content-type content-hash extension)]
            (object-store/put-object! vault-id (:object-key favicon)
                                      (:bytes favicon) content-type))
          (catch Exception e
            (log/warn "Favicon generation failed:" (.getMessage e))))

        ;; Update database with logo key
        (db/update-vault-logo! vault-id logo-object-key)

        (resp/success {:message "Logo uploaded successfully"
                       :logo-url (common/admin-asset-url vault-id logo-object-key)})))))

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
   Route: GET /admin/vaults/:id/logo
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
          (let [body (common/input-stream->bytes (:Body result))
                content-type (or (:ContentType result) "application/octet-stream")]
            {:status 200
             :headers {"Content-Type" content-type
                       "Content-Length" (count body)
                       "Cache-Control" "public, max-age=31536000, immutable"}
             :body body})
          (resp/json-error 404 "Logo not found"))))))

(defn serve-vault-favicon
  "Serve vault favicon (32x32).
   Route: GET /admin/vaults/:id/favicon
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
          (let [body (common/input-stream->bytes (:Body result))
                content-type (or (:ContentType result) "application/octet-stream")]
            {:status 200
             :headers {"Content-Type" content-type
                       "Content-Length" (count body)
                       "Cache-Control" "public, max-age=31536000, immutable"}
             :body body})
          (resp/json-error 404 "Logo not found"))))))
