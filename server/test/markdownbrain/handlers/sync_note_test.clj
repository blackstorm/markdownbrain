(ns markdownbrain.handlers.sync-note-test
  "Note sync tests: create, modify, delete, rename"
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [markdownbrain.db :as db]
            [markdownbrain.utils :as utils]
            [markdownbrain.handlers.sync :as sync]
            [markdownbrain.handlers.sync-test-utils :as test-utils]))

(use-fixtures :each test-utils/setup-test-db)

;; ============================================================
;; Sync File - Create/Modify 测试
;; ============================================================

(deftest test-sync-file-create
  (testing "Sync new file with valid token (Bearer header)"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          sync-key (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "sync-list.com" sync-key)
          request (test-utils/sync-request-with-header sync-key
                                           :body {:path "test.md"
                                                 :clientId "test-client-1"
                                                 :content "# Test Content"
                                                 :metadata {:tags ["test"]}
                                                 :hash "abc123"
                                                 :mtime "2025-12-21T10:00:00Z"
                                                 :action "create"})
          response (sync/sync-file request)]
      (is (= 200 (:status response)))
      (is (get-in response [:body :success]))
      ;; Verify document was created
      (let [doc (db/get-note-by-path vault-id "test.md")]
        (is (= "# Test Content" (:content doc)))
        (is (= "abc123" (:hash doc))))))

  (testing "Update existing file"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          sync-key (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "sync-update.com" sync-key)
          doc-id (utils/generate-uuid)
          _ (db/upsert-note! doc-id tenant-id vault-id "existing.md" "existing-client-id" "# Old Content" "{}" "old-hash" "2025-12-21T09:00:00Z")
          request (test-utils/sync-request-with-header sync-key
                                           :body {:path "existing.md"
                                                 :clientId "existing-client-id"
                                                 :content "# Updated Content"
                                                 :metadata {}
                                                 :hash "new-hash"
                                                 :mtime "2025-12-21T12:00:00Z"
                                                 :action "modify"})
          response (sync/sync-file request)]
      (is (= 200 (:status response)))
      (is (get-in response [:body :success]))
      ;; Verify document was updated
      (let [doc (db/get-note-by-path vault-id "existing.md")]
        (is (= "# Updated Content" (:content doc)))
        (is (= "new-hash" (:hash doc))))))

  (testing "Sync with invalid sync-key"
    (let [sync-key "invalid-sync-key"
          request (test-utils/sync-request-with-header sync-key
                                           :body {:path "test.md"
                                                 :clientId "test-client-invalid"
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
          request (test-utils/sync-request-with-header wrong-key
                                           :body {:path "test.md"
                                                 :clientId "test-client-wrong-key"
                                                 :content "# Content"
                                                 :metadata {}
                                                 :hash "hash"
                                                 :mtime "2025-12-21T10:00:00Z"
                                                 :action "create"})
          response (sync/sync-file request)]
      (is (= 401 (:status response)))
      (is (false? (get-in response [:body :success]))))))

;; ============================================================
;; Sync File - Delete 测试
;; ============================================================

(deftest test-sync-file-delete
  (testing "Delete file with valid token"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          sync-key (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "sync-delete.com" sync-key)
          doc-id (utils/generate-uuid)
          _ (db/upsert-note! doc-id tenant-id vault-id "to-delete.md" "delete-client-id" "# Delete Me" "{}" "hash" "2025-12-21T10:00:00Z")
          request (test-utils/sync-request-with-header sync-key
                                           :body {:path "to-delete.md"
                                                 :clientId "delete-client-id"
                                                 :action "delete"})
          response (sync/sync-file request)]
      (is (= 200 (:status response)))
      (is (get-in response [:body :success]))
      ;; Verify document was deleted
      (is (nil? (db/get-note-by-path vault-id "to-delete.md")))))

  (testing "Delete non-existent file"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          sync-key (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "sync-missing.com" sync-key)
          request (test-utils/sync-request-with-header sync-key
                                           :body {:path "non-existent.md"
                                                 :clientId "non-existent-client-id"
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
          _ (db/upsert-note! doc-id tenant-id vault-id "file.md" "delete-token-client-id" "# Content" "{}" "hash" "2025-12-21T10:00:00Z")
          request (test-utils/sync-request-with-header "wrong-key"
                                           :body {:path "file.md"
                                                 :clientId "delete-token-client-id"
                                                 :action "delete"})
          response (sync/sync-file request)]
      (is (= 401 (:status response)))
      (is (false? (get-in response [:body :success])))
      ;; Verify document was NOT deleted
      (is (some? (db/get-note-by-path vault-id "file.md"))))))

;; ============================================================
;; Sync File - Rename 测试
;; ============================================================

(deftest test-sync-file-rename
  (testing "File rename - 同 hash 不同 path 时更新路径"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          sync-key (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "rename-test.com" sync-key)
          doc-id (utils/generate-uuid)
          ;; Given: 文档路径为 old-name.md
          _ (db/upsert-note! doc-id tenant-id vault-id "old-name.md" "rename-client-id"
                                 "# Content" "{}" "same-hash" "2025-12-21T10:00:00Z")
          ;; When: 发送同样 hash 但不同 path 的请求
          request (test-utils/sync-request-with-header sync-key
                                           :body {:path "new-name.md"
                                                 :clientId "rename-client-id"
                                                 :content "# Content"
                                                 :metadata {}
                                                 :hash "same-hash"
                                                 :mtime "2025-12-21T12:00:00Z"
                                                 :action "modify"})
          response (sync/sync-file request)]
      ;; Then: 返回成功且标记为 renamed
      (is (= 200 (:status response)))
      (is (get-in response [:body :success]))
      (is (true? (get-in response [:body :renamed])))
      (is (= "old-name.md" (get-in response [:body :old-path])))
      ;; 验证路径已更新
      (let [doc (db/get-note-by-client-id vault-id "rename-client-id")]
        (is (= "new-name.md" (:path doc)))
        ;; 内容和 hash 应保持不变
        (is (= "# Content" (:content doc)))
        (is (= "same-hash" (:hash doc))))))

  (testing "File rename - 旧路径不再存在"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          sync-key (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "rename-old-path.com" sync-key)
          doc-id (utils/generate-uuid)
          _ (db/upsert-note! doc-id tenant-id vault-id "before.md" "path-client-id"
                                 "# Test" "{}" "hash123" "2025-12-21T10:00:00Z")
          request (test-utils/sync-request-with-header sync-key
                                           :body {:path "after.md"
                                                 :clientId "path-client-id"
                                                 :content "# Test"
                                                 :metadata {}
                                                 :hash "hash123"
                                                 :mtime "2025-12-21T12:00:00Z"
                                                 :action "modify"})
          _ (sync/sync-file request)]
      ;; 旧路径应该找不到文档
      (is (nil? (db/get-note-by-path vault-id "before.md")))
      ;; 新路径应该能找到
      (is (some? (db/get-note-by-path vault-id "after.md")))))

  (testing "Same hash and path - 跳过更新"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          sync-key (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "skip-update.com" sync-key)
          doc-id (utils/generate-uuid)
          _ (db/upsert-note! doc-id tenant-id vault-id "unchanged.md" "unchanged-client-id"
                                 "# Same" "{}" "unchanged-hash" "2025-12-21T10:00:00Z")
          request (test-utils/sync-request-with-header sync-key
                                           :body {:path "unchanged.md"
                                                 :clientId "unchanged-client-id"
                                                 :content "# Same"
                                                 :metadata {}
                                                 :hash "unchanged-hash"
                                                 :mtime "2025-12-21T12:00:00Z"
                                                 :action "modify"})
          response (sync/sync-file request)]
      ;; 应返回成功但标记为 skipped
      (is (= 200 (:status response)))
      (is (get-in response [:body :success]))
      (is (true? (get-in response [:body :skipped])))
      (is (= "unchanged" (get-in response [:body :reason]))))))

;; ============================================================
;; Client Type 测试
;; ============================================================

(deftest test-sync-file-with-client-type
  (testing "Sync file stores client type from request"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          sync-key (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "client-type.com" sync-key)
          request (test-utils/sync-request-with-header sync-key
                                           :body {:path "test.md"
                                                 :clientId "test-client-with-type"
                                                 :clientType "obsidian"
                                                 :content "# Content"
                                                 :metadata {}
                                                 :hash "abc123"
                                                 :mtime "2025-12-21T10:00:00Z"
                                                 :action "create"})
          response (sync/sync-file request)]
      (is (= 200 (:status response)))
      (is (get-in response [:body :success])))))
