(ns markdownbrain.handlers.admin
  (:require [markdownbrain.db :as db]
            [markdownbrain.utils :as utils]
            [markdownbrain.config :as config]
            [markdownbrain.response :as resp]
            [ring.util.response :as response]
            [selmer.parser :as selmer]))

;; 初始化管理员用户
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

;; 管理员登录
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
      ;; 登录失败，返回 200 状态码但 success 为 false
      {:status 200
       :body {:success false
              :error "Invalid username or password"}})))

;; 管理员登出
(defn logout [request]
  (resp/success {} nil))

;; 列出 vault
(defn list-vaults [request]
  (let [tenant-id (get-in request [:session :tenant-id])
        vaults (db/list-vaults-by-tenant tenant-id)
        vaults-with-masked (mapv (fn [vault]
                                   (let [sync-key (:sync-key vault)
                                         masked (str (subs sync-key 0 8) "******" (subs sync-key (- (count sync-key) 8)))]
                                     (assoc vault :masked-key masked)))
                                 vaults)]
    (resp/html (selmer/render-file "templates/admin/vault-list.html"
                                   {:vaults vaults-with-masked}))))

;; 创建 vault
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

;; 删除 vault
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

;; 搜索 vault 中的文档
(defn search-vault-documents [request]
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
      (let [documents (db/search-documents-by-vault vault-id query)]
        (resp/success {:documents documents})))))

;; 更新 vault 的首页文档
(defn update-vault-root-doc [request]
  (let [tenant-id (get-in request [:session :tenant-id])
        vault-id (get-in request [:path-params :id])
        params (or (:body-params request) (:params request))
        root-doc-id (:rootDocId params)
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

      (nil? root-doc-id)
      {:status 200
       :body {:success false
              :error "Missing rootDocId"}}

      :else
      (do
        (db/update-vault-root-doc! vault-id root-doc-id)
        (resp/success {:message "Root document updated"
                       :rootDocId root-doc-id})))))

;; 获取 vault 的首页文档选择器（返回 HTML 片段）
(defn get-root-doc-selector [request]
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
      (let [documents (db/search-documents-by-vault vault-id "")
            root-doc-id (:root-doc-id vault)]
        {:status 200
         :headers {"Content-Type" "text/html"}
         :body (selmer/render-file "templates/admin/root-doc-selector.html"
                                    {:documents documents
                                     :vault-id vault-id
                                     :root-doc-id root-doc-id})}))))
