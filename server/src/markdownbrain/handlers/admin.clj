(ns markdownbrain.handlers.admin
  (:require
   [markdownbrain.db :as db]
   [markdownbrain.response :as resp]
   [markdownbrain.utils :as utils]
   [selmer.parser :as selmer]))

(defn init-admin [request]
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
                       :user-id user-id})))))

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

(defn admin-home [request]
  (let [tenant-id (get-in request [:session :tenant-id])
        tenant (db/get-tenant tenant-id)
        vaults (db/list-vaults-by-tenant tenant-id)
        vaults-with-data (mapv (fn [vault]
                                 (let [sync-key (:sync-key vault)
                                       masked (str (subs sync-key 0 8) "******" (subs sync-key (- (count sync-key) 8)))
                                       notes (db/search-notes-by-vault (:id vault) "")]
                                   (assoc vault
                                          :masked-key masked
                                          :notes notes)))
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
                                       notes (db/search-notes-by-vault (:id vault) "")]
                                   (assoc vault
                                          :masked-key masked
                                          :notes notes)))
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
