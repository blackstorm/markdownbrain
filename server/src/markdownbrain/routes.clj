(ns markdownbrain.routes
  (:require
   [markdownbrain.config :as config]
   [markdownbrain.handlers.admin :as admin]
   [markdownbrain.handlers.frontend :as frontend]
   [markdownbrain.handlers.internal :as internal]
   [markdownbrain.handlers.sync :as sync]
   [markdownbrain.middleware :as middleware]
   [muuntaja.core :as m]
   [reitit.ring :as ring]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.middleware.parameters :as parameters]))

(def muuntaja-instance
  (m/create
   (assoc-in
    m/default-options
    [:formats "application/json" :decoder-opts]
    {:decode-key-fn true})))

(def frontend-routes
  [["/favicon.ico" {:get frontend/serve-favicon}]
   ["/storage/{*path}" {:get frontend/serve-asset}]
   ["/" {:get frontend/get-note}]
   ["/{*path}" {:get frontend/get-note}]])

(def admin-routes
  (let [base-routes
        [["/obsidian"
          ["/vault/info" {:get sync/vault-info}]
          ["/sync" {:post sync/sync-file}]
          ["/sync/full" {:post sync/sync-full}]
          ["/assets/sync" {:post sync/sync-asset}]]

         ["/admin" 
          ["" {:middleware [middleware/wrap-auth] :get admin/admin-home}]
          ["/health" {:get internal/health}]
          ["/login" {:get admin/login-page
                     :post admin/login}]
          ["/logout" {:post {:middleware [middleware/wrap-auth]
                             :handler admin/logout}}]
          ["/init" {:get admin/init-page
                    :post admin/init-admin}]
          ["/storage/:id/{*path}" {:middleware [middleware/wrap-auth]
                                   :get admin/serve-admin-asset}]
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
          ["/vaults/:id/renew-sync-key" {:middleware [middleware/wrap-auth]
                                         :post admin/renew-vault-sync-key}]
          ["/vaults/:id/logo" {:get {:middleware [middleware/wrap-auth]
                                      :handler admin/serve-vault-logo}
                               :post {:middleware [middleware/wrap-auth]
                                      :handler admin/upload-vault-logo}
                               :delete {:middleware [middleware/wrap-auth]
                                        :handler admin/delete-vault-logo}}]
          ["/vaults/:id/favicon" {:get {:middleware [middleware/wrap-auth]
                                        :handler admin/serve-vault-favicon}}]]]
        ;; Conditionally add domain-check route when on-demand TLS is enabled
        domain-check-route ["/admin/domain-check" {:get internal/domain-check}]]
    (if (config/on-demand-tls-enabled?)
      (conj base-routes domain-check-route)
      base-routes)))

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
