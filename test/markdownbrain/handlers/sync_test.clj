(ns markdownbrain.handlers.sync-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [markdownbrain.db :as db]
            [markdownbrain.utils :as utils]
            [markdownbrain.handlers.sync :as sync]
            [next.jdbc :as jdbc]
            [ring.mock.request :as mock]))

;; 测试数据库（临时文件 SQLite）
(defn setup-test-db [f]
  (let [temp-file (java.io.File/createTempFile "test-db" ".db")
        _ (.deleteOnExit temp-file)
        test-ds (delay (jdbc/get-datasource {:dbtype "sqlite" :dbname (.getPath temp-file)}))]
    (with-redefs [db/datasource test-ds]
      (db/init-db!)
      (f)
      (.delete temp-file))))

(use-fixtures :each setup-test-db)

;; Helper function to create sync request with Bearer token
(defn sync-request-with-header
  [vault-id sync-token & {:keys [body]}]
  (-> (mock/request :post "/api/sync")
      (assoc :headers {"authorization" (str "Bearer " vault-id ":" sync-token)})
      (assoc :body-params body)))

;; Helper function to create sync request with body params
(defn sync-request-with-body
  [vault-id sync-token & {:keys [body]}]
  (-> (mock/request :post "/api/sync")
      (assoc :body-params (merge body
                                {:vault-id vault-id
                                 :sync-token sync-token}))))

;; Sync File - Create/Modify 测试
(deftest test-sync-file-create
  (testing "Sync new file with valid token (Bearer header)"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          sync-token (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "sync-list.com" sync-token "dns")
          request (sync-request-with-header vault-id sync-token
                                           :body {:path "test.md"
                                                 :content "# Test Content"
                                                 :metadata {:tags ["test"]}
                                                 :hash "abc123"
                                                 :mtime "2025-12-21T10:00:00Z"})
          response (sync/sync-file request)]
      (is (= 200 (:status response)))
      (is (get-in response [:body :success]))
      ;; Verify document was created
      (let [doc (db/get-document-by-path vault-id "test.md")]
        (is (= "# Test Content" (:content doc)))
        (is (= "abc123" (:hash doc))))))

  (testing "Sync file with valid token (body params)"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          sync-token (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "sync-create.com" sync-token "dns")
          request (sync-request-with-body vault-id sync-token
                                         :body {:path "doc.md"
                                               :content "# Document"
                                               :metadata {}
                                               :hash "def456"
                                               :mtime "2025-12-21T11:00:00Z"})
          response (sync/sync-file request)]
      (is (= 200 (:status response)))
      (is (get-in response [:body :success]))
      ;; Verify document was created
      (let [doc (db/get-document-by-path vault-id "doc.md")]
        (is (= "# Document" (:content doc))))))

  (testing "Update existing file"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          sync-token (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "sync-update.com" sync-token "dns")
          doc-id (utils/generate-uuid)
          _ (db/upsert-document! doc-id tenant-id vault-id "existing.md" "# Old Content" "{}" "old-hash" "2025-12-21T09:00:00Z")
          request (sync-request-with-header vault-id sync-token
                                           :body {:path "existing.md"
                                                 :content "# Updated Content"
                                                 :metadata {}
                                                 :hash "new-hash"
                                                 :mtime "2025-12-21T12:00:00Z"})
          response (sync/sync-file request)]
      (is (= 200 (:status response)))
      (is (get-in response [:body :success]))
      ;; Verify document was updated
      (let [doc (db/get-document-by-path vault-id "existing.md")]
        (is (= "# Updated Content" (:content doc)))
        (is (= "new-hash" (:hash doc))))))

  (testing "Sync with invalid vault_id"
    (let [vault-id "invalid-vault-id"
          sync-token "invalid-token"
          request (sync-request-with-header vault-id sync-token
                                           :body {:path "test.md"
                                                 :content "# Content"
                                                 :metadata {}
                                                 :hash "hash"
                                                 :mtime "2025-12-21T10:00:00Z"})
          response (sync/sync-file request)]
      (is (= 401 (:status response)))
      (is (false? (get-in response [:body :success])))
      (is (= "Invalid vault_id or sync_token" (get-in response [:body :error])))))

  (testing "Sync with wrong sync_token"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          correct-token (utils/generate-uuid)
          wrong-token (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "sync-invalid.com" correct-token "dns")
          request (sync-request-with-header vault-id wrong-token
                                           :body {:path "test.md"
                                                 :content "# Content"
                                                 :metadata {}
                                                 :hash "hash"
                                                 :mtime "2025-12-21T10:00:00Z"})
          response (sync/sync-file request)]
      (is (= 401 (:status response)))
      (is (false? (get-in response [:body :success]))))))

;; Sync File - Delete 测试
(deftest test-sync-file-delete
  (testing "Delete file with valid token"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          sync-token (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "sync-delete.com" sync-token "dns")
          doc-id (utils/generate-uuid)
          _ (db/upsert-document! doc-id tenant-id vault-id "to-delete.md" "# Delete Me" "{}" "hash" "2025-12-21T10:00:00Z")
          request (sync-request-with-header vault-id sync-token
                                           :body {:path "to-delete.md"
                                                 :action "delete"})
          response (sync/sync-file request)]
      (is (= 200 (:status response)))
      (is (get-in response [:body :success]))
      ;; Verify document was deleted
      (is (nil? (db/get-document-by-path vault-id "to-delete.md")))))

  (testing "Delete non-existent file"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          sync-token (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "sync-missing.com" sync-token "dns")
          request (sync-request-with-header vault-id sync-token
                                           :body {:path "non-existent.md"
                                                 :action "delete"})
          response (sync/sync-file request)]
      (is (= 200 (:status response)))
      (is (get-in response [:body :success]))))

  (testing "Delete with invalid token"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          sync-token (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "sync-duplicate.com" sync-token "dns")
          doc-id (utils/generate-uuid)
          _ (db/upsert-document! doc-id tenant-id vault-id "file.md" "# Content" "{}" "hash" "2025-12-21T10:00:00Z")
          request (sync-request-with-header vault-id "wrong-token"
                                           :body {:path "file.md"
                                                 :action "delete"})
          response (sync/sync-file request)]
      (is (= 401 (:status response)))
      (is (false? (get-in response [:body :success])))
      ;; Verify document was NOT deleted
      (is (some? (db/get-document-by-path vault-id "file.md"))))))
