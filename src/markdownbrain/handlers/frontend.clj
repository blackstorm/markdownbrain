(ns markdownbrain.handlers.frontend
  (:require [markdownbrain.db :as db]
            [markdownbrain.response :as resp]
            [selmer.parser :as selmer]
            [ring.util.response :as response]))

;; 获取当前域名对应的 vault
(defn get-current-vault [request]
  (when-let [host (get-in request [:headers "host"])]
    (let [domain (first (clojure.string/split host #":"))]
      (db/get-vault-by-domain domain))))

;; 首页 - 显示 vault 信息
(defn home [request]
  (if-let [vault (get-current-vault request)]
    (let [documents (db/list-documents-by-vault (:id vault))]
      (resp/html (selmer/render-file "templates/frontend/home.html"
                                     {:vault vault
                                      :documents documents})))
    {:status 404
     :body "未找到站点"}))

;; API: 获取文档列表
(defn get-documents [request]
  (if-let [vault (get-current-vault request)]
    (resp/ok (db/list-documents-by-vault (:id vault)))
    (resp/not-found "Vault not found")))

;; API: 获取单个文档
(defn get-document [request]
  (let [doc-id (get-in request [:path-params :id])]
    (if-let [doc (db/get-document doc-id)]
      (resp/ok doc)
      (resp/not-found "Document not found"))))

;; 管理后台首页
(defn admin-home [request]
  (resp/html (selmer/render-file "templates/admin/vaults.html" {})))

;; 登录页面
(defn login-page [request]
  (resp/html (selmer/render-file "templates/admin/login.html" {})))

;; 初始化页面
(defn init-page [request]
  (resp/html (selmer/render-file "templates/admin/init.html" {})))
