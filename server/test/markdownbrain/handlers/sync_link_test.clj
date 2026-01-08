(ns markdownbrain.handlers.sync-link-test
  "Server-side link parsing tests"
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [markdownbrain.db :as db]
            [markdownbrain.utils :as utils]
            [markdownbrain.handlers.sync :as sync]
            [markdownbrain.handlers.sync-test-utils :as test-utils]))

(use-fixtures :each test-utils/setup-test-db)

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
          request (test-utils/sync-request-with-header sync-key
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
          request (test-utils/sync-request-with-header sync-key
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
          request (test-utils/sync-request-with-header sync-key
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
          _ (sync/sync-file (test-utils/sync-request-with-header sync-key
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
          _ (sync/sync-file (test-utils/sync-request-with-header sync-key
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
          request (test-utils/sync-request-with-header sync-key
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
