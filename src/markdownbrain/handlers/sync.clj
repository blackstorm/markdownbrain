(ns markdownbrain.handlers.sync
  (:require [markdownbrain.db :as db]
            [markdownbrain.utils :as utils]
            [markdownbrain.response :as resp]
            [clojure.data.json :as json]))

;; 验证同步 key
(defn validate-sync-key [sync-key]
  (db/get-vault-by-sync-key sync-key))

;; 从 Authorization header 解析 sync-key
(defn parse-sync-key [request]
  (when-let [auth-header (get-in request [:headers "authorization"])]
    (when-let [[_ key] (re-matches #"Bearer\s+(.+)" auth-header)]
      key)))

;; Vault 信息接口
(defn vault-info [request]
  (if-let [sync-key (parse-sync-key request)]
    (if-let [vault (validate-sync-key sync-key)]
      (resp/ok {:vault {:id (:id vault)
                        :name (:name vault)
                        :domain (:domain vault)
                        :created-at (:created-at vault)}})
      (resp/unauthorized "Invalid sync-key"))
    (resp/unauthorized "Missing authorization header")))

;; 同步文件
(defn sync-file [request]
  (if-let [sync-key (parse-sync-key request)]
    (if-let [vault (validate-sync-key sync-key)]
      (let [{:keys [path content metadata hash mtime action]} (:body-params request)
            vault-id (:id vault)]
        (case action
          "delete"
          (do
            (db/delete-document! vault-id path)
            (resp/success {:vault-id vault-id
                           :path path
                           :action action}))

          ;; "create" 和 "modify" 都使用 upsert
          (let [doc-id (utils/generate-uuid)
                tenant-id (:tenant-id vault)
                metadata-str (when metadata (json/write-str metadata))]
            (db/upsert-document! doc-id tenant-id vault-id path content metadata-str hash mtime)
            (resp/success {:vault-id vault-id
                           :path path
                           :action action}))))
      (resp/unauthorized "Invalid sync-key"))
    (resp/unauthorized "Missing authorization header")))
