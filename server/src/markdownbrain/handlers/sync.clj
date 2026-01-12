(ns markdownbrain.handlers.sync
  (:require [markdownbrain.db :as db]
            [markdownbrain.utils :as utils]
            [markdownbrain.response :as resp]
            [markdownbrain.link-parser :as link-parser]
            [markdownbrain.validation :as validation]
            [markdownbrain.object-store :as store]
            [markdownbrain.config :as config]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [clojure.string :as str])
  (:import [java.util Base64]))

(defn validate-sync-key [sync-key]
  (db/get-vault-by-sync-key sync-key))

(defn parse-sync-key [request]
  (when-let [auth-header (get-in request [:headers "authorization"])]
    (when-let [[_ key] (re-matches #"Bearer\s+(.+)" auth-header)]
      key)))

;; ============================================================
;; Asset Reference Tracking
;; ============================================================

(defn- normalize-asset-path
  "规范化 asset 路径用于匹配
   - 移除前导 / 
   - 转小写
   
   示例: '/images/photo.png' -> 'images/photo.png'"
  [path]
  (-> path
      str/trim
      (str/replace #"^/" "")
      str/lower-case))

(defn- extract-filename
  "从路径中提取文件名（不含目录）
   
   示例: 'folder/subfolder/image.png' -> 'image.png'"
  [path]
  (-> path
      (str/split #"/")
      last
      str/lower-case))

(defn- resolve-embed-to-asset
  "将 embed 路径解析到 asset client-id
   
   匹配逻辑（与 Obsidian 一致）：
   1. 完整路径匹配
   2. 文件名匹配（跨目录）"
  [embed-path assets]
  (let [normalized (normalize-asset-path embed-path)
        filename (extract-filename embed-path)
        ;; 先尝试完整路径匹配
        full-match (first (filter #(= (normalize-asset-path (:path %)) normalized) assets))
        ;; 再尝试文件名匹配
        filename-match (first (filter #(= (extract-filename (:path %)) filename) assets))]
    (or full-match filename-match)))

(defn- extract-asset-refs-from-content
  "从 note 内容中提取所有 asset 引用的 client-id
   
   流程：
   1. 使用 link-parser 提取所有 embeds (![[...]])
   2. 解析每个 embed 到 asset client-id
   3. 返回有效的 asset client-ids"
  [content vault-id]
  (let [links (link-parser/extract-links content)
        embeds (filter #(= "embed" (:link-type %)) links)
        assets (db/list-assets-by-vault vault-id)]
    (when (seq embeds)
      (log/debug "Found" (count embeds) "embeds in note")
      (let [resolved-assets (keep #(resolve-embed-to-asset (:path %) assets) embeds)
            client-ids (map :client-id resolved-assets)]
        (log/debug "Resolved" (count client-ids) "assets from embeds")
        (distinct client-ids)))))

(defn- update-asset-refs-and-cleanup-orphans!
  "更新 note 的 asset 引用，并清理孤立 assets
   
   流程：
   1. 更新 note_asset_refs 表
   2. 找出引用计数变为 0 的 assets
   3. 软删除这些孤立 assets"
  [vault-id note-client-id new-asset-client-ids]
  ;; 获取旧的引用
  (let [old-refs (db/get-asset-refs-by-note vault-id note-client-id)
        old-asset-ids (set (map :asset-client-id old-refs))
        new-asset-ids (set new-asset-client-ids)
        ;; 找出被移除引用的 assets
        removed-asset-ids (clojure.set/difference old-asset-ids new-asset-ids)]
    
    ;; 更新引用
    (db/update-note-asset-refs! vault-id note-client-id new-asset-client-ids)
    (log/debug "Updated asset refs for note" note-client-id 
               "- Old:" (count old-asset-ids) "New:" (count new-asset-ids))
    
    ;; 检查被移除引用的 assets 是否成为孤儿
    (when (seq removed-asset-ids)
      (doseq [asset-id removed-asset-ids]
        (let [ref-count (db/count-asset-refs vault-id asset-id)]
          (when (zero? ref-count)
            (log/info "Soft deleting orphan asset - ClientId:" asset-id)
            (db/soft-delete-asset! vault-id asset-id)))))))

(defn- cleanup-orphan-assets-for-deleted-note!
  "当 note 被删除时，清理可能成为孤儿的 assets
   
   流程：
   1. 获取该 note 引用的所有 assets
   2. 删除该 note 的所有引用
   3. 检查每个 asset 是否成为孤儿
   4. 软删除孤儿 assets"
  [vault-id note-client-id]
  (let [refs (db/get-asset-refs-by-note vault-id note-client-id)
        asset-ids (map :asset-client-id refs)]
    ;; 先删除引用
    (db/delete-note-asset-refs-by-note! vault-id note-client-id)
    ;; 检查并清理孤儿
    (doseq [asset-id asset-ids]
      (let [ref-count (db/count-asset-refs vault-id asset-id)]
        (when (zero? ref-count)
          (log/info "Soft deleting orphan asset after note deletion - ClientId:" asset-id)
          (db/soft-delete-asset! vault-id asset-id))))))

(defn vault-info [request]
  (if-let [sync-key (parse-sync-key request)]
    (if-let [vault (validate-sync-key sync-key)]
      (resp/ok {:vault {:id (:id vault)
                        :name (:name vault)
                        :domain (:domain vault)
                        :created-at (:created-at vault)}})
      (resp/unauthorized "Invalid sync-key"))
    (resp/unauthorized "Missing authorization header")))

(defn- handle-delete [vault-id clientId path]
  (log/info "Deleting note - Path:" path "ClientId:" clientId)
  ;; 先清理 asset 引用和孤立 assets
  (cleanup-orphan-assets-for-deleted-note! vault-id clientId)
  ;; 再删除 note
  (db/delete-note-by-client-id! vault-id clientId)
  (log/info "Note deleted successfully")
  (resp/success {:vault-id vault-id
                 :path path
                 :client-id clientId
                 :action "delete"}))

(defn- handle-upsert [vault tenant-id vault-id clientId path content metadata hash mtime action]
  (log/info "Processing note upsert - Path:" path "ClientId:" clientId "Hash:" hash)

  (let [existing-note (db/get-note-by-client-id vault-id clientId)
        hash-unchanged? (and existing-note hash (= hash (:hash existing-note)))
        path-unchanged? (and existing-note (= path (:path existing-note)))]

    (cond
      (and hash-unchanged? path-unchanged?)
      (do
        (log/info "Note unchanged (same hash and path) - Path:" path "Hash:" hash)
        (resp/success {:vault-id vault-id
                       :path path
                       :client-id clientId
                       :action action
                       :skipped true
                       :reason "unchanged"}))

      (and hash-unchanged? (not path-unchanged?))
      (do
        (log/info "Note renamed - Old path:" (:path existing-note) "New path:" path)
        (db/update-note-path! vault-id clientId path)
        (resp/success {:vault-id vault-id
                       :path path
                       :client-id clientId
                       :action action
                       :renamed true
                       :old-path (:path existing-note)}))

      :else
      (do
        (log/info "Upserting note - Path:" path "ClientId:" clientId)
        (let [note-id (utils/generate-uuid)
              metadata-str (when metadata (json/write-str metadata))]
          (log/debug "Note details - NoteId:" note-id "ClientId:" clientId "TenantId:" tenant-id "VaultId:" vault-id)

          (db/upsert-note! note-id tenant-id vault-id path clientId content metadata-str hash mtime)
          (log/info "Note upserted successfully")

          (let [all-notes (db/get-notes-for-link-resolution vault-id)
                extracted-links (link-parser/extract-links content)
                resolved-links (link-parser/resolve-links extracted-links all-notes)
                valid-links (filter :target-client-id resolved-links)]

            (log/info "Link parsing - Extracted:" (count extracted-links)
                     "Resolved:" (count resolved-links)
                     "Valid:" (count valid-links))

            (db/delete-note-links-by-source! vault-id clientId)

            (doseq [link valid-links]
              (try
                (db/insert-note-link! vault-id clientId
                                      (:target-client-id link)
                                      (:target-path link)
                                      (:link-type link)
                                      (:display-text link)
                                      (:original link))
                (catch Exception e
                  (log/error "Failed to insert link:" (.getMessage e)))))

            (log/info "Links sync completed - Inserted:" (count valid-links)))

          ;; Asset reference tracking
          (let [asset-client-ids (extract-asset-refs-from-content content vault-id)]
            (update-asset-refs-and-cleanup-orphans! vault-id clientId (or asset-client-ids [])))

          (resp/success {:vault-id vault-id
                         :path path
                         :client-id clientId
                         :action action}))))))

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

          (let [validation-result (validation/validate-sync-request (:body-params request))]
            (if (:valid? validation-result)
              (let [{:keys [path clientId content metadata hash mtime action]} (:body-params request)
                    vault-id (:id vault)
                    tenant-id (:tenant-id vault)]

                (let [content-validation (validation/validate-content-required action (:body-params request))]
                  (if (:valid? content-validation)
                    (do
                      (log/debug "Parsed params - Path:" path "ClientId:" clientId "Action:" action "HasContent:" (some? content))

                      (case action
                        "delete" (handle-delete vault-id clientId path)
                        ("create" "modify") (handle-upsert vault tenant-id vault-id clientId path content metadata hash mtime action)
                        (resp/bad-request (str "Unknown action: " action))))

                    (do
                      (log/error "Content validation failed:" (:errors content-validation))
                      (resp/bad-request (:message content-validation) (:errors content-validation))))))

              (do
                (log/error "Request validation failed:" (:errors validation-result))
                (resp/bad-request (:message validation-result) (:errors validation-result))))))
        (do
          (log/error "Invalid sync-key")
          (resp/unauthorized "Invalid sync-key"))))
    (do
      (log/error "Missing authorization header")
      (resp/unauthorized "Missing authorization header"))))

;; Full Sync - 孤儿笔记清理

(defn- delete-and-count
  [vault-id client-ids]
  (let [result (db/delete-notes-not-in-list! vault-id client-ids)]
    (log/info "Delete result:" result)
    (or (:update-count result) 0)))

(defn- execute-full-sync
  [vault body]
  (let [vault-id (:id vault)
        client-ids (get-in body [:clientIds])
        server-count (count (db/list-note-client-ids-by-vault vault-id))
        client-count (count client-ids)]
    (log/info "Full sync - Vault:" vault-id "Server notes:" server-count "Client notes:" client-count)
    (let [deleted (delete-and-count vault-id client-ids)]
      (when (pos? deleted)
        (log/info "Deleted" deleted "orphan notes")
        (db/delete-orphan-links! vault-id))
      (resp/success {:vault-id vault-id
                     :action "full-sync"
                     :client-notes client-count
                     :deleted-count deleted
                     :remaining-notes (- server-count deleted)}))))

(defn sync-full
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

;; ============================================================
;; Asset Sync
;; ============================================================

(defn- decode-base64 [s]
  (when s
    (.decode (Base64/getDecoder) s)))

(defn- handle-asset-delete [vault-id client-id path]
  (log/info "Soft deleting asset - ClientId:" client-id "Path:" path)
  (db/soft-delete-asset! vault-id client-id)
  (resp/success {:vault-id vault-id
                 :client-id client-id
                 :path path
                 :action "delete"}))

(defn- handle-asset-upsert [vault tenant-id vault-id client-id path size content-type sha256 content-base64]
  (log/info "Processing asset upsert - ClientId:" client-id "Path:" path "Size:" size "Type:" content-type)

  (let [existing (db/get-asset-by-client-id vault-id client-id)
        hash-unchanged? (and existing sha256 (= sha256 (:sha256 existing)))
        path-changed? (and existing (not= path (:path existing)))]

    (cond
      (and hash-unchanged? (not path-changed?))
      (do
        (log/info "Asset unchanged - ClientId:" client-id)
        (resp/success {:vault-id vault-id
                       :client-id client-id
                       :path path
                       :action "unchanged"
                       :skipped true}))

      (and hash-unchanged? path-changed?)
      (do
        (log/info "Asset moved (path changed, same content) - ClientId:" client-id "OldPath:" (:path existing) "NewPath:" path)
        (db/upsert-asset! (utils/generate-uuid) tenant-id vault-id client-id path (:object-key existing) size content-type sha256)
        (resp/success {:vault-id vault-id
                       :client-id client-id
                       :path path
                       :action "moved"
                       :old-path (:path existing)}))

      :else
      (if-not (config/storage-enabled?)
        (do
          (log/warn "Storage not configured, skipping asset upload")
          (resp/error 503 "Storage not configured"))
        (let [extension (store/content-type->extension content-type)
              object-key (if existing
                           (:object-key existing)
                           (store/asset-object-key client-id extension))
              content-bytes (decode-base64 content-base64)]
          (if-not content-bytes
            (resp/bad-request "Invalid or missing base64 content")
            (do
              (log/info "Uploading asset - Object-key:" object-key)
              (store/put-object! vault-id object-key content-bytes content-type)

              (let [asset-id (utils/generate-uuid)]
                (db/upsert-asset! asset-id tenant-id vault-id client-id path object-key size content-type sha256)
                (log/info "Asset upserted successfully - ClientId:" client-id)

                (resp/success {:vault-id vault-id
                               :client-id client-id
                               :path path
                               :object-key object-key
                               :action "upserted"})))))))))

(defn sync-asset [request]
  (log/debug "========== Asset Sync Request ==========")
  (log/debug "Headers:" (:headers request))
  (log/debug "Body params keys:" (keys (:body-params request)))

  (if-let [sync-key (parse-sync-key request)]
    (if-let [vault (validate-sync-key sync-key)]
      (let [validation-result (validation/validate-asset-sync-request (:body-params request))]
        (if (:valid? validation-result)
          (let [{:keys [path clientId action size contentType sha256 content]} (:body-params request)
                vault-id (:id vault)
                tenant-id (:tenant-id vault)]

            (let [metadata-validation (validation/validate-asset-metadata-required action (:body-params request))]
              (if (:valid? metadata-validation)
                (do
                  (log/debug "Parsed asset params - ClientId:" clientId "Path:" path "Action:" action "Size:" size)
                  (case action
                    "delete" (handle-asset-delete vault-id clientId path)
                    ("create" "modify") (handle-asset-upsert vault tenant-id vault-id clientId path size contentType sha256 content)
                    (resp/bad-request (str "Unknown action: " action))))
                (do
                  (log/error "Metadata validation failed:" (:errors metadata-validation))
                  (resp/bad-request (:message metadata-validation) (:errors metadata-validation))))))
          (do
            (log/error "Asset sync validation failed:" (:errors validation-result))
            (resp/bad-request (:message validation-result) (:errors validation-result)))))
      (do
        (log/error "Invalid sync-key for asset sync")
        (resp/unauthorized "Invalid sync-key")))
    (do
      (log/error "Missing authorization header for asset sync")
      (resp/unauthorized "Missing authorization header"))))
