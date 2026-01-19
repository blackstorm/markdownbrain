(ns markdownbrain.handlers.sync
  "Sync protocol (snapshot + incremental uploads).

   Endpoints:
   - POST /sync/changes
   - POST /sync/notes/{id}
   - POST /sync/assets/{id}
   - DELETE /sync/notes/{note_id}/assets/{asset_id}"
  (:require [markdownbrain.db :as db]
            [markdownbrain.object-store :as object-store]
            [markdownbrain.response :as resp]
            [markdownbrain.link-parser :as link-parser]
            [markdownbrain.utils :as utils]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [clojure.set :as set])
  (:import (java.util Base64)))

;; =============================================================================
;; Auth Helpers
;; =============================================================================

(defn- parse-sync-key [request]
  (when-let [auth-header (get-in request [:headers "authorization"])]
    (when-let [[_ key] (re-matches #"Bearer\s+(.+)" auth-header)]
      key)))

(defn- validate-sync-key [sync-key]
  (db/get-vault-by-sync-key sync-key))

(defn- require-auth [request]
  (if-let [sync-key (parse-sync-key request)]
    (if-let [vault (validate-sync-key sync-key)]
      {:ok true :vault vault}
      {:ok false :response (resp/unauthorized "Invalid sync-key")})
    {:ok false :response (resp/unauthorized "Missing authorization header")}))

;; =============================================================================
;; Helpers
;; =============================================================================

(defn- normalize-hash-entry [entry]
  {:id (:id entry)
   :hash (:hash entry)})

(defn- decode-base64
  [encoded]
  (when encoded
    (.decode (Base64/getDecoder) ^String encoded)))

(defn- ensure-string
  [value]
  (when (some? value)
    (str value)))

(defn- upsert-note-with-links!
  [tenant-id vault-id note-id path content hash metadata]
  (let [metadata-str (when metadata (json/write-str metadata))]
    (db/upsert-note! (utils/generate-uuid) tenant-id vault-id path note-id content metadata-str hash nil)
    (when content
      (let [all-notes (db/get-notes-for-link-resolution vault-id)
            links (link-parser/extract-links content)
            resolved (link-parser/resolve-links links all-notes)
            valid-links (filter :target-client-id resolved)
            deduped (link-parser/deduplicate-by-target valid-links)]
        (db/delete-note-links-by-source! vault-id note-id)
        (doseq [link deduped]
          (db/insert-note-link! vault-id note-id
                                (:target-client-id link)
                                (:target-path link)
                                (:link-type link)
                                (:display-text link)
                                (:original link)))))))

(declare delete-asset!)

(defn- update-note-asset-refs!
  [vault-id note-id asset-ids]
  (let [existing-refs (db/get-asset-refs-by-note vault-id note-id)
        existing-ids (set (map :asset-client-id existing-refs))
        new-ids (set (distinct asset-ids))
        removed-ids (set/difference existing-ids new-ids)]
    (db/update-note-asset-refs! vault-id note-id (vec new-ids))
    (doseq [asset-id removed-ids]
      (when (zero? (db/count-asset-refs vault-id asset-id))
        (delete-asset! vault-id asset-id)))))

(defn- delete-note!
  [vault-id note-id]
  (db/delete-note-links-by-source! vault-id note-id)
  (db/delete-note-asset-refs-by-note! vault-id note-id)
  (db/delete-note-by-client-id! vault-id note-id)
  (db/delete-orphan-links! vault-id))

(defn- delete-asset!
  [vault-id asset-id]
  (when-let [asset (db/get-asset-by-client-id vault-id asset-id)]
    (object-store/delete-object! vault-id (:object-key asset))
    (db/delete-note-asset-refs-by-asset! vault-id asset-id)
    (db/delete-asset-by-client-id! vault-id asset-id)))

;; =============================================================================
;; Handlers
;; =============================================================================

(defn sync-changes
  "POST /sync/changes

   Request body:
   {
     notes: [{id: \"...\", hash: \"...\"}],
     assets: [{id: \"...\", hash: \"...\"}]
   }

   Response body:
   {
     need_upsert: {notes: [...], assets: [...]},
     deleted_on_server: {notes: [...], assets: [...]}
   }"
  [request]
  (let [{:keys [ok vault response]} (require-auth request)]
    (if-not ok
      response
      (let [vault-id (:id vault)
            {:keys [notes assets]} (:body-params request)
            client-notes (map normalize-hash-entry (or notes []))
            client-assets (map normalize-hash-entry (or assets []))
            client-note-map (into {} (map (juxt :id :hash) client-notes))
            client-asset-map (into {} (map (juxt :id :hash) client-assets))
            server-notes (db/list-notes-by-vault vault-id)
            server-assets (db/list-assets-by-vault vault-id)
            server-note-map (into {} (map (juxt :client-id :hash) server-notes))
            server-asset-map (into {} (map (juxt :client-id :md5) server-assets))
            notes-to-delete (->> server-note-map
                                 (remove (fn [[id _]] (contains? client-note-map id)))
                                 (map (fn [[id hash]] {:id id :hash hash})))
            assets-to-delete (->> server-asset-map
                                  (remove (fn [[id _]] (contains? client-asset-map id)))
                                  (map (fn [[id hash]] {:id id :hash hash})))
            notes-to-upsert (->> client-note-map
                                 (filter (fn [[id hash]]
                                           (not= hash (get server-note-map id))))
                                 (map (fn [[id hash]] {:id id :hash hash})))
            assets-to-upsert (->> client-asset-map
                                  (filter (fn [[id hash]]
                                            (not= hash (get server-asset-map id))))
                                  (map (fn [[id hash]] {:id id :hash hash})))]
        (doseq [{:keys [id]} notes-to-delete]
          (delete-note! vault-id id))
        (doseq [{:keys [id]} assets-to-delete]
          (delete-asset! vault-id id))
        (resp/ok {:need_upsert {:notes (vec notes-to-upsert)
                                :assets (vec assets-to-upsert)}
                  :deleted_on_server {:notes (vec notes-to-delete)
                                      :assets (vec assets-to-delete)}})))))

(defn sync-note
  "POST /sync/notes/{id}"
  [request]
  (let [{:keys [ok vault response]} (require-auth request)]
    (if-not ok
      response
      (let [vault-id (:id vault)
            tenant-id (:tenant-id vault)
            note-id (get-in request [:path-params :id])
            {:keys [path content hash metadata assetIds]} (:body-params request)
            note-path (ensure-string path)
            note-hash (ensure-string hash)]
        (cond
          (str/blank? note-id)
          (resp/bad-request "Missing note id")

          (str/blank? note-path)
          (resp/bad-request "Missing note path")

          (str/blank? note-hash)
          (resp/bad-request "Missing note hash")

          (nil? content)
          (resp/bad-request "Missing note content")

          :else
          (let [existing (db/get-note-by-client-id vault-id note-id)
                existing-hash (:hash existing)
                existing-path (:path existing)]
            (if (and existing (= existing-hash note-hash) (= existing-path note-path))
              (resp/ok {:status "skipped" :noteId note-id})
              (do
                (upsert-note-with-links! tenant-id vault-id note-id note-path content note-hash metadata)
                (when (vector? assetIds)
                  (update-note-asset-refs! vault-id note-id assetIds))
                (resp/ok {:status "stored" :noteId note-id})))))))))

(defn sync-asset
  "POST /sync/assets/{id}"
  [request]
  (let [{:keys [ok vault response]} (require-auth request)]
    (if-not ok
      response
      (let [vault-id (:id vault)
            tenant-id (:tenant-id vault)
            asset-id (get-in request [:path-params :id])
            {:keys [path contentType size hash content]} (:body-params request)
            asset-path (ensure-string path)
            asset-hash (ensure-string hash)
            asset-content-type (ensure-string contentType)
            content-bytes (decode-base64 content)]
        (cond
          (str/blank? asset-id)
          (resp/bad-request "Missing asset id")

          (str/blank? asset-path)
          (resp/bad-request "Missing asset path")

          (str/blank? asset-hash)
          (resp/bad-request "Missing asset hash")

          (str/blank? asset-content-type)
          (resp/bad-request "Missing asset contentType")

          (nil? content-bytes)
          (resp/bad-request "Missing asset content")

          :else
          (let [extension (object-store/content-type->extension asset-content-type)
                object-key (object-store/asset-object-key asset-id extension)
                asset-size (or size (alength ^bytes content-bytes))]
            (object-store/put-object! vault-id object-key content-bytes asset-content-type)
            (db/upsert-asset! (utils/generate-uuid)
                              tenant-id vault-id asset-id asset-path object-key
                              asset-size asset-content-type asset-hash)
            (resp/ok {:status "stored" :assetId asset-id})))))))

(defn delete-note-asset
  "DELETE /sync/notes/{note_id}/assets/{asset_id}"
  [request]
  (let [{:keys [ok vault response]} (require-auth request)]
    (if-not ok
      response
      (let [vault-id (:id vault)
            note-id (get-in request [:path-params :note_id])
            asset-id (get-in request [:path-params :asset_id])]
        (cond
          (str/blank? note-id)
          (resp/bad-request "Missing note id")

          (str/blank? asset-id)
          (resp/bad-request "Missing asset id")

          :else
          (do
            (db/delete-note-asset-ref! vault-id note-id asset-id)
            (when (zero? (db/count-asset-refs vault-id asset-id))
              (delete-asset! vault-id asset-id))
            (resp/ok {:status "deleted" :noteId note-id :assetId asset-id})))))))

(defn vault-info [request]
  (let [{:keys [ok vault response]} (require-auth request)]
    (if-not ok
      response
      (resp/ok {:vault {:id (:id vault)
                        :name (:name vault)
                        :domain (:domain vault)
                        :createdAt (:created-at vault)}}))))
