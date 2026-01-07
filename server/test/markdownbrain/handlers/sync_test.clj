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
          request (sync-request-with-header sync-key
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
          request (sync-request-with-header sync-key
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
          request (sync-request-with-header wrong-key
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

;; Sync File - Delete 测试
(deftest test-sync-file-delete
  (testing "Delete file with valid token"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          sync-key (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "sync-delete.com" sync-key)
          doc-id (utils/generate-uuid)
          _ (db/upsert-note! doc-id tenant-id vault-id "to-delete.md" "delete-client-id" "# Delete Me" "{}" "hash" "2025-12-21T10:00:00Z")
          request (sync-request-with-header sync-key
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
          request (sync-request-with-header sync-key
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
          request (sync-request-with-header "wrong-key"
                                           :body {:path "file.md"
                                                 :clientId "delete-token-client-id"
                                                 :action "delete"})
          response (sync/sync-file request)]
      (is (= 401 (:status response)))
      (is (false? (get-in response [:body :success])))
      ;; Verify document was NOT deleted
      (is (some? (db/get-note-by-path vault-id "file.md"))))))

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
          _ (db/upsert-note! (utils/generate-uuid) tenant-id vault-id "a.md" "c1" "c" "{}" "h1" "2024-01-01T00:00:00Z")
          _ (db/upsert-note! (utils/generate-uuid) tenant-id vault-id "b.md" "c2" "c" "{}" "h2" "2024-01-01T00:00:00Z")
          _ (db/upsert-note! (utils/generate-uuid) tenant-id vault-id "c.md" "c3" "c" "{}" "h3" "2024-01-01T00:00:00Z")
          ;; 客户端只有 c1 和 c2
          request (sync-request-with-header sync-key
                                           :body {:clientIds ["c1" "c2"]})
          response (sync/sync-full request)
          body (:body response)]
      ;; Then: 删除 1 个孤儿 (c3)
      (is (= 200 (:status response)))
      (is (true? (get-in body [:success])))
      (is (= 1 (get-in body [:deleted-count])))
      (is (= 2 (get-in body [:remaining-notes])))
      ;; 验证文档状态
      (is (some? (db/get-note-by-client-id vault-id "c1")))
      (is (some? (db/get-note-by-client-id vault-id "c2")))
      (is (nil? (db/get-note-by-client-id vault-id "c3"))))))

(deftest test-sync-full-empty-list
  (testing "Full sync 空列表删除所有文档"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          sync-key (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "empty-full-sync.com" sync-key)
          _ (db/upsert-note! (utils/generate-uuid) tenant-id vault-id "a.md" "c1" "c" "{}" "h" "2024-01-01T00:00:00Z")
          _ (db/upsert-note! (utils/generate-uuid) tenant-id vault-id "b.md" "c2" "c" "{}" "h" "2024-01-01T00:00:00Z")
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
          _ (db/upsert-note! (utils/generate-uuid) tenant-id vault-id "a.md" "c1" "c" "{}" "h1" "2024-01-01T00:00:00Z")
          _ (db/upsert-note! (utils/generate-uuid) tenant-id vault-id "b.md" "c2" "c" "{}" "h2" "2024-01-01T00:00:00Z")
          request (sync-request-with-header sync-key
                                           :body {:clientIds ["c1" "c2"]})
          response (sync/sync-full request)
          body (:body response)]
      (is (= 200 (:status response)))
      (is (= 0 (get-in body [:deleted-count])))
      (is (= 2 (get-in body [:remaining-notes]))))))

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

;; Sync with clientType field (Obsidian plugin compatibility)
(deftest test-sync-file-with-client-type
  (testing "Sync file with clientType field (from Obsidian plugin)"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          sync-key (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "client-type-test.com" sync-key)
          request (sync-request-with-header sync-key
                                           :body {:path "test.md"
                                                 :clientId "test-client-with-type"
                                                 :clientType "obsidian"
                                                 :content "# Test Content"
                                                 :metadata {:tags ["test"]}
                                                 :hash "abc123"
                                                 :mtime "2025-12-21T10:00:00Z"
                                                 :action "create"})
          response (sync/sync-file request)]
      (is (= 200 (:status response)))
      (is (get-in response [:body :success])))))

;; ============================================================
;; File Rename - 文件重命名测试
;; ============================================================

;; ============================================================
;; Server-side Link Parsing - 服务端解析链接测试
;; ============================================================

(deftest test-sync-file-server-parses-links
  (testing "服务端从 content 解析链接并存入 document_links"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          sync-key (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "link-parse-test.com" sync-key)
          ;; 先创建目标文档
          _ (db/upsert-note! (utils/generate-uuid) tenant-id vault-id "Target Note.md" "target-client-id"
                                 "# Target" "{}" "h1" "2025-01-01T00:00:00Z")
          ;; 同步包含链接的源文档（不发送 metadata.links）
          request (sync-request-with-header sync-key
                                           :body {:path "source.md"
                                                 :clientId "source-client-id"
                                                 :content "# Source\n\nSee [[Target Note]] for details."
                                                 :metadata {}  ;; 没有 links 字段
                                                 :hash "source-hash"
                                                 :mtime "2025-01-01T00:00:00Z"
                                                 :action "create"})
          response (sync/sync-file request)]
      ;; 同步成功
      (is (= 200 (:status response)))
      ;; 验证链接被正确解析并存入 document_links
      (let [links (db/get-note-links vault-id "source-client-id")]
        (is (= 1 (count links)))
        (is (= "target-client-id" (:target-client-id (first links))))
        (is (= "Target Note" (:target-path (first links))))
        (is (= "link" (:link-type (first links))))
        (is (= "Target Note" (:display-text (first links))))
        (is (= "[[Target Note]]" (:original (first links)))))))

  (testing "服务端解析多个链接"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          sync-key (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "multi-link-test.com" sync-key)
          ;; 创建目标文档
          _ (db/upsert-note! (utils/generate-uuid) tenant-id vault-id "Note A.md" "client-a"
                                 "# A" "{}" "ha" "2025-01-01T00:00:00Z")
          _ (db/upsert-note! (utils/generate-uuid) tenant-id vault-id "Note B.md" "client-b"
                                 "# B" "{}" "hb" "2025-01-01T00:00:00Z")
          ;; 同步包含多个链接的文档
          request (sync-request-with-header sync-key
                                           :body {:path "hub.md"
                                                 :clientId "hub-client-id"
                                                 :content "# Hub\n\n[[Note A]] and [[Note B|别名]]"
                                                 :metadata {}
                                                 :hash "hub-hash"
                                                 :mtime "2025-01-01T00:00:00Z"
                                                 :action "create"})
          response (sync/sync-file request)]
      (is (= 200 (:status response)))
      (let [links (db/get-note-links vault-id "hub-client-id")]
        (is (= 2 (count links)))
        (is (= #{"client-a" "client-b"} (set (map :target-client-id links)))))))

  (testing "链接目标不存在时不存储（broken link 在渲染时处理）"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          sync-key (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "broken-link-test.com" sync-key)
          ;; 同步包含不存在目标的链接
          request (sync-request-with-header sync-key
                                           :body {:path "orphan.md"
                                                 :clientId "orphan-client-id"
                                                 :content "# Orphan\n\nSee [[Non Existent]]"
                                                 :metadata {}
                                                 :hash "orphan-hash"
                                                 :mtime "2025-01-01T00:00:00Z"
                                                 :action "create"})
          response (sync/sync-file request)]
      (is (= 200 (:status response)))
      ;; broken link 不存储到 document_links
      (let [links (db/get-note-links vault-id "orphan-client-id")]
        (is (empty? links)))))

  (testing "更新文档时重新解析链接"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          sync-key (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "update-links-test.com" sync-key)
          ;; 创建目标文档
          _ (db/upsert-note! (utils/generate-uuid) tenant-id vault-id "Old Target.md" "old-target"
                                 "# Old" "{}" "ho" "2025-01-01T00:00:00Z")
          _ (db/upsert-note! (utils/generate-uuid) tenant-id vault-id "New Target.md" "new-target"
                                 "# New" "{}" "hn" "2025-01-01T00:00:00Z")
          ;; 第一次同步：链接到 Old Target
          _ (sync/sync-file (sync-request-with-header sync-key
                                                      :body {:path "source.md"
                                                            :clientId "update-source"
                                                            :content "[[Old Target]]"
                                                            :metadata {}
                                                            :hash "hash1"
                                                            :mtime "2025-01-01T00:00:00Z"
                                                            :action "create"}))
          ;; 验证第一次的链接
          links-v1 (db/get-note-links vault-id "update-source")
          _ (is (= 1 (count links-v1)))
          _ (is (= "old-target" (:target-client-id (first links-v1))))
          ;; 第二次同步：改为链接到 New Target
          _ (sync/sync-file (sync-request-with-header sync-key
                                                      :body {:path "source.md"
                                                            :clientId "update-source"
                                                            :content "[[New Target]]"
                                                            :metadata {}
                                                            :hash "hash2"
                                                            :mtime "2025-01-01T01:00:00Z"
                                                            :action "modify"}))
          ;; 验证链接已更新
          links-v2 (db/get-note-links vault-id "update-source")]
      (is (= 1 (count links-v2)))
      (is (= "new-target" (:target-client-id (first links-v2))))))

  (testing "无链接的文档不创建 document_links 记录"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          sync-key (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "no-links-test.com" sync-key)
          request (sync-request-with-header sync-key
                                           :body {:path "plain.md"
                                                 :clientId "plain-client-id"
                                                 :content "# Plain text\n\nNo links here."
                                                 :metadata {}
                                                 :hash "plain-hash"
                                                 :mtime "2025-01-01T00:00:00Z"
                                                 :action "create"})
          response (sync/sync-file request)]
      (is (= 200 (:status response)))
      (let [links (db/get-note-links vault-id "plain-client-id")]
        (is (empty? links))))))

;; ============================================================
;; File Rename - 文件重命名测试
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
          request (sync-request-with-header sync-key
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
          request (sync-request-with-header sync-key
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
          request (sync-request-with-header sync-key
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
;; Resource Sync 测试
;; ============================================================

(defn resource-sync-request
  [sync-key & {:keys [body]}]
  (-> (mock/request :post "/obsidian/resources/sync")
      (assoc :headers {"authorization" (str "Bearer " sync-key)})
      (assoc :body-params body)))

(deftest test-sync-resource-delete
  (testing "Delete resource soft-deletes from database"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          sync-key (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "res-delete.com" sync-key)
          _ (db/upsert-resource! (utils/generate-uuid) tenant-id vault-id "images/photo.png"
                                 "resources/images/photo.png" 1000 "image/png" "hash123")
          request (resource-sync-request sync-key
                                         :body {:path "images/photo.png"
                                                :action "delete"})
          response (sync/sync-resource request)]
      (is (= 200 (:status response)))
      (is (get-in response [:body :success]))
      (is (nil? (db/get-resource-by-path vault-id "images/photo.png"))))))

(deftest test-sync-resource-validation
  (testing "Resource sync requires valid fields"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          sync-key (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "res-validation.com" sync-key)
          request (resource-sync-request sync-key
                                         :body {:path ""
                                                :action "invalid"})
          response (sync/sync-resource request)]
      (is (= 400 (:status response)))
      (is (false? (get-in response [:body :success])))))

  (testing "Create/modify requires size, contentType, sha256"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          sync-key (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "res-metadata.com" sync-key)
          request (resource-sync-request sync-key
                                         :body {:path "images/logo.png"
                                                :action "create"})
          response (sync/sync-resource request)]
      (is (= 400 (:status response)))
      (is (false? (get-in response [:body :success]))))))

(deftest test-sync-resource-unauthorized
  (testing "Resource sync with invalid token"
    (let [request (resource-sync-request "invalid-token"
                                         :body {:path "images/test.png"
                                                :action "delete"})
          response (sync/sync-resource request)]
      (is (= 401 (:status response)))
      (is (false? (get-in response [:body :success])))))

  (testing "Resource sync without authorization"
    (let [request (-> (mock/request :post "/obsidian/resources/sync")
                      (assoc :body-params {:path "test.png" :action "delete"}))
          response (sync/sync-resource request)]
      (is (= 401 (:status response)))
      (is (false? (get-in response [:body :success]))))))
