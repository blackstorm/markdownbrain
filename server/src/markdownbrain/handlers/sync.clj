(ns markdownbrain.handlers.sync
  (:require [markdownbrain.db :as db]
            [markdownbrain.utils :as utils]
            [markdownbrain.response :as resp]
            [markdownbrain.link-diff :as link-diff]
            [markdownbrain.validation :as validation]
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

;; 处理删除操作
(defn- handle-delete [vault-id clientId path]
  (log/info "Deleting document - Path:" path "ClientId:" clientId)
  (db/delete-document-by-client-id! vault-id clientId)
  (log/info "Document deleted successfully")
  (resp/success {:vault-id vault-id
                 :path path
                 :client-id clientId
                 :action "delete"}))

;; 处理创建/修改操作
(defn- handle-upsert [vault tenant-id vault-id clientId path content metadata hash mtime action]
  (log/info "Processing document upsert - Path:" path "ClientId:" clientId "Hash:" hash)

  ;; 检查文档是否已存在且 hash 相同（避免不必要的更新）
  (let [existing-doc (db/get-document-by-client-id vault-id clientId)]
    (if (and existing-doc hash (= hash (:hash existing-doc)))
      ;; Hash 相同，文件内容未变化，跳过更新
      (do
        (log/info "Document unchanged (same hash) - Path:" path "Hash:" hash)
        (resp/success {:vault-id vault-id
                       :path path
                       :client-id clientId
                       :action action
                       :skipped true
                       :reason "unchanged"}))

      ;; Hash 不同或文档不存在，执行 upsert
      (do
        (log/info "Upserting document - Path:" path "ClientId:" clientId)
        (let [doc-id (utils/generate-uuid)
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

          ;; 验证请求体
          (let [validation-result (validation/validate-sync-request (:body-params request))]
            (if (:valid? validation-result)
              (let [{:keys [path clientId content metadata hash mtime action]} (:body-params request)
                    vault-id (:id vault)
                    tenant-id (:tenant-id vault)]

                ;; 验证 content 是否在需要时存在
                (let [content-validation (validation/validate-content-required action (:body-params request))]
                  (if (:valid? content-validation)
                    (do
                      (log/debug "Parsed params - Path:" path "ClientId:" clientId "Action:" action "HasContent:" (some? content))

                      ;; 根据 action 分发到不同的处理函数
                      (case action
                        "delete" (handle-delete vault-id clientId path)
                        ("create" "modify") (handle-upsert vault tenant-id vault-id clientId path content metadata hash mtime action)
                        (resp/bad-request (str "Unknown action: " action))))

                    ;; Content validation failed
                    (do
                      (log/error "Content validation failed:" (:errors content-validation))
                      (resp/bad-request (:message content-validation) (:errors content-validation))))))

              ;; Request validation failed
              (do
                (log/error "Request validation failed:" (:errors validation-result))
                (resp/bad-request (:message validation-result) (:errors validation-result))))))
        (do
          (log/error "Invalid sync-key")
          (resp/unauthorized "Invalid sync-key"))))
    (do
      (log/error "Missing authorization header")
      (resp/unauthorized "Missing authorization header"))))

;; ============================================================
;; Full Sync - 孤儿文档清理
;; ============================================================

(defn- delete-and-count
  "删除孤儿文档并返回删除数量"
  [vault-id client-ids]
  (let [result (db/delete-documents-not-in-list! vault-id client-ids)]
    (log/info "Delete result:" result)
    (or (:update-count result) 0)))

(defn- execute-full-sync
  "执行 full-sync 并返回统计信息"
  [vault body]
  (let [vault-id (:id vault)
        client-ids (get-in body [:clientIds])
        server-count (count (db/list-document-client-ids-by-vault vault-id))
        client-count (count client-ids)]
    (log/info "Full sync - Vault:" vault-id "Server docs:" server-count "Client docs:" client-count)
    (let [deleted (delete-and-count vault-id client-ids)]
      (when (pos? deleted)
        (log/info "Deleted" deleted "orphan documents")
        (db/delete-orphan-links! vault-id))
      (resp/success {:vault-id vault-id
                     :action "full-sync"
                     :client-docs client-count
                     :deleted-count deleted
                     :remaining-docs (- server-count deleted)}))))

(defn sync-full
  "Full sync - 清理孤儿文档
   客户端发送完整文档列表，服务器删除不在列表中的文档"
  [request]
  (log/info "Full sync request received")
  (if-let [sync-key (parse-sync-key request)]
    (if-let [vault (validate-sync-key sync-key)]
      (let [validation-result (validation/validate-full-sync-request (:body-params request))]
        (if (:valid? validation-result)
          (execute-full-sync vault (:body-params request))
          (do
            (log/error "Full-sync validation failed:" (:errors validation-result))
            (resp/bad-request (:message validation-result) (:errors validation-result)))))
      (resp/unauthorized "Invalid sync-key"))
    (resp/unauthorized "Missing authorization header")))
