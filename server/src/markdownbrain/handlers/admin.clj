(ns markdownbrain.handlers.admin
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [markdownbrain.db :as db]
   [markdownbrain.object-store :as object-store]
   [markdownbrain.response :as resp]
   [markdownbrain.utils :as utils]
   [selmer.parser :as selmer]))

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
  #{"image/png" "image/jpeg" "image/gif" "image/webp" "image/svg+xml"})

(def ^:private logo-extension-map
  {"image/png" "png"
   "image/jpeg" "jpg"
   "image/gif" "gif"
   "image/webp" "webp"
   "image/svg+xml" "svg"})

(defn upload-vault-logo
  "Upload a logo image for a vault.
   Expects multipart form data with 'logo' file field.
   Validates: vault ownership, file type (png/jpg/gif/webp/svg), max size (2MB)."
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
              :error (str "Invalid file type. Allowed: PNG, JPEG, GIF, WebP, SVG. Got: " (:content-type file))}}

      (> (:size file) (* 2 1024 1024)) ; 2MB limit
      {:status 400
       :body {:success false :error "File too large. Maximum size is 2MB."}}

      :else
      (let [extension (get logo-extension-map (:content-type file) "png")
            content (with-open [in (io/input-stream (:tempfile file))]
                      (let [baos (java.io.ByteArrayOutputStream.)]
                        (io/copy in baos)
                        (.toByteArray baos)))
            content-hash (utils/sha256-hex content)
            object-key (object-store/logo-object-key content-hash extension)]
        ;; Delete old logo if exists (different key means different file)
        (when-let [old-key (:logo-object-key vault)]
          (when (not= old-key object-key)
            (object-store/delete-object! vault-id old-key)))
        ;; Upload to S3
        (object-store/put-object! vault-id object-key content (:content-type file))
        ;; Update DB
        (db/update-vault-logo! vault-id object-key)
        (resp/success {:message "Logo uploaded successfully"
                       :logo-url (admin-asset-url vault-id object-key)})))))

(defn delete-vault-logo
  "Remove the logo for a vault."
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
        ;; Delete from S3
        (object-store/delete-object! vault-id (:logo-object-key vault))
        ;; Clear DB reference
        (db/update-vault-logo! vault-id nil)
        (resp/success {:message "Logo deleted successfully"})))))

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
      {:status 404
       :headers {"Content-Type" "text/plain"}
       :body "Vault not found"}
      
      (not= (:tenant-id vault) tenant-id)
      {:status 403
       :headers {"Content-Type" "text/plain"}
       :body "Permission denied"}
      
      (or (nil? path) (str/blank? path))
      {:status 400
       :headers {"Content-Type" "text/plain"}
       :body "Missing path"}
      
      :else
      (let [result (object-store/get-object vault-id path)]
        (if result
          (let [body (input-stream->bytes (:Body result))
                content-type (or (:ContentType result) "application/octet-stream")]
            {:status 200
             :headers {"Content-Type" content-type
                       "Cache-Control" "public, max-age=31536000, immutable"}
             :body body})
          {:status 404
           :headers {"Content-Type" "text/plain"}
           :body "Not found"})))))
