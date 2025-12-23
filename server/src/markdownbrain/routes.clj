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

   ;; Obsidian 同步接口
   ["/obsidian"
    ["/vault/info" {:get sync/vault-info}]
    ["/sync" {:post sync/sync-file}]]

   ;; 文档查询接口（用于前端展示）
   ["/documents" {:get frontend/get-documents}]
   ["/documents/:id" {:get frontend/get-document}]

   ;; 管理后台
   ["/admin"
    ["" {:get frontend/admin-home}]
    ["/login" {:get frontend/login-page
               :post admin/login}]
    ["/logout" {:post {:middleware [middleware/wrap-auth]
                       :handler admin/logout}}]
    ["/init" {:get frontend/init-page
              :post admin/init-admin}]
    ["/vaults" {:middleware [middleware/wrap-auth]
                :get admin/list-vaults
                :post admin/create-vault}]
    ["/vaults/:id" {:middleware [middleware/wrap-auth]
                    :put admin/update-vault
                    :delete admin/delete-vault}]
    ["/vaults/:id/documents" {:middleware [middleware/wrap-auth]
                              :get admin/search-vault-documents}]
    ["/vaults/:id/root-doc" {:middleware [middleware/wrap-auth]
                             :put admin/update-vault-root-doc}]
    ["/vaults/:id/root-doc-selector" {:middleware [middleware/wrap-auth]
                                      :get admin/get-root-doc-selector}]]])

(def app
  (ring/ring-handler
   (ring/router routes
                {:data {:muuntaja muuntaja-instance
                        :middleware [parameters/parameters-middleware
                                     muuntaja/format-middleware]}})
   (ring/create-default-handler)))
