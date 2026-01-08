(ns markdownbrain.handlers.sync-full-test
  "Full sync tests: orphan note cleanup"
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [markdownbrain.db :as db]
            [markdownbrain.utils :as utils]
            [markdownbrain.handlers.sync :as sync]
            [markdownbrain.handlers.sync-test-utils :as test-utils]
            [ring.mock.request :as mock]))

(use-fixtures :each test-utils/setup-test-db)

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
          request (test-utils/sync-request-with-header sync-key
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
          request (test-utils/sync-request-with-header sync-key
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
          request (test-utils/sync-request-with-header sync-key
                                       :body {:clientIds ["c1" "c2"]})
          response (sync/sync-full request)
          body (:body response)]
      (is (= 200 (:status response)))
      (is (= 0 (get-in body [:deleted-count])))
      (is (= 2 (get-in body [:remaining-notes]))))))

(deftest test-sync-full-unauthorized
  (testing "Full sync 无效 token"
    (let [request (test-utils/sync-request-with-header "invalid-token"
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
