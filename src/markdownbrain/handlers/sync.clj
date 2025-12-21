(ns markdownbrain.handlers.sync
  (:require [markdownbrain.db :as db]
            [markdownbrain.utils :as utils]
            [markdownbrain.response :as resp]
            [clojure.data.json :as json]))

;; 验证同步 token
(defn validate-sync-token [vault-id sync-token]
  (when-let [vault (db/get-vault-by-sync-token sync-token)]
    (when (= (:id vault) vault-id)
      vault)))

;; 同步文件
(defn sync-file [request]
  (let [auth-header (get-in request [:headers "authorization"])
        auth (utils/parse-auth-header auth-header)
        {:keys [vault-id sync-token]} (or auth (:body-params request))
        {:keys [path content metadata hash mtime action]} (:body-params request)]

    (if-let [vault (validate-sync-token vault-id sync-token)]
      (do
        (case action
          "delete"
          (db/delete-document! vault-id path)

          ;; "create" 和 "modify" 都使用 upsert
          (let [doc-id (utils/generate-uuid)
                tenant-id (:tenant-id vault)
                metadata-str (when metadata (json/write-str metadata))]
            (db/upsert-document! doc-id tenant-id vault-id path content metadata-str hash mtime)))

        (resp/success {:vault-id vault-id
                       :path path
                       :action action}))

      (resp/unauthorized "Invalid vault_id or sync_token"))))
