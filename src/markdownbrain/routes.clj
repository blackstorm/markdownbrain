(ns markdownbrain.routes
  (:require [reitit.ring :as ring]
            [reitit.ring.middleware.parameters :as parameters]
            [markdownbrain.handlers.admin :as admin]
            [markdownbrain.handlers.sync :as sync]
            [markdownbrain.handlers.frontend :as frontend]
            [markdownbrain.middleware :as middleware]))

(def routes
  [["/" {:get frontend/home}]

   ["/api"
    ["/admin"
     ["/init" {:post admin/init-admin}]
     ["/login" {:post admin/login}]
     ["/logout" {:post {:middleware [middleware/wrap-auth]
                        :handler admin/logout}}]
     ["/vaults" {:get {:middleware [middleware/wrap-auth]
                       :handler admin/list-vaults}
                 :post {:middleware [middleware/wrap-auth]
                        :handler admin/create-vault}}]]

    ["/sync" {:post sync/sync-file}]

    ["/documents" {:get frontend/get-documents}]
    ["/documents/:id" {:get frontend/get-document}]]

   ["/admin"
    ["" {:get frontend/admin-home}]
    ["/login" {:get frontend/login-page}]]])

(def app
  (ring/ring-handler
    (ring/router routes
                 {:data {:middleware [parameters/parameters-middleware]}})
    (ring/create-default-handler)))
