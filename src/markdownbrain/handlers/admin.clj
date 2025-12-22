(ns markdownbrain.handlers.admin
  (:require [markdownbrain.db :as db]
            [markdownbrain.utils :as utils]
            [markdownbrain.config :as config]
            [markdownbrain.response :as resp]
            [ring.util.response :as response]))

;; 初始化管理员用户
(defn init-admin [request]
  (let [params (or (:body-params request) (:params request))
        {:keys [username password tenant-name]} params]
    (cond
      (or (nil? username) (nil? password) (nil? tenant-name))
      (resp/bad-request "缺少必填字段")

      (db/get-user-by-username username)
      (resp/bad-request "用户名已存在")

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
  (let [{:keys [username password]} (:body-params request)
        user (db/get-user-by-username username)]
    (if (and user (utils/verify-password password (:password-hash user)))
      (resp/success {:user {:id (:id user)
                            :username (:username user)
                            :tenant-id (:tenant-id user)}}
                    {:user-id (:id user)
                     :tenant-id (:tenant-id user)})
      (resp/unauthorized "用户名或密码错误"))))

;; 管理员登出
(defn logout [request]
  (resp/success {} nil))

;; 列出 vault
(defn list-vaults [request]
  (let [tenant-id (get-in request [:session :tenant-id])
        vaults (db/list-vaults-by-tenant tenant-id)]
    (resp/ok (mapv #(select-keys % [:id :name :domain :sync-key :created-at])
                   vaults))))

;; 创建 vault
(defn create-vault [request]
  (let [tenant-id (get-in request [:session :tenant-id])
        {:keys [name domain]} (:body-params request)]
    (if (or (nil? name) (nil? domain))
      (resp/bad-request "缺少必填字段")
      (let [vault-id (utils/generate-uuid)
            sync-key (utils/generate-uuid)]
        (db/create-vault! vault-id tenant-id name domain sync-key)
        (resp/success {:vault {:id vault-id
                               :name name
                               :domain domain
                               :sync-key sync-key}})))))
