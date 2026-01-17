(ns markdownbrain.handlers.sync-v2
  "Sync V2 Protocol: /v1/sync/plan and /v1/sync/commit
   
   Core principles:
   - Client is the source of truth (unidirectional sync)
   - Explicit deletes only (no delete-by-absence)
   - Atomic plan→commit with syncToken
   - Idempotent retry with same syncToken
   - Soft delete with retention period"
  (:require [markdownbrain.db :as db]
            [markdownbrain.utils :as utils]
            [markdownbrain.response :as resp]
            [markdownbrain.link-parser :as link-parser]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [clojure.string :as str]))

;; =============================================================================
;; Auth Helpers
;; =============================================================================

(defn- parse-sync-key [request]
  (when-let [auth-header (get-in request [:headers "authorization"])]
    (when-let [[_ key] (re-matches #"Bearer\s+(.+)" auth-header)]
      key)))

(defn- validate-sync-key [sync-key]
  (db/get-vault-by-sync-key sync-key))

;; =============================================================================
;; Database Extensions for V2
;; =============================================================================

(defn- get-vault-last-applied-rev [vault-id]
  (or (:last-applied-rev (db/get-vault-by-id vault-id)) 0))

(defn- update-vault-last-applied-rev! [vault-id rev]
  (db/execute-one! ["UPDATE vaults SET last_applied_rev = ? WHERE id = ?" rev vault-id]))

(defn- get-note-hash [vault-id file-id]
  (:hash (db/get-note-by-client-id vault-id file-id)))

(defn- list-active-notes [vault-id]
  (db/execute! ["SELECT client_id, path, hash FROM notes 
                 WHERE vault_id = ? AND deleted_at IS NULL" vault-id]))

(defn- soft-delete-note! [vault-id file-id]
  (db/execute-one! ["UPDATE notes SET deleted_at = strftime('%s', 'now'), 
                     updated_at = CURRENT_TIMESTAMP 
                     WHERE vault_id = ? AND client_id = ? AND deleted_at IS NULL"
                    vault-id file-id]))

(defn- upsert-note-v2! [tenant-id vault-id file-id path content hash metadata]
  (let [note-id (utils/generate-uuid)
        metadata-str (when metadata (json/write-str metadata))]
    (db/execute-one!
      ["INSERT INTO notes (id, tenant_id, vault_id, path, client_id, content, metadata, hash, deleted_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL)
        ON CONFLICT(vault_id, client_id) DO UPDATE SET
          path = excluded.path,
          content = excluded.content,
          metadata = excluded.metadata,
          hash = excluded.hash,
          deleted_at = NULL,
          updated_at = CURRENT_TIMESTAMP"
       note-id tenant-id vault-id path file-id content metadata-str hash])))

;; =============================================================================
;; Sync Plan Storage
;; =============================================================================

(def ^:private plan-expiry-seconds 3600) ;; 1 hour

(defn- create-sync-plan! [vault-id from-rev to-rev need-upload deletes]
  (let [plan-id (utils/generate-uuid)
        expires-at (+ (quot (System/currentTimeMillis) 1000) plan-expiry-seconds)]
    (db/execute-one!
      ["INSERT INTO sync_plans (id, vault_id, from_rev, to_rev, need_upload, deletes, expires_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)"
       plan-id vault-id from-rev to-rev
       (json/write-str need-upload)
       (json/write-str deletes)
       expires-at])
    plan-id))

(defn- get-sync-plan [plan-id vault-id]
  (let [plan (db/execute-one!
               ["SELECT * FROM sync_plans WHERE id = ? AND vault_id = ?"
                plan-id vault-id])]
    (when plan
      (-> plan
          (update :need-upload #(when % (json/read-str % :key-fn keyword)))
          (update :deletes #(when % (json/read-str % :key-fn keyword)))))))

(defn- plan-expired? [plan]
  (let [now (quot (System/currentTimeMillis) 1000)]
    (and (> (:expires-at plan) 0)
         (< (:expires-at plan) now))))

(defn- plan-already-applied? [plan]
  (= 0 (:expires-at plan)))

(defn- mark-plan-applied! [plan-id]
  (db/execute-one! ["UPDATE sync_plans SET expires_at = 0 WHERE id = ?" plan-id]))

;; =============================================================================
;; Plan Logic
;; =============================================================================

(defn- process-upsert-op [vault-id op]
  (let [{:keys [fileId hash]} op
        existing-hash (get-note-hash vault-id fileId)]
    (if (= existing-hash hash)
      {:type :already-have :fileId fileId :hash hash}
      {:type :need-upload :fileId fileId :hash hash})))

(defn- process-delete-op [vault-id op]
  (let [{:keys [fileId ifMatchHash]} op
        existing-hash (get-note-hash vault-id fileId)]
    (cond
      (nil? existing-hash)
      {:fileId fileId :status "not-found"}
      
      (not= existing-hash ifMatchHash)
      {:fileId fileId :status "rejected-precondition" :reason "ifMatchHash mismatch"
       :expected ifMatchHash :actual existing-hash}
      
      :else
      {:fileId fileId :status "accepted"})))

(defn- process-incremental-ops [vault-id ops]
  (let [upserts (filter #(= "upsert" (:op %)) ops)
        deletes (filter #(= "delete" (:op %)) ops)
        upsert-results (map #(process-upsert-op vault-id %) upserts)
        delete-results (map #(process-delete-op vault-id %) deletes)]
    {:need-upload (filter #(= :need-upload (:type %)) upsert-results)
     :already-have (filter #(= :already-have (:type %)) upsert-results)
     :delete-results delete-results
     :accepted-deletes (filter #(= "accepted" (:status %)) delete-results)}))

(defn- find-orphan-candidates [vault-id manifest]
  (let [client-file-ids (set (map :fileId manifest))
        server-notes (list-active-notes vault-id)]
    (->> server-notes
         (filter #(not (contains? client-file-ids (:client-id %))))
         (map #(hash-map :fileId (:client-id %) :path (:path %))))))

(defn- validate-plan-request [body]
  (let [{:keys [mode]} body]
    (cond
      (nil? mode)
      {:valid? false :error "Missing required field: mode"}
      
      (and (= mode "incremental") (nil? (:baseRev body)))
      {:valid? false :error "Missing required field: baseRev for incremental mode"}
      
      (and (= mode "incremental") (empty? (:ops body)))
      {:valid? false :error "Missing required field: ops for incremental mode"}
      
      (and (= mode "full") (nil? (:manifest body)))
      {:valid? false :error "Missing required field: manifest for full mode"}
      
      :else
      {:valid? true})))

;; =============================================================================
;; Handlers
;; =============================================================================

(defn sync-plan
  "POST /v1/sync/plan
   
   Request body:
   - mode: 'incremental' | 'full'
   - baseRev: integer (for incremental)
   - ops: [{rev, op, fileId, path, hash, size, ifMatchHash?}] (for incremental)
   - manifest: [{fileId, hash}] (for full)
   
   Response:
   - syncToken: string
   - needUpload: [{fileId, hash}]
   - alreadyHave: [{fileId, hash}]
   - deleteResults: [{fileId, status, reason?}]
   - orphanCandidates: [{fileId, path}] (for full mode only)"
  [request]
  (if-let [sync-key (parse-sync-key request)]
    (if-let [vault (validate-sync-key sync-key)]
      (let [body (:body-params request)
            validation (validate-plan-request body)]
        (if-not (:valid? validation)
          (resp/bad-request (:error validation))
          (let [vault-id (:id vault)
                tenant-id (:tenant-id vault)
                server-rev (get-vault-last-applied-rev vault-id)
                {:keys [mode baseRev ops manifest]} body]
            (case mode
              "incremental"
              (if (not= baseRev server-rev)
                {:status 409
                 :body {:success false
                        :error "Cursor mismatch"
                        :serverLastAppliedRev server-rev}}
                (let [to-rev (apply max (map :rev ops))
                      results (process-incremental-ops vault-id ops)
                      need-upload (map #(select-keys % [:fileId :hash]) (:need-upload results))
                      already-have (map #(select-keys % [:fileId :hash]) (:already-have results))
                      accepted-deletes (map :fileId (:accepted-deletes results))
                      plan-id (create-sync-plan! vault-id baseRev to-rev 
                                                  (mapv :fileId need-upload) 
                                                  (vec accepted-deletes))]
                  (log/info "Sync plan created - vault:" vault-id "plan:" plan-id 
                            "needUpload:" (count need-upload) "deletes:" (count accepted-deletes))
                  (resp/ok {:syncToken plan-id
                            :serverState {:lastAppliedRev server-rev}
                            :needUpload need-upload
                            :alreadyHave already-have
                            :deleteResults (:delete-results results)})))
              
              "full"
              (let [orphans (find-orphan-candidates vault-id manifest)
                    manifest-hashes (into {} (map (juxt :fileId :hash) manifest))
                    need-upload (for [{:keys [fileId hash]} manifest
                                      :let [server-hash (get-note-hash vault-id fileId)]
                                      :when (not= server-hash hash)]
                                  {:fileId fileId :hash hash})
                    already-have (for [{:keys [fileId hash]} manifest
                                       :let [server-hash (get-note-hash vault-id fileId)]
                                       :when (= server-hash hash)]
                                   {:fileId fileId :hash hash})
                    plan-id (create-sync-plan! vault-id server-rev server-rev 
                                                (mapv :fileId need-upload) [])]
                (log/info "Full sync plan created - vault:" vault-id "plan:" plan-id
                          "needUpload:" (count need-upload) "orphans:" (count orphans))
                (resp/ok {:syncToken plan-id
                          :serverState {:lastAppliedRev server-rev}
                          :needUpload (vec need-upload)
                          :alreadyHave (vec already-have)
                          :orphanCandidates orphans}))
              
              (resp/bad-request (str "Unknown mode: " mode))))))
      (resp/unauthorized "Invalid sync-key"))
    (resp/unauthorized "Missing authorization header")))

(defn sync-commit
  "POST /v1/sync/commit
   
   Request body:
   - syncToken: string
   - files: [{fileId, hash, content, path?, metadata?}]
   - finalize: boolean
   
   Response:
   - status: 'ok' | 'partial'
   - lastAppliedRev: integer
   - fileResults: [{fileId, status}]"
  [request]
  (if-let [sync-key (parse-sync-key request)]
    (if-let [vault (validate-sync-key sync-key)]
      (let [vault-id (:id vault)
            tenant-id (:tenant-id vault)
            {:keys [syncToken files finalize]} (:body-params request)
            plan (get-sync-plan syncToken vault-id)]
        (cond
          (nil? plan)
          (resp/bad-request "Invalid syncToken")
          
          (plan-expired? plan)
          (resp/bad-request "Expired syncToken")
          
          (plan-already-applied? plan)
          (resp/ok {:status "ok"
                    :lastAppliedRev (:to-rev plan)
                    :fileResults []
                    :idempotent true})
          
          :else
          (let [need-upload-set (set (:need-upload plan))
                deletes (:deletes plan)
                uploaded-file-ids (set (map :fileId files))
                file-results (atom [])
                
                ;; Process file uploads
                _ (doseq [{:keys [fileId hash content path metadata]} files]
                    (when (contains? need-upload-set fileId)
                      (try
                        (let [note-path (or path (str fileId ".md"))]
                          (upsert-note-v2! tenant-id vault-id fileId note-path content hash metadata)
                          ;; Process links
                          (when content
                            (let [all-notes (db/get-notes-for-link-resolution vault-id)
                                  links (link-parser/extract-links content)
                                  resolved (link-parser/resolve-links links all-notes)
                                  valid-links (filter :target-client-id resolved)]
                              (db/delete-note-links-by-source! vault-id fileId)
                              (doseq [link valid-links]
                                (db/insert-note-link! vault-id fileId
                                                      (:target-client-id link)
                                                      (:target-path link)
                                                      (:link-type link)
                                                      (:display-text link)
                                                      (:original link)))))
                          (swap! file-results conj {:fileId fileId :status "stored"}))
                        (catch Exception e
                          (log/error "Failed to store file:" fileId (.getMessage e))
                          (swap! file-results conj {:fileId fileId :status "error" :error (.getMessage e)})))))
                
                ;; Check if all required uploads are done
                all-uploaded? (every? #(or (contains? uploaded-file-ids %)
                                          (not (contains? need-upload-set %)))
                                      need-upload-set)
                can-finalize? (and finalize all-uploaded?)]
            
            (if can-finalize?
              (do
                ;; Execute deletes
                (doseq [file-id deletes]
                  (soft-delete-note! vault-id file-id)
                  (swap! file-results conj {:fileId file-id :status "deleted"}))
                
                ;; Update revision
                (update-vault-last-applied-rev! vault-id (:to-rev plan))
                (mark-plan-applied! syncToken)
                
                (log/info "Sync commit finalized - vault:" vault-id "rev:" (:to-rev plan))
                (resp/ok {:status "ok"
                          :lastAppliedRev (:to-rev plan)
                          :fileResults @file-results}))
              
              (do
                (log/info "Sync commit partial - vault:" vault-id)
                (resp/ok {:status "partial"
                          :lastAppliedRev (get-vault-last-applied-rev vault-id)
                          :fileResults @file-results
                          :remainingUploads (vec (clojure.set/difference 
                                                   need-upload-set uploaded-file-ids))}))))))
      (resp/unauthorized "Invalid sync-key"))
    (resp/unauthorized "Missing authorization header")))

;; =============================================================================
;; Vault Info (kept for compatibility)
;; =============================================================================

(defn vault-info [request]
  (if-let [sync-key (parse-sync-key request)]
    (if-let [vault (validate-sync-key sync-key)]
      (resp/ok {:vault {:id (:id vault)
                        :name (:name vault)
                        :domain (:domain vault)
                        :lastAppliedRev (get-vault-last-applied-rev (:id vault))
                        :createdAt (:created-at vault)}})
      (resp/unauthorized "Invalid sync-key"))
    (resp/unauthorized "Missing authorization header")))
