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
            [markdownbrain.middleware :as middleware]))

(def muuntaja-instance
  (m/create
   (assoc-in
    m/default-options
    [:formats "application/json" :decoder-opts]
    {:decode-key-fn true})))

(def frontend-routes
  [["/{*path}" {:get frontend/get-note}]])

(def admin-routes
  [["/obsidian"
    ["/vault/info" {:get sync/vault-info}]
    ["/sync" {:post sync/sync-file}]
    ["/sync/full" {:post sync/sync-full}]]

   ["/admin"
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
                                       :get admin/get-root-note-selector}]]])

(def frontend-app
  (ring/ring-handler
   (ring/router frontend-routes
                {:data {:muuntaja muuntaja-instance
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
