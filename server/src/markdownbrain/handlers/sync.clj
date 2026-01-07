(ns markdownbrain.handlers.sync
  (:require [markdownbrain.db :as db]
            [markdownbrain.utils :as utils]
            [markdownbrain.response :as resp]
            [markdownbrain.link-parser :as link-parser]
            [markdownbrain.validation :as validation]
            [markdownbrain.object-store :as store]
            [markdownbrain.config :as config]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log])
  (:import [java.util Base64]))

(defn validate-sync-key [sync-key]
  (db/get-vault-by-sync-key sync-key))

(defn parse-sync-key [request]
  (when-let [auth-header (get-in request [:headers "authorization"])]
    (when-let [[_ key] (re-matches #"Bearer\s+(.+)" auth-header)]
      key)))

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
        hash-unchanged? (and existing sha256 (= sha256 (:sha256 existing)))]

    (if hash-unchanged?
      (do
        (log/info "Asset unchanged (same hash) - ClientId:" client-id)
        (resp/success {:vault-id vault-id
                       :client-id client-id
                       :path path
                       :action "unchanged"
                       :skipped true}))
      (if-not (config/s3-enabled?)
        (do
          (log/warn "S3 not configured, skipping asset upload")
          (resp/error 503 "S3 storage not configured"))
        (let [object-key (store/asset-object-key path)
              content-bytes (decode-base64 content-base64)]
          (if-not content-bytes
            (resp/bad-request "Invalid or missing base64 content")
            (do
              (log/info "Uploading asset to S3 - Object-key:" object-key)
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
