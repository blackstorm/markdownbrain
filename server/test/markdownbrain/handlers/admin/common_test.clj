(ns markdownbrain.handlers.admin.common-test
  "Tests for common admin utilities."
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [markdownbrain.db :as db]
   [markdownbrain.handlers.admin.common :as common]
   [markdownbrain.handlers.admin.test-utils :as test-utils :refer [authenticated-request]]
   [markdownbrain.object-store :as object-store]
   [markdownbrain.utils :as utils]))

(use-fixtures :each test-utils/setup-test-db)

(deftest test-format-storage-size
  (testing "Format nil as 0 B"
    (is (= "0 B" (common/format-storage-size nil))))

  (testing "Format bytes"
    (is (= "512 B" (common/format-storage-size 512))))

  (testing "Format KB"
    (is (= "1.5 KB" (common/format-storage-size 1536))))

  (testing "Format MB"
    (is (= "2.5 MB" (common/format-storage-size (* 2.5 1024 1024)))))

  (testing "Format GB"
    (is (= "1.50 GB" (common/format-storage-size (* 1.5 1024 1024 1024))))))

(deftest test-admin-asset-url
  (testing "Generate correct URL"
    (is (= "/console/storage/vault-123/site/logo/abc.png"
           (common/admin-asset-url "vault-123" "site/logo/abc.png")))))

(deftest test-serve-admin-asset-success
  (testing "Serve asset successfully"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          user-id (utils/generate-uuid)
          _ (db/create-user! user-id tenant-id "asset-admin" "hash")
          vault-id (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Asset Test" "asset-test.com" (utils/generate-uuid))
          test-content (.getBytes "test image content")
          mock-result {:Body (java.io.ByteArrayInputStream. test-content)
                       :ContentType "image/png"}]
      (with-redefs [object-store/get-object (fn [vid key]
                                              (when (and (= vid vault-id)
                                                         (= key "site/logo/abc123.png"))
                                                mock-result))]
        (let [request (-> (authenticated-request :get (str "/console/storage/" vault-id "/site/logo/abc123.png")
                                                 tenant-id user-id)
                          (assoc :path-params {:id vault-id :path "site/logo/abc123.png"}))
              response (common/serve-admin-asset request)]
          (is (= 200 (:status response)))
          (is (= "image/png" (get-in response [:headers "Content-Type"])))
          (is (bytes? (:body response))))))))

(deftest test-serve-admin-asset-not-found
  (testing "Serve asset for non-existent vault returns 404"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          user-id (utils/generate-uuid)
          _ (db/create-user! user-id tenant-id "notfound-asset-admin" "hash")

          request (-> (authenticated-request :get "/console/storage/non-existent-id/site/logo/test.png"
                                             tenant-id user-id)
                      (assoc :path-params {:id "non-existent-id" :path "site/logo/test.png"}))
          response (common/serve-admin-asset request)]
      (is (= 404 (:status response))))))

(deftest test-serve-admin-asset-wrong-tenant
  (testing "Serve asset for vault from different tenant returns 403"
    (let [tenant-id-1 (utils/generate-uuid)
          tenant-id-2 (utils/generate-uuid)
          _ (db/create-tenant! tenant-id-1 "Org 1")
          _ (db/create-tenant! tenant-id-2 "Org 2")
          user-id-1 (utils/generate-uuid)
          user-id-2 (utils/generate-uuid)
          _ (db/create-user! user-id-1 tenant-id-1 "tenant1-asset" "hash")
          _ (db/create-user! user-id-2 tenant-id-2 "tenant2-asset" "hash")
          vault-id (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id-1 "Tenant1 Vault" "tenant1.com" (utils/generate-uuid))

          request (-> (authenticated-request :get (str "/console/storage/" vault-id "/site/logo/test.png")
                                             tenant-id-2 user-id-2)
                      (assoc :path-params {:id vault-id :path "site/logo/test.png"}))
          response (common/serve-admin-asset request)]
      (is (= 403 (:status response))))))

(deftest test-serve-admin-asset-missing-path
  (testing "Serve asset with missing path returns 400"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          user-id (utils/generate-uuid)
          _ (db/create-user! user-id tenant-id "missing-path-admin" "hash")
          vault-id (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Missing Path Test" "missingpath.com" (utils/generate-uuid))

          request (-> (authenticated-request :get (str "/console/storage/" vault-id "/")
                                             tenant-id user-id)
                      (assoc :path-params {:id vault-id :path nil}))
          response (common/serve-admin-asset request)]
      (is (= 400 (:status response))))))

(deftest test-serve-admin-asset-not-found-in-storage
  (testing "Serve non-existent asset returns 404"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          user-id (utils/generate-uuid)
          _ (db/create-user! user-id tenant-id "nonexist-asset-admin" "hash")
          vault-id (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "NonExist Asset Test" "nonexistasset.com" (utils/generate-uuid))]
      (with-redefs [object-store/get-object (fn [_ _] nil)]
        (let [request (-> (authenticated-request :get (str "/console/storage/" vault-id "/site/logo/notfound.png")
                                                 tenant-id user-id)
                          (assoc :path-params {:id vault-id :path "site/logo/notfound.png"}))
              response (common/serve-admin-asset request)]
          (is (= 404 (:status response))))))))
