(ns markdownbrain.handlers.admin.vaults
  "Vault CRUD and notes management handlers."
  (:require
   [clojure.string :as str]
   [markdownbrain.db :as db]
   [markdownbrain.handlers.admin.common :as common]
   [markdownbrain.object-store :as object-store]
   [markdownbrain.response :as resp]
   [markdownbrain.utils :as utils]
   [selmer.parser :as selmer]))

(defn- enrich-vault-data
  "Add computed fields to vault for display."
  [vault]
  (let [sync-key (:sync-key vault)
        masked (str (subs sync-key 0 8) "******" (subs sync-key (- (count sync-key) 8)))
        notes (db/search-notes-by-vault (:id vault) "")
        storage-bytes (db/get-vault-storage-size (:id vault))
        logo-url (when-let [key (:logo-object-key vault)]
                   (common/admin-asset-url (:id vault) key))]
    (assoc vault
           :masked-key masked
           :notes notes
           :storage-size (common/format-storage-size storage-bytes)
           :logo-url logo-url)))

(defn admin-home
  "Admin home page showing all vaults."
  [request]
  (let [tenant-id (get-in request [:session :tenant-id])
        tenant (db/get-tenant tenant-id)
        vaults (db/list-vaults-by-tenant tenant-id)
        vaults-with-data (mapv enrich-vault-data vaults)]
    (resp/html (selmer/render-file "templates/admin/vaults.html"
                                   {:tenant tenant
                                    :vaults vaults-with-data}))))

(defn list-vaults
  "List all vaults for current tenant."
  [request]
  (let [tenant-id (get-in request [:session :tenant-id])
        vaults (db/list-vaults-by-tenant tenant-id)
        vaults-with-data (mapv enrich-vault-data vaults)]
    (resp/html (selmer/render-file "templates/admin/vault-list.html"
                                   {:vaults vaults-with-data}))))

(defn create-vault
  "Create a new vault."
  [request]
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

(defn delete-vault
  "Delete a vault and all its data."
  [request]
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

(defn update-vault
  "Update vault name and domain."
  [request]
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

      (or (nil? name) (str/blank? name))
      {:status 200
       :body {:success false
              :error "Site name is required"}}

      (or (nil? domain) (str/blank? domain))
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

(defn search-vault-notes
  "Search notes within a vault."
  [request]
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

(defn update-vault-root-note
  "Set the root note for a vault."
  [request]
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
                       :root-note-id root-note-id})))))

(defn get-root-note-selector
  "Render the root note selector component."
  [request]
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

(defn renew-vault-sync-key
  "Generate a new sync key for a vault."
  [request]
  (let [tenant-id (get-in request [:session :tenant-id])
        vault-id (get-in request [:path-params :id])
        vault (db/get-vault-by-id vault-id)
        new-sync-key (utils/generate-uuid)]
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
        (db/update-vault-sync-key! vault-id new-sync-key)
        {:status 200
         :body {:success true
                :message "Sync key renewed"
                :sync-key new-sync-key}}))))
