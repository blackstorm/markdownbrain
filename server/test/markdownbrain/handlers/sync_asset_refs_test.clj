(ns markdownbrain.handlers.sync-asset-refs-test
  "Note-Asset reference tracking and orphan cleanup tests"
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [markdownbrain.db :as db]
            [markdownbrain.utils :as utils]
            [markdownbrain.handlers.sync :as sync]
            [markdownbrain.handlers.sync-test-utils :as test-utils]))

(use-fixtures :each test-utils/setup-test-db)

;; ============================================================
;; Note-Asset Reference Tracking 测试
;; 当 note 同步时，解析嵌入的 asset 引用并更新 note_asset_refs 表
;; ============================================================

(deftest test-note-asset-refs-tracking
  (testing "同步 note 时解析并记录 asset 引用"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          sync-key (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "asset-refs-1.com" sync-key)
          ;; 先创建两个 asset
          _ (db/upsert-asset! (utils/generate-uuid) tenant-id vault-id "image-client-1" "images/photo.png"
                              "assets/image-client-1" 1000 "image/png" "hash1")
          _ (db/upsert-asset! (utils/generate-uuid) tenant-id vault-id "pdf-client-1" "docs/report.pdf"
                              "assets/pdf-client-1" 5000 "application/pdf" "hash2")
          ;; 同步一个引用这两个 asset 的 note
          request (test-utils/sync-request-with-header sync-key
                                       :body {:path "note-with-assets.md"
                                             :clientId "note-client-1"
                                             :content "# My Note\n\n![[images/photo.png]]\n\nSee ![[docs/report.pdf]]"
                                             :metadata {}
                                             :hash "note-hash-1"
                                             :mtime "2025-12-21T10:00:00Z"
                                             :action "create"})
          response (sync/sync-file request)]
      (is (= 200 (:status response)))
      ;; 验证 note_asset_refs 表中有两条记录
      (let [refs (db/get-asset-refs-by-note vault-id "note-client-1")]
        (is (= 2 (count refs)))
        (is (= #{"image-client-1" "pdf-client-1"}
               (set (map :asset-client-id refs)))))))

  (testing "更新 note 时更新 asset 引用 - 移除旧引用添加新引用"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          sync-key (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "asset-refs-2.com" sync-key)
          ;; 创建三个 asset
          _ (db/upsert-asset! (utils/generate-uuid) tenant-id vault-id "asset-a" "a.png"
                              "assets/asset-a" 100 "image/png" "ha")
          _ (db/upsert-asset! (utils/generate-uuid) tenant-id vault-id "asset-b" "b.png"
                              "assets/asset-b" 100 "image/png" "hb")
          _ (db/upsert-asset! (utils/generate-uuid) tenant-id vault-id "asset-c" "c.png"
                              "assets/asset-c" 100 "image/png" "hc")
          ;; 第一次同步：引用 asset-a 和 asset-b
          _ (sync/sync-file (test-utils/sync-request-with-header sync-key
                                                     :body {:path "note.md"
                                                           :clientId "note-update-test"
                                                           :content "![[a.png]]\n![[b.png]]"
                                                           :metadata {}
                                                           :hash "h1"
                                                           :mtime "2025-12-21T10:00:00Z"
                                                           :action "create"}))
          refs-v1 (db/get-asset-refs-by-note vault-id "note-update-test")
          _ (is (= #{"asset-a" "asset-b"} (set (map :asset-client-id refs-v1))))
          ;; 第二次同步：移除 asset-a，添加 asset-c
          _ (sync/sync-file (test-utils/sync-request-with-header sync-key
                                                     :body {:path "note.md"
                                                           :clientId "note-update-test"
                                                           :content "![[b.png]]\n![[c.png]]"
                                                           :metadata {}
                                                           :hash "h2"
                                                           :mtime "2025-12-21T11:00:00Z"
                                                           :action "modify"}))
          refs-v2 (db/get-asset-refs-by-note vault-id "note-update-test")]
      ;; 验证：asset-a 被移除，asset-c 被添加
      (is (= #{"asset-b" "asset-c"} (set (map :asset-client-id refs-v2))))))

  (testing "note 中没有 asset 引用时，refs 表为空"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          sync-key (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "asset-refs-3.com" sync-key)
          _ (sync/sync-file (test-utils/sync-request-with-header sync-key
                                                     :body {:path "plain.md"
                                                           :clientId "plain-note"
                                                           :content "# Just text\n\nNo images here."
                                                           :metadata {}
                                                           :hash "plain-h"
                                                           :mtime "2025-12-21T10:00:00Z"
                                                           :action "create"}))
          refs (db/get-asset-refs-by-note vault-id "plain-note")]
      (is (empty? refs)))))

;; ============================================================
;; Orphan Asset Cleanup 测试
;; 当 asset 引用计数归零时，立即删除 asset
;; ============================================================

(deftest test-orphan-asset-immediate-cleanup
  (testing "单个 note 移除对 asset 的引用时，asset 被删除"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          sync-key (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "orphan-cleanup-1.com" sync-key)
          ;; 创建一个 asset
          _ (db/upsert-asset! (utils/generate-uuid) tenant-id vault-id "lonely-asset" "lonely.png"
                              "assets/lonely-asset" 100 "image/png" "lonely-hash")
          ;; 同步一个引用该 asset 的 note
          _ (sync/sync-file (test-utils/sync-request-with-header sync-key
                                                     :body {:path "referrer.md"
                                                           :clientId "referrer-note"
                                                           :content "![[lonely.png]]"
                                                           :metadata {}
                                                           :hash "ref-h1"
                                                           :mtime "2025-12-21T10:00:00Z"
                                                           :action "create"}))
          ;; 验证 asset 存在
          _ (is (some? (db/get-asset-by-client-id vault-id "lonely-asset")))
          ;; 更新 note，移除对 asset 的引用
          _ (sync/sync-file (test-utils/sync-request-with-header sync-key
                                                     :body {:path "referrer.md"
                                                           :clientId "referrer-note"
                                                           :content "# No more image"
                                                           :metadata {}
                                                           :hash "ref-h2"
                                                           :mtime "2025-12-21T11:00:00Z"
                                                           :action "modify"}))]
      ;; 验证 asset 已被删除（soft delete）
      (is (nil? (db/get-asset-by-client-id vault-id "lonely-asset")))))

  (testing "多个 note 引用同一个 asset 时，只有最后一个移除引用才删除 asset"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          sync-key (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "orphan-cleanup-2.com" sync-key)
          ;; 创建一个 shared asset
          _ (db/upsert-asset! (utils/generate-uuid) tenant-id vault-id "shared-asset" "shared.png"
                              "assets/shared-asset" 100 "image/png" "shared-hash")
          ;; 两个 note 都引用这个 asset
          _ (sync/sync-file (test-utils/sync-request-with-header sync-key
                                                     :body {:path "note-a.md"
                                                           :clientId "note-a"
                                                           :content "![[shared.png]]"
                                                           :metadata {}
                                                           :hash "a-h1"
                                                           :mtime "2025-12-21T10:00:00Z"
                                                           :action "create"}))
          _ (sync/sync-file (test-utils/sync-request-with-header sync-key
                                                     :body {:path "note-b.md"
                                                           :clientId "note-b"
                                                           :content "![[shared.png]]"
                                                           :metadata {}
                                                           :hash "b-h1"
                                                           :mtime "2025-12-21T10:00:00Z"
                                                           :action "create"}))
          ;; note-a 移除引用
          _ (sync/sync-file (test-utils/sync-request-with-header sync-key
                                                     :body {:path "note-a.md"
                                                           :clientId "note-a"
                                                           :content "# Empty"
                                                           :metadata {}
                                                           :hash "a-h2"
                                                           :mtime "2025-12-21T11:00:00Z"
                                                           :action "modify"}))
          ;; asset 应该还在（note-b 还在引用）
          _ (is (some? (db/get-asset-by-client-id vault-id "shared-asset")))
          ;; note-b 也移除引用
          _ (sync/sync-file (test-utils/sync-request-with-header sync-key
                                                     :body {:path "note-b.md"
                                                           :clientId "note-b"
                                                           :content "# Empty too"
                                                           :metadata {}
                                                           :hash "b-h2"
                                                           :mtime "2025-12-21T12:00:00Z"
                                                           :action "modify"}))]
      ;; 现在 asset 应该被删除了
      (is (nil? (db/get-asset-by-client-id vault-id "shared-asset")))))

  (testing "删除 note 时清理孤立 asset"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          sync-key (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "orphan-cleanup-3.com" sync-key)
          ;; 创建 asset
          _ (db/upsert-asset! (utils/generate-uuid) tenant-id vault-id "delete-test-asset" "delete-me.png"
                              "assets/delete-test-asset" 100 "image/png" "del-hash")
          ;; note 引用 asset
          _ (sync/sync-file (test-utils/sync-request-with-header sync-key
                                                     :body {:path "to-delete.md"
                                                           :clientId "to-delete-note"
                                                           :content "![[delete-me.png]]"
                                                           :metadata {}
                                                           :hash "del-h1"
                                                           :mtime "2025-12-21T10:00:00Z"
                                                           :action "create"}))
          _ (is (some? (db/get-asset-by-client-id vault-id "delete-test-asset")))
          ;; 删除 note
          _ (sync/sync-file (test-utils/sync-request-with-header sync-key
                                                     :body {:path "to-delete.md"
                                                           :clientId "to-delete-note"
                                                           :action "delete"}))]
      ;; asset 应该被删除
      (is (nil? (db/get-asset-by-client-id vault-id "delete-test-asset"))))))
