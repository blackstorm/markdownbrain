(ns markdownbrain.routes
  (:require [reitit.ring :as ring]
            [reitit.ring.middleware.parameters :as parameters]
            [reitit.ring.coercion :as coercion]
            [reitit.coercion.spec]
            [muuntaja.core :as m]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [markdownbrain.handlers.admin :as admin]
            [markdownbrain.handlers.sync :as sync]
            [markdownbrain.handlers.frontend :as frontend]
            [markdownbrain.middleware :as middleware]
            [markdownbrain.db :as db]
            [markdownbrain.config :as config]))

(def muuntaja-instance
  (m/create
   (assoc-in
    m/default-options
    [:formats "application/json" :decoder-opts]
    {:decode-key-fn true})))

(defn frontend-dispatch
  "Dispatch requests based on path prefix.
   /r/* -> get-resource
   /* -> get-note"
  [request]
  (let [path (get-in request [:path-params :path] "")]
    (if (clojure.string/starts-with? path "r/")
      ;; Resource path: strip 'r/' prefix and call get-resource
      (let [resource-path (subs path 2)]
        (frontend/get-resource (assoc-in request [:path-params :path] resource-path)))
      ;; Note path: call get-note as-is
      (frontend/get-note request))))

(def frontend-routes
  [["/" {:get frontend/get-note}]
   ["/{*path}" {:get frontend-dispatch}]])

(defn valid-internal-token? [req]
  (let [token (config/internal-token)
        req-token (or (get-in req [:headers "authorization"])
                      (get-in req [:query-params "token"]))]
    (and token
         (or (= req-token token)
             (= req-token (str "Bearer " token))))))

(def admin-routes
  [["/obsidian"
    ["/vault/info" {:get sync/vault-info}]
    ["/sync" {:post sync/sync-file}]
    ["/sync/full" {:post sync/sync-full}]
    ["/resources/sync" {:post sync/sync-resource}]]

   ["/admin"
    ["/health" {:get (fn [req]
                       (if (valid-internal-token? req)
                         {:status 200 :body "ok"}
                         {:status 401 :body "unauthorized"}))}]
    ["/domain-check" {:get (fn [req]
                             (if-not (valid-internal-token? req)
                               {:status 401 :body "unauthorized"}
                               (let [domain (get-in req [:query-params "domain"])]
                                 (if (and domain (db/get-vault-by-domain domain))
                                   {:status 200 :body "ok"}
                                   {:status 404 :body "not found"}))))}]
    ["" {:middleware [middleware/wrap-auth]
         :get admin/admin-home}]
    ["/login" {:get admin/login-page
               :post admin/login}]
    ["/logout" {:post {:middleware [middleware/wrap-auth]
                       :handler admin/logout}}]
    ["/init" {:get admin/init-page
              :post admin/init-admin}]
    ["/vaults" {:middleware [middleware/wrap-auth]
                :get admin/list-vaults
                :post admin/create-vault}]
    ["/vaults/:id" {:middleware [middleware/wrap-auth]
                    :put admin/update-vault
                    :delete admin/delete-vault}]
    ["/vaults/:id/notes" {:middleware [middleware/wrap-auth]
                          :get admin/search-vault-notes}]
    ["/vaults/:id/root-note" {:middleware [middleware/wrap-auth]
                              :put admin/update-vault-root-note}]
    ["/vaults/:id/root-note-selector" {:middleware [middleware/wrap-auth]
                                        :get admin/get-root-note-selector}]
    ["/vaults/:id/logo" {:get admin/get-vault-logo
                         :post {:middleware [middleware/wrap-auth]
                                :handler admin/upload-vault-logo}
                         :delete {:middleware [middleware/wrap-auth]
                                  :handler admin/delete-vault-logo}}]]])

(def frontend-app
  (ring/ring-handler
   (ring/router frontend-routes
                {:conflicts nil
                 :data {:muuntaja muuntaja-instance
                        :middleware [parameters/parameters-middleware
                                     muuntaja/format-middleware]}})
   (ring/create-default-handler)))

(def admin-app
  (ring/ring-handler
   (ring/router admin-routes
                {:data {:muuntaja muuntaja-instance
                        :middleware [parameters/parameters-middleware
                                     muuntaja/format-middleware]}})
   (ring/create-default-handler)))
