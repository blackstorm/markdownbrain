(ns markdownbrain.handlers.sync
  (:require [markdownbrain.db :as db]
            [markdownbrain.utils :as utils]
            [markdownbrain.response :as resp]
            [markdownbrain.link-diff :as link-diff]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]))

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
  (log/debug "========== Sync Request ==========")
  (log/debug "Headers:" (:headers request))
  (log/debug "Content-Type:" (get-in request [:headers "content-type"]))
  (log/debug "Body params keys:" (keys (:body-params request)))

  (if-let [sync-key (parse-sync-key request)]
    (do
      (log/debug "Sync key parsed successfully")
      (if-let [vault (validate-sync-key sync-key)]
        (do
          (log/info "Vault found - ID:" (:id vault) "Name:" (:name vault) "Client-Type:" (:client-type vault))
          (let [{:keys [path clientId content metadata hash mtime action]} (:body-params request)
                vault-id (:id vault)]
            (log/debug "Parsed params - Path:" path "ClientId:" clientId "Action:" action "HasContent:" (some? content))

            (cond
              (nil? path)
              (do
                (log/error "Missing required field: path")
                (resp/bad-request "Missing required field: path"))

              (nil? clientId)
              (do
                (log/error "Missing required field: clientId")
                (resp/bad-request "Missing required field: clientId"))

              (nil? action)
              (do
                (log/error "Missing required field: action")
                (resp/bad-request "Missing required field: action"))

              (= action "delete")
              (do
                (log/info "Deleting document - Path:" path "ClientId:" clientId)
                (db/delete-document-by-client-id! vault-id clientId)
                (log/info "Document deleted successfully")
                (resp/success {:vault-id vault-id
                               :path path
                               :client-id clientId
                               :action action}))

              ;; "create" 和 "modify" 都使用 upsert
              :else
              (do
                (log/info "Upserting document - Path:" path "ClientId:" clientId)
                (let [doc-id (utils/generate-uuid)
                      tenant-id (:tenant-id vault)
                      metadata-str (when metadata (json/write-str metadata))]
                  (log/debug "Document details - DocId:" doc-id "ClientId:" clientId "TenantId:" tenant-id "VaultId:" vault-id)

                  ;; 保存文档
                  (db/upsert-document! doc-id tenant-id vault-id path clientId content metadata-str hash mtime)
                  (log/info "Document upserted successfully")

                  ;; 处理链接关系 - 使用 diff 算法
                  (let [new-links (or (:links metadata) [])
                        existing-links (db/get-document-links vault-id clientId)
                        ;; 计算 diff
                        operations (link-diff/compute-link-diff existing-links new-links)
                        summary (link-diff/summarize-diff operations)]

                    (log/info "Link diff analysis - Existing:" (count existing-links)
                             "New:" (count new-links)
                             "Operations:" (:total-operations summary)
                             "Deletes:" (:deletes summary)
                             "Inserts:" (:inserts summary)
                             "Updates:" (:updates summary))

                    ;; 执行操作
                    (doseq [operation operations]
                      (case (:op operation)
                        :delete
                        (do
                          (log/debug "Executing DELETE - Target:" (:target-client-id operation))
                          (db/delete-document-link-by-target! vault-id clientId (:target-client-id operation)))

                        :insert
                        (let [{:keys [target-client-id target-path link-type display-text original]} (:link-data operation)]
                          (log/debug "Executing INSERT - Target:" target-client-id "Path:" target-path)
                          (try
                            (db/insert-document-link! vault-id clientId target-client-id target-path
                                                     link-type display-text original)
                            (catch Exception e
                              (log/error "Failed to insert link:" (.getMessage e))
                              (log/error "Exception:" e))))))

                    (log/info "Links sync completed -" (:total-operations summary) "operations applied"))

                  (resp/success {:vault-id vault-id
                                 :path path
                                 :client-id clientId
                                 :action action}))))))
        (do
          (log/error "Invalid sync-key")
          (resp/unauthorized "Invalid sync-key"))))
    (do
      (log/error "Missing authorization header")
      (resp/unauthorized "Missing authorization header"))))
