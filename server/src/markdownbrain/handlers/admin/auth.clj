(ns markdownbrain.handlers.admin.auth
  "Admin authentication and initialization handlers."
  (:require
   [markdownbrain.db :as db]
   [markdownbrain.response :as resp]
   [markdownbrain.utils :as utils]
   [selmer.parser :as selmer]))

(defn init-admin
  "Initialize system with first admin user."
  [request]
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

(defn login
  "Admin login handler."
  [request]
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

(defn logout
  "Admin logout handler."
  [request]
  {:status 302
   :session nil
   :headers {"Location" "/admin/login"}})

(defn login-page
  "Render login page."
  [request]
  (resp/html (selmer/render-file "templates/admin/login.html" {})))

(defn init-page
  "Render initialization page."
  [request]
  (resp/html (selmer/render-file "templates/admin/init.html" {})))
