(ns markdownbrain.routes
  (:require
   [markdownbrain.config :as config]
   [markdownbrain.handlers.console :as console]
   [markdownbrain.handlers.frontend :as frontend]
   [markdownbrain.handlers.internal :as internal]
   [markdownbrain.handlers.sync :as sync]
   [markdownbrain.middleware :as middleware]
   [markdownbrain.response :as response]
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

(def console-routes
  (let [base-routes
        [["/" {:get (fn [_] (response/redirect "/console"))}]

         ["/obsidian"
          ["/sync"
           ["/changes" {:post sync/sync-changes}]
           ["/notes/:id" {:post sync/sync-note}]
           ["/assets/:id" {:post sync/sync-asset}]]
          ["/vault/info" {:get sync/vault-info}]]

         ["/console"
          ["" {:middleware [middleware/wrap-auth] :get console/console-home}]
          ["/health" {:get internal/health}]
          ["/login" {:get console/login-page
                     :post console/login}]
          ["/logout" {:post {:middleware [middleware/wrap-auth]
                             :handler console/logout}}]
          ["/user/password" {:middleware [middleware/wrap-auth]
                             :put console/change-password}]
          ["/init" {:get console/init-page
                    :post console/init-console}]
          ["/storage/:id/{*path}" {:middleware [middleware/wrap-auth]
                                   :get console/serve-console-asset}]
          ["/vaults" {:middleware [middleware/wrap-auth]
                      :get console/list-vaults
                      :post console/create-vault}]
          ["/vaults/:id" {:middleware [middleware/wrap-auth]
                          :put console/update-vault
                          :delete console/delete-vault}]
          ["/vaults/:id/notes" {:middleware [middleware/wrap-auth]
                                :get console/search-vault-notes}]
          ["/vaults/:id/root-note" {:middleware [middleware/wrap-auth]
                                    :put console/update-vault-root-note}]
          ["/vaults/:id/root-note-selector" {:middleware [middleware/wrap-auth]
                                             :get console/get-root-note-selector}]
          ["/vaults/:id/renew-sync-key" {:middleware [middleware/wrap-auth]
                                         :post console/renew-vault-sync-key}]
          ["/vaults/:id/custom-head-html" {:middleware [middleware/wrap-auth]
                                           :put console/update-custom-head-html}]
          ["/vaults/:id/logo" {:get {:middleware [middleware/wrap-auth]
                                     :handler console/serve-vault-logo}
                               :post {:middleware [middleware/wrap-auth]
                                      :handler console/upload-vault-logo}
                               :delete {:middleware [middleware/wrap-auth]
                                        :handler console/delete-vault-logo}}]
          ["/vaults/:id/favicon" {:get {:middleware [middleware/wrap-auth]
                                        :handler console/serve-vault-favicon}}]]]
        domain-check-route ["/console/domain-check" {:get internal/domain-check}]]
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

(def console-app
  (ring/ring-handler
   (ring/router console-routes
                {:data {:muuntaja muuntaja-instance
                        :middleware [parameters/parameters-middleware
                                     muuntaja/format-middleware]}})
   (ring/create-default-handler)))
