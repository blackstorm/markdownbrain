(ns markdownbrain.handlers.sync-asset-test
  "Asset sync tests: create, delete, validation"
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [markdownbrain.db :as db]
            [markdownbrain.utils :as utils]
            [markdownbrain.handlers.sync :as sync]
            [markdownbrain.handlers.sync-test-utils :as test-utils]
            [ring.mock.request :as mock]))

(use-fixtures :each test-utils/setup-test-db)

;; ============================================================
;; Asset Sync 测试
;; ============================================================

(deftest test-sync-asset-delete
  (testing "Delete asset soft-deletes from database"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          sync-key (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "asset-delete.com" sync-key)
          asset-client-id "asset-client-123"
          _ (db/upsert-asset! (utils/generate-uuid) tenant-id vault-id asset-client-id "images/photo.png"
                              "assets/images/photo.png" 1000 "image/png" "hash123")
          request (test-utils/asset-sync-request sync-key
                                      :body {:path "images/photo.png"
                                             :clientId asset-client-id
                                             :action "delete"})
          response (sync/sync-asset request)]
      (is (= 200 (:status response)))
      (is (get-in response [:body :success]))
      (is (= asset-client-id (get-in response [:body :client-id])))
      (is (nil? (db/get-asset-by-client-id vault-id asset-client-id))))))

(deftest test-sync-asset-validation
  (testing "Asset sync requires valid fields"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          sync-key (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "asset-validation.com" sync-key)
          request (test-utils/asset-sync-request sync-key
                                      :body {:path ""
                                             :clientId ""
                                             :action "invalid"})
          response (sync/sync-asset request)]
      (is (= 400 (:status response)))
      (is (false? (get-in response [:body :success])))))

  (testing "Create/modify requires size, contentType, sha256"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          vault-id (utils/generate-uuid)
          sync-key (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Blog" "asset-metadata.com" sync-key)
          request (test-utils/asset-sync-request sync-key
                                      :body {:path "images/logo.png"
                                             :clientId "logo-client-id"
                                             :action "create"})
          response (sync/sync-asset request)]
      (is (= 400 (:status response)))
      (is (false? (get-in response [:body :success]))))))

(deftest test-sync-asset-unauthorized
  (testing "Asset sync with invalid token"
    (let [request (test-utils/asset-sync-request "invalid-token"
                                      :body {:path "images/test.png"
                                             :clientId "test-asset-client"
                                             :action "delete"})
          response (sync/sync-asset request)]
      (is (= 401 (:status response)))
      (is (false? (get-in response [:body :success])))))

  (testing "Asset sync without authorization"
    (let [request (-> (mock/request :post "/obsidian/assets/sync")
                      (assoc :body-params {:path "test.png" :clientId "test-client" :action "delete"}))
          response (sync/sync-asset request)]
      (is (= 401 (:status response)))
      (is (false? (get-in response [:body :success]))))))
