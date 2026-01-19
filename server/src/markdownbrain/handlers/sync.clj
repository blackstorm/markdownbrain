(ns markdownbrain.handlers.sync
  "Sync protocol (snapshot + incremental uploads).

   Endpoints:
   - POST /sync/changes
   - POST /sync/notes/{id}
   - POST /sync/assets/{id}"
  (:require [markdownbrain.config :as config]
            [markdownbrain.db :as db]
            [markdownbrain.object-store :as object-store]
            [markdownbrain.response :as resp]
            [markdownbrain.link-parser :as link-parser]
            [markdownbrain.utils :as utils]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [clojure.set :as set]
            [clojure.tools.logging :as log])
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
            deduped (link-parser/deduplicate-by-target valid-links)
            existing-links (db/get-note-links vault-id note-id)
            normalize-link (fn [link]
                             (select-keys link [:target-client-id :target-path :link-type :display-text :original]))
            existing-map (into {}
                               (map (fn [link]
                                      [(:target-client-id link) (normalize-link link)]))
                               existing-links)
            new-map (into {}
                          (map (fn [link]
                                 [(:target-client-id link) (normalize-link link)]))
                          deduped)
            existing-keys (set (keys existing-map))
            new-keys (set (keys new-map))
            to-delete (set/difference existing-keys new-keys)
            to-add (set/difference new-keys existing-keys)
            to-keep (set/intersection existing-keys new-keys)
            to-update (filter (fn [key]
                                (not= (existing-map key) (new-map key)))
                              to-keep)]
        (when (config/development?)
          (log/debug "sync-note link diff"
                     {:note-id note-id
                      :existing (count existing-keys)
                      :new (count new-keys)
                      :delete (count to-delete)
                      :update (count to-update)
                      :insert (count to-add)}))
        (doseq [target-id (concat to-delete to-update)]
          (db/delete-note-link-by-target! vault-id note-id target-id))
        (doseq [target-id (concat to-add to-update)]
          (let [link (new-map target-id)]
            (db/insert-note-link! vault-id note-id
                                  (:target-client-id link)
                                  (:target-path link)
                                  (:link-type link)
                                  (:display-text link)
                                  (:original link))))))))

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
    (when (config/development?)
      (log/debug "delete-asset"
                 {:vault-id vault-id
                  :asset-id asset-id
                  :object-key (:object-key asset)}))
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
  "POST /sync/notes/{id}

   Request body:
   {
     path: \"...\",
     content: \"...\",
     hash: \"...\",
     metadata: {...},
     assets: [{id: \"...\", hash: \"...\"}],
     linked_notes: [{id: \"...\", hash: \"...\"}]
   }

   Behavior:
   - Upserts the note + parsed note links
   - Syncs note_asset_refs (and removes orphan assets)
   - Returns missing assets/linked notes based on server state

   Response body:
   {
     status: \"stored\",
     noteId: \"...\",
     need_upload_assets: [...],
     need_upload_notes: [...]
   }"
  [request]
  (let [{:keys [ok vault response]} (require-auth request)]
    (if-not ok
      response
      (let [vault-id (:id vault)
            tenant-id (:tenant-id vault)
            note-id (get-in request [:path-params :id])
            {:keys [path content hash metadata assets linked_notes]} (:body-params request)
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

          (not (vector? assets))
          (resp/bad-request "Missing assets")

          (not (vector? linked_notes))
          (resp/bad-request "Missing linked notes")

          :else
          (let [existing (db/get-note-by-client-id vault-id note-id)
                existing-hash (:hash existing)
                existing-path (:path existing)]
            (if (and existing (= existing-hash note-hash) (= existing-path note-path))
              (resp/ok {:status "skipped" :noteId note-id})
              (do
                ;; Sync note content + links, then update asset refs and compute missing uploads.
                (upsert-note-with-links! tenant-id vault-id note-id note-path content note-hash metadata)
                (let [asset-entries assets
                      asset-ids (mapv :id asset-entries)
                      linked-entries (mapv normalize-hash-entry linked_notes)]
                  (update-note-asset-refs! vault-id note-id asset-ids)
                  (let [need-upload (->> asset-entries
                                         (filter (fn [{:keys [id hash]}]
                                                   (let [existing (db/get-asset-by-client-id vault-id id)]
                                                     (or (nil? existing)
                                                         (not= (:md5 existing) hash)))))
                                         (mapv (fn [{:keys [id hash]}] {:id id :hash hash})))
                        need-upload-notes (->> linked-entries
                                               (filter (fn [{:keys [id hash]}]
                                                         (let [existing (db/get-note-by-client-id vault-id id)]
                                                           (or (nil? existing)
                                                               (not= (:hash existing) hash)))))
                                               (mapv (fn [{:keys [id hash]}] {:id id :hash hash})))]
                    (when (config/development?)
                      (log/debug "sync-note assets"
                                 {:note-id note-id
                                  :referenced (count asset-entries)
                                  :need-upload (count need-upload)}))
                    (when (config/development?)
                      (log/debug "sync-note linked-notes"
                                 {:note-id note-id
                                  :referenced (count linked-entries)
                                  :need-upload (count need-upload-notes)}))
                    (resp/ok {:status "stored"
                              :noteId note-id
                              :need_upload_assets need-upload
                              :need_upload_notes need-upload-notes})))))))))))

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
            asset-content-type (ensure-string contentType)]
        (cond
          (str/blank? asset-id)
          (resp/bad-request "Missing asset id")

          (str/blank? asset-path)
          (resp/bad-request "Missing asset path")

          (str/blank? asset-hash)
          (resp/bad-request "Missing asset hash")

          (str/blank? asset-content-type)
          (resp/bad-request "Missing asset contentType")

          :else
          (let [existing (db/get-asset-by-client-id vault-id asset-id)
                existing-hash (:md5 existing)
                existing-path (:path existing)]
            (if (and existing
                     (= existing-hash asset-hash)
                     (= existing-path asset-path))
              (do
                (when (config/development?)
                  (log/debug "sync-asset skipped"
                             {:vault-id vault-id
                              :asset-id asset-id
                              :path asset-path
                              :hash asset-hash}))
                (resp/ok {:status "skipped" :assetId asset-id}))
              (let [content-bytes (decode-base64 content)]
                (cond
                  (nil? content-bytes)
                  (resp/bad-request "Missing asset content")

                  :else
                  (let [extension (object-store/content-type->extension asset-content-type)
                        object-key (object-store/asset-object-key asset-id extension)
                        asset-size (or size (alength ^bytes content-bytes))]
                    (when (config/development?)
                      (log/debug "sync-asset stored"
                                 {:vault-id vault-id
                                  :asset-id asset-id
                                  :path asset-path
                                  :hash asset-hash
                                  :object-key object-key
                                  :size asset-size}))
                    (object-store/put-object! vault-id object-key content-bytes asset-content-type)
                    (db/upsert-asset! (utils/generate-uuid)
                                      tenant-id vault-id asset-id asset-path object-key
                                      asset-size asset-content-type asset-hash)
                    (resp/ok {:status "stored" :assetId asset-id})))))))))))

(defn vault-info [request]
  (let [{:keys [ok vault response]} (require-auth request)]
    (if-not ok
      response
      (resp/ok {:vault {:id (:id vault)
                        :name (:name vault)
                        :domain (:domain vault)
                        :createdAt (:created-at vault)}}))))
