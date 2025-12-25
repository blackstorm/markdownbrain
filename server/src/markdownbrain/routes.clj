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

;; Frontend 路由 (端口 8080)
;; 仅包含：公开的文档展示
;; 使用单一的 catch-all 路由处理所有请求
;; - / (无UUID): 返回 root document
;; - /:id (有UUID): 返回指定文档，支持 ?stacked=xxx&stacked=yyy 参数
(def frontend-routes
  [["/*path" {:get frontend/get-note}]])

;; Admin 路由 (端口 9090)
;; 包含：管理后台、登录、Vault 管理、Obsidian 同步 API
(def admin-routes
  [;; Obsidian 同步接口
   ["/obsidian"
    ["/vault/info" {:get sync/vault-info}]
    ["/sync" {:post sync/sync-file}]]

   ;; 管理后台
   ["/admin"
    ["" {:middleware [middleware/wrap-auth]
         :get frontend/admin-home}]
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

;; Frontend App (8080)
(def frontend-app
  (ring/ring-handler
   (ring/router frontend-routes
                {:data {:muuntaja muuntaja-instance
                        :middleware [parameters/parameters-middleware
                                     muuntaja/format-middleware]}})
   (ring/create-default-handler)))

;; Admin App (9090)
(def admin-app
  (ring/ring-handler
   (ring/router admin-routes
                {:data {:muuntaja muuntaja-instance
                        :middleware [parameters/parameters-middleware
                                     muuntaja/format-middleware]}})
   (ring/create-default-handler)))
