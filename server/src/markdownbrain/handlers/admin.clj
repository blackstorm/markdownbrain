(ns markdownbrain.handlers.admin
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [markdownbrain.db :as db]
   [markdownbrain.image-processing :as image-processing]
   [markdownbrain.object-store :as object-store]
   [markdownbrain.response :as resp]
   [markdownbrain.utils :as utils]
   [selmer.parser :as selmer]))

(declare input-stream->bytes)

(def ^:private default-logo-size 512)

(defn init-admin [request]
  (if (db/has-any-user?)
    {:status 403
     :body {:success false
            :error "System already initialized"}}
    (let [params (or (:body-params request) (:params request))
          {:keys [username password tenant-name]} params]
      (cond
        (or (nil? username) (nil? password) (nil? tenant-name))
        {:status 200
         :body {:success false
                :error "Missing required fields"}}

        (db/get-user-by-username username)
        {:status 200
         :body {:success false
                :error "Username already exists"}}

        :else
        (let [tenant-id (utils/generate-uuid)
              user-id (utils/generate-uuid)
              password-hash (utils/hash-password password)]
          (db/create-tenant! tenant-id tenant-name)
          (db/create-user! user-id tenant-id username password-hash)
          (resp/success {:tenant-id tenant-id
                         :user-id user-id}))))))

(defn login [request]
  (let [params (or (:body-params request) (:params request))
        {:keys [username password]} params
        user (db/get-user-by-username username)]
    (if (and user (utils/verify-password password (:password-hash user)))
      (resp/success {:user {:id (:id user)
                            :username (:username user)
                            :tenant-id (:tenant-id user)}}
                    {:user-id (:id user)
                     :tenant-id (:tenant-id user)})
      {:status 200
       :body {:success false
              :error "Invalid username or password"}})))

(defn logout [request]
  {:status 302
   :session nil
   :headers {"Location" "/admin/login"}})

(defn format-storage-size [bytes]
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

(defn admin-home [request]
  (let [tenant-id (get-in request [:session :tenant-id])
        tenant (db/get-tenant tenant-id)
        vaults (db/list-vaults-by-tenant tenant-id)
        vaults-with-data (mapv (fn [vault]
                                 (let [sync-key (:sync-key vault)
                                       masked (str (subs sync-key 0 8) "******" (subs sync-key (- (count sync-key) 8)))
                                       notes (db/search-notes-by-vault (:id vault) "")
                                       storage-bytes (db/get-vault-storage-size (:id vault))
                                       logo-url (when-let [key (:logo-object-key vault)]
                                                  (admin-asset-url (:id vault) key))]
                                   (assoc vault
                                          :masked-key masked
                                          :notes notes
                                          :storage-size (format-storage-size storage-bytes)
                                          :logo-url logo-url)))
                               vaults)]
    (resp/html (selmer/render-file "templates/admin/vaults.html"
                                    {:tenant tenant
                                     :vaults vaults-with-data}))))

(defn login-page [request]
  (resp/html (selmer/render-file "templates/admin/login.html" {})))

(defn init-page [request]
  (resp/html (selmer/render-file "templates/admin/init.html" {})))

(defn list-vaults [request]
  (let [tenant-id (get-in request [:session :tenant-id])
        vaults (db/list-vaults-by-tenant tenant-id)
        vaults-with-data (mapv (fn [vault]
                                 (let [sync-key (:sync-key vault)
                                       masked (str (subs sync-key 0 8) "******" (subs sync-key (- (count sync-key) 8)))
                                       notes (db/search-notes-by-vault (:id vault) "")
                                       storage-bytes (db/get-vault-storage-size (:id vault))
                                       logo-url (when-let [key (:logo-object-key vault)]
                                                  (admin-asset-url (:id vault) key))]
                                   (assoc vault
                                          :masked-key masked
                                          :notes notes
                                          :storage-size (format-storage-size storage-bytes)
                                          :logo-url logo-url)))
                               vaults)]
    (resp/html (selmer/render-file "templates/admin/vault-list.html"
                                   {:vaults vaults-with-data}))))

(defn create-vault [request]
  (let [tenant-id (get-in request [:session :tenant-id])
        params (or (:body-params request) (:params request))
        {:keys [name domain]} params]
    (cond
      (or (nil? name) (nil? domain))
      {:status 200
       :body {:success false
              :error "Missing required fields"}}

      (db/get-vault-by-domain domain)
      {:status 200
       :body {:success false
              :error "Domain already in use"}}

      :else
      (let [vault-id (utils/generate-uuid)
            sync-key (utils/generate-uuid)]
        (db/create-vault! vault-id tenant-id name domain sync-key)
        (resp/success {:vault {:id vault-id
                               :name name
                               :domain domain
                               :sync-key sync-key}})))))

(defn delete-vault [request]
  (let [tenant-id (get-in request [:session :tenant-id])
        vault-id (get-in request [:path-params :id])
        vault (db/get-vault-by-id vault-id)]
    (cond
      (nil? vault)
      {:status 200
       :body {:success false
              :error "Site not found"}}

      (not= (:tenant-id vault) tenant-id)
      {:status 200
       :body {:success false
              :error "Permission denied"}}

      :else
      (do
        (object-store/delete-vault-objects! vault-id)
        (db/delete-vault! vault-id)
        (resp/success {:message "Site deleted"})))))

(defn update-vault [request]
  (let [tenant-id (get-in request [:session :tenant-id])
        vault-id (get-in request [:path-params :id])
        params (or (:body-params request) (:params request))
        name (:name params)
        domain (:domain params)
        vault (db/get-vault-by-id vault-id)]
    (cond
      (nil? vault)
      {:status 200
       :body {:success false
              :error "Site not found"}}

      (not= (:tenant-id vault) tenant-id)
      {:status 200
       :body {:success false
              :error "Permission denied"}}

      (or (nil? name) (clojure.string/blank? name))
      {:status 200
       :body {:success false
              :error "Site name is required"}}

      (or (nil? domain) (clojure.string/blank? domain))
      {:status 200
       :body {:success false
              :error "Domain is required"}}

      :else
      (do
        (db/update-vault! vault-id name domain)
        {:status 200
         :body {:success true
                :vault {:id vault-id
                        :name name
                        :domain domain}}}))))

(defn search-vault-notes [request]
  (let [tenant-id (get-in request [:session :tenant-id])
        vault-id (get-in request [:path-params :id])
        query (get-in request [:params :q])
        vault (db/get-vault-by-id vault-id)]
    (cond
      (nil? vault)
      {:status 200
       :body {:success false
              :error "Vault not found"}}

      (not= (:tenant-id vault) tenant-id)
      {:status 200
       :body {:success false
              :error "Permission denied"}}

      (nil? query)
      {:status 200
       :body {:success false
              :error "Missing search query"}}

      :else
      (let [notes (db/search-notes-by-vault vault-id query)]
        (resp/success {:notes notes})))))

(defn update-vault-root-note [request]
  (let [tenant-id (get-in request [:session :tenant-id])
        vault-id (get-in request [:path-params :id])
        params (or (:body-params request) (:params request))
        root-note-id (:rootNoteId params)
        vault (db/get-vault-by-id vault-id)]
    (cond
      (nil? vault)
      {:status 200
       :body {:success false
              :error "Vault not found"}}

      (not= (:tenant-id vault) tenant-id)
      {:status 200
       :body {:success false
              :error "Permission denied"}}

      (nil? root-note-id)
      {:status 200
       :body {:success false
              :error "Missing rootNoteId"}}

      :else
      (do
        (db/update-vault-root-note! vault-id root-note-id)
        (resp/success {:message "Root note updated"
                       :rootNoteId root-note-id})))))

(defn get-root-note-selector [request]
  (let [tenant-id (get-in request [:session :tenant-id])
        vault-id (get-in request [:path-params :id])
        vault (db/get-vault-by-id vault-id)]
    (cond
      (nil? vault)
      {:status 404
       :headers {"Content-Type" "text/html"}
       :body "<div class=\"alert alert-error\"><span>Vault not found</span></div>"}

      (not= (:tenant-id vault) tenant-id)
      {:status 403
       :headers {"Content-Type" "text/html"}
       :body "<div class=\"alert alert-error\"><span>Permission denied</span></div>"}

      :else
      (let [notes (db/search-notes-by-vault vault-id "")
            root-note-id (:root-note-id vault)]
        {:status 200
         :headers {"Content-Type" "text/html"}
         :body (selmer/render-file "templates/admin/root-note-selector.html"
                                    {:notes notes
                                     :vault-id vault-id
                                     :root-note-id root-note-id})}))))

(def ^:private allowed-logo-types
  #{"image/png" "image/jpeg" "image/webp"})

(def ^:private logo-extension-map
  {"image/png" "png"
   "image/jpeg" "jpg"
   "image/webp" "webp"})

(defn upload-vault-logo
  "Upload a logo image for a vault.
   Expects multipart form data with 'logo' file field.
   Validates: vault ownership, file type (png/jpg/webp only), max size (2MB).
   Generates thumbnails: 512x512, 256x256, 128x128, 64x64, 32x32.

   NOTE: WebP support depends on ImageIO plugins. Standard Java ImageIO doesn't
   include WebP support by default. If WebP files fail to process, consider using
   PNG or JPEG format instead, or add a WebP ImageIO plugin to your classpath."
  [request]
  (let [tenant-id (get-in request [:session :tenant-id])
        vault-id (get-in request [:path-params :id])
        vault (db/get-vault-by-id vault-id)
        ;; Multipart file is in :multipart-params or :params
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
              :error (str "Invalid file type. Allowed: PNG, JPEG, WebP. Got: " (:content-type file))}}

      (or (zero? (:size file)) (> (:size file) (* 2 1024 1024))) ; 2MB limit, min 1 byte
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
            object-key (object-store/logo-object-key content-hash extension)]

        ;; Delete old logo and all possible thumbnails if exists (different key means different file)
        (when-let [old-key (:logo-object-key vault)]
          (when (not= old-key object-key)
            ;; Clear cache for old logo
            (object-store/clear-logo-cache! old-key)
            ;; Delete old thumbnails by trying each size
            (doseq [size image-processing/thumbnail-sizes]
              (let [thumb-key (object-store/thumbnail-object-key-for-logo old-key size)]
                (try
                  (object-store/delete-object! vault-id thumb-key)
                  (catch Exception e
                    (log/debug "Failed to delete old thumbnail" thumb-key "for vault" vault-id ":" (.getMessage e))))))
            ;; Delete old logo
            (object-store/delete-object! vault-id old-key)))

        ;; Upload original
        (object-store/put-object! vault-id object-key content content-type)

        ;; Generate and upload thumbnails with transaction safety (fixes problem 4)
        (try
          (let [thumbnails (image-processing/generate-thumbnails
                           content content-type content-hash extension)
                ;; Track uploaded thumbnail keys for rollback
                uploaded-thumbs (atom [])]

            ;; Upload all thumbnails, tracking for rollback
            (try
              (doseq [{:keys [object-key thumb-bytes]} (vals thumbnails)]
                (object-store/put-object! vault-id object-key thumb-bytes content-type)
                (swap! uploaded-thumbs conj object-key))

              ;; If all thumbnails uploaded successfully, update DB
              (db/update-vault-logo! vault-id object-key)

              ;; Clear cache for new logo so it will be lazily loaded on first request
              ;; This removes the need for synchronous pre-warming (fixes problem 6)
              (object-store/clear-logo-cache! object-key)

              (resp/success {:message "Logo uploaded successfully"
                             :logo-url (admin-asset-url vault-id object-key)})

              (catch Exception e
                ;; Rollback: delete any thumbnails that were uploaded
                (doseq [thumb-key @uploaded-thumbs]
                  (try
                    (object-store/delete-object! vault-id thumb-key)
                    (catch Exception _)))
                ;; Also delete the original logo
                (try
                  (object-store/delete-object! vault-id object-key)
                  (catch Exception _))
                ;; Re-throw the exception
                (throw e))))

          (catch Exception e
            {:status 500
             :body {:success false
                    :error (str "Failed to upload logo: " (.getMessage e))}}))))))

(defn delete-vault-logo
  "Remove the logo and all thumbnails for a vault."
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
      (do
        ;; Clear cache for this logo
        (object-store/clear-logo-cache! (:logo-object-key vault))
        ;; Delete all thumbnails from storage (try each size)
        (doseq [size image-processing/thumbnail-sizes]
          (let [thumb-key (object-store/thumbnail-object-key-for-logo (:logo-object-key vault) size)]
            (try
              (object-store/delete-object! vault-id thumb-key)
              (catch Exception e
                (log/debug "Failed to delete thumbnail" thumb-key "for vault" vault-id ":" (.getMessage e))))))
        ;; Delete original logo from storage
        (object-store/delete-object! vault-id (:logo-object-key vault))
        ;; Clear DB reference
        (db/update-vault-logo! vault-id nil)
        (resp/success {:message "Logo deleted successfully"})))))

;; ============================================================
;; Vault Logo Serving with Smart Thumbnail Fallback
;; ============================================================

(defn serve-vault-logo
  "Serve vault logo with smart thumbnail fallback.
   Route: GET /admin/vaults/:id/logo?size=N

   Query param ?size=N (default: 512)
   If requested size doesn't exist, falls back to next smaller size.
   Returns 404 if no logo is set."
  [request]
  (let [tenant-id (get-in request [:session :tenant-id])
        vault-id (get-in request [:path-params :id])
        vault (db/get-vault-by-id vault-id)
        size-param (or (get-in request [:params "size"]) (str default-logo-size))
        requested-size (try (Integer/parseInt size-param)
                           (catch Exception _ default-logo-size))]
    (cond
      (nil? vault)
      (resp/json-error 404 "Vault not found")

      (not= (:tenant-id vault) tenant-id)
      (resp/json-error 403 "Permission denied")

      (str/blank? (:logo-object-key vault))
      (resp/json-error 404 "Logo not found")

      :else
      (let [logo-key (:logo-object-key vault)
            available-size (object-store/get-available-thumbnail-size
                            vault-id logo-key requested-size)]
        (if (and available-size (<= available-size requested-size))
          (let [object-key (if (= available-size requested-size)
                            logo-key
                            (object-store/thumbnail-object-key-for-logo logo-key available-size))
                result (object-store/get-object vault-id object-key)]
            (if result
              (let [body (input-stream->bytes (:Body result))
                    content-type (or (:ContentType result) "application/octet-stream")]
                {:status 200
                 :headers {"Content-Type" content-type
                           "Content-Length" (count body)
                           "Cache-Control" "public, max-age=31536000, immutable"}
                 :body body})
              (resp/json-error 404 "Logo not found")))
          ;; No thumbnail available, serve original
          (let [result (object-store/get-object vault-id logo-key)]
            (if result
              (let [body (input-stream->bytes (:Body result))
                    content-type (or (:ContentType result) "application/octet-stream")]
                {:status 200
                 :headers {"Content-Type" content-type
                           "Content-Length" (count body)
                           "Cache-Control" "public, max-age=31536000, immutable"}
                 :body body})
              (resp/json-error 404 "Logo not found"))))))))

;; ============================================================
;; Admin Asset Serving
;; ============================================================

(defn- input-stream->bytes
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
