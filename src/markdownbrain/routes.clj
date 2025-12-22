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

;; 自定义 Muuntaja 实例，确保 UTF-8 编码
(def muuntaja-instance
  (m/create
    (assoc-in
      m/default-options
      [:formats "application/json" :decoder-opts]
      {:decode-key-fn true})))

(def routes
  [["/" {:get frontend/home}]

   ["/api"
    ["/admin"
     ["/logout" {:post {:middleware [middleware/wrap-auth]
                        :handler admin/logout}}]]

    ["/vault"
     ["/info" {:get sync/vault-info}]]

    ["/sync" {:post sync/sync-file}]

    ["/documents" {:get frontend/get-documents}]
    ["/documents/:id" {:get frontend/get-document}]]

   ["/admin"
    ["" {:get frontend/admin-home}]
    ["/login" {:get frontend/login-page
               :post admin/login}]
    ["/init" {:get frontend/init-page
              :post admin/init-admin}]
    ["/vaults" {:middleware [middleware/wrap-auth]
                :get admin/list-vaults
                :post admin/create-vault}]
    ["/vaults/:id" {:middleware [middleware/wrap-auth]
                    :delete admin/delete-vault}]]])

(def app
  (ring/ring-handler
    (ring/router routes
                 {:data {:muuntaja muuntaja-instance
                         :middleware [parameters/parameters-middleware
                                      muuntaja/format-middleware]}})
    (ring/create-default-handler)))
