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
  [sync-key & {:keys [body]}]
  (-> (mock/request :post "/api/sync")
      (assoc :headers {"authorization" (str "Bearer " sync-key)})
      (assoc :body-params body)))

;; Sync File - Create/Modify 测试
(deftest test-sync-file-create
  (testing "Sync new file with valid token (Bearer header)"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          sync-key (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "sync-list.com" sync-key)
          request (sync-request-with-header sync-key
                                           :body {:path "test.md"
                                                 :content "# Test Content"
                                                 :metadata {:tags ["test"]}
                                                 :hash "abc123"
                                                 :mtime "2025-12-21T10:00:00Z"
                                                 :action "create"})
          response (sync/sync-file request)]
      (is (= 200 (:status response)))
      (is (get-in response [:body :success]))
      ;; Verify document was created
      (let [doc (db/get-document-by-path vault-id "test.md")]
        (is (= "# Test Content" (:content doc)))
        (is (= "abc123" (:hash doc))))))

  (testing "Update existing file"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          sync-key (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "sync-update.com" sync-key)
          doc-id (utils/generate-uuid)
          _ (db/upsert-document! doc-id tenant-id vault-id "existing.md" "# Old Content" "{}" "old-hash" "2025-12-21T09:00:00Z")
          request (sync-request-with-header sync-key
                                           :body {:path "existing.md"
                                                 :content "# Updated Content"
                                                 :metadata {}
                                                 :hash "new-hash"
                                                 :mtime "2025-12-21T12:00:00Z"
                                                 :action "modify"})
          response (sync/sync-file request)]
      (is (= 200 (:status response)))
      (is (get-in response [:body :success]))
      ;; Verify document was updated
      (let [doc (db/get-document-by-path vault-id "existing.md")]
        (is (= "# Updated Content" (:content doc)))
        (is (= "new-hash" (:hash doc))))))

  (testing "Sync with invalid sync-key"
    (let [sync-key "invalid-sync-key"
          request (sync-request-with-header sync-key
                                           :body {:path "test.md"
                                                 :content "# Content"
                                                 :metadata {}
                                                 :hash "hash"
                                                 :mtime "2025-12-21T10:00:00Z"
                                                 :action "create"})
          response (sync/sync-file request)]
      (is (= 401 (:status response)))
      (is (false? (get-in response [:body :success])))
      (is (= "Invalid sync-key" (get-in response [:body :error])))))

  (testing "Sync with wrong sync-key"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          correct-key (utils/generate-uuid)
          wrong-key (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "sync-invalid.com" correct-key)
          request (sync-request-with-header wrong-key
                                           :body {:path "test.md"
                                                 :content "# Content"
                                                 :metadata {}
                                                 :hash "hash"
                                                 :mtime "2025-12-21T10:00:00Z"
                                                 :action "create"})
          response (sync/sync-file request)]
      (is (= 401 (:status response)))
      (is (false? (get-in response [:body :success]))))))

;; Sync File - Delete 测试
(deftest test-sync-file-delete
  (testing "Delete file with valid token"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          sync-key (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "sync-delete.com" sync-key)
          doc-id (utils/generate-uuid)
          _ (db/upsert-document! doc-id tenant-id vault-id "to-delete.md" "# Delete Me" "{}" "hash" "2025-12-21T10:00:00Z")
          request (sync-request-with-header sync-key
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
          sync-key (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "sync-missing.com" sync-key)
          request (sync-request-with-header sync-key
                                           :body {:path "non-existent.md"
                                                 :action "delete"})
          response (sync/sync-file request)]
      (is (= 200 (:status response)))
      (is (get-in response [:body :success]))))

  (testing "Delete with invalid token"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          sync-key (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "sync-duplicate.com" sync-key)
          doc-id (utils/generate-uuid)
          _ (db/upsert-document! doc-id tenant-id vault-id "file.md" "# Content" "{}" "hash" "2025-12-21T10:00:00Z")
          request (sync-request-with-header "wrong-key"
                                           :body {:path "file.md"
                                                 :action "delete"})
          response (sync/sync-file request)]
      (is (= 401 (:status response)))
      (is (false? (get-in response [:body :success])))
      ;; Verify document was NOT deleted
      (is (some? (db/get-document-by-path vault-id "file.md"))))))

;; ============================================================
;; Sync Full - 孤儿文档清理测试
;; ============================================================

(deftest test-sync-full-cleanup-orphans
  (testing "Full sync 删除孤儿文档"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          sync-key (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "full-sync-test.com" sync-key)
          ;; Given: 服务器有 3 个文档
          _ (db/upsert-document! (utils/generate-uuid) tenant-id vault-id "a.md" "c1" "c" "{}" "h1" "2024-01-01T00:00:00Z")
          _ (db/upsert-document! (utils/generate-uuid) tenant-id vault-id "b.md" "c2" "c" "{}" "h2" "2024-01-01T00:00:00Z")
          _ (db/upsert-document! (utils/generate-uuid) tenant-id vault-id "c.md" "c3" "c" "{}" "h3" "2024-01-01T00:00:00Z")
          ;; 客户端只有 c1 和 c2
          request (sync-request-with-header sync-key
                                           :body {:clientIds ["c1" "c2"]})
          response (sync/sync-full request)
          body (:body response)]
      ;; Then: 删除 1 个孤儿 (c3)
      (is (= 200 (:status response)))
      (is (true? (get-in body [:success])))
      (is (= 1 (get-in body [:deleted-count])))
      (is (= 2 (get-in body [:remaining-docs])))
      ;; 验证文档状态
      (is (some? (db/get-document-by-client-id vault-id "c1")))
      (is (some? (db/get-document-by-client-id vault-id "c2")))
      (is (nil? (db/get-document-by-client-id vault-id "c3"))))))

(deftest test-sync-full-empty-list
  (testing "Full sync 空列表删除所有文档"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          sync-key (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "empty-full-sync.com" sync-key)
          _ (db/upsert-document! (utils/generate-uuid) tenant-id vault-id "a.md" "c1" "c" "{}" "h" "2024-01-01T00:00:00Z")
          _ (db/upsert-document! (utils/generate-uuid) tenant-id vault-id "b.md" "c2" "c" "{}" "h" "2024-01-01T00:00:00Z")
          request (sync-request-with-header sync-key
                                           :body {:clientIds []})
          response (sync/sync-full request)]
      (is (= 400 (:status response)))
      (is (false? (get-in response [:body :success]))))))

(deftest test-sync-full-no-orphans
  (testing "Full sync 无孤儿文档 (服务器和客户端一致)"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          sync-key (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "no-orphans.com" sync-key)
          _ (db/upsert-document! (utils/generate-uuid) tenant-id vault-id "a.md" "c1" "c" "{}" "h1" "2024-01-01T00:00:00Z")
          _ (db/upsert-document! (utils/generate-uuid) tenant-id vault-id "b.md" "c2" "c" "{}" "h2" "2024-01-01T00:00:00Z")
          request (sync-request-with-header sync-key
                                           :body {:clientIds ["c1" "c2"]})
          response (sync/sync-full request)
          body (:body response)]
      (is (= 200 (:status response)))
      (is (= 0 (get-in body [:deleted-count])))
      (is (= 2 (get-in body [:remaining-docs]))))))

(deftest test-sync-full-unauthorized
  (testing "Full sync 无效 token"
    (let [request (sync-request-with-header "invalid-token"
                                            :body {:clientIds ["c1"]})
          response (sync/sync-full request)]
      (is (= 401 (:status response)))
      (is (false? (get-in response [:body :success]))))))

(deftest test-sync-full-missing-auth
  (testing "Full sync 缺少 authorization header"
    (let [request (-> (mock/request :post "/obsidian/sync/full")
                      (assoc :body-params {:clientIds ["c1"]}))
          response (sync/sync-full request)]
      (is (= 401 (:status response)))
      (is (false? (get-in response [:body :success]))))))
