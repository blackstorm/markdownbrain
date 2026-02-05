(ns mdbrain.handlers.console.common-test
  "Tests for common console utilities."
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [mdbrain.db :as db]
   [mdbrain.handlers.console.common :as common]
   [mdbrain.handlers.console.test-utils :as test-utils :refer [authenticated-request]]
   [mdbrain.object-store :as object-store]
   [mdbrain.utils :as utils]))

(use-fixtures :each test-utils/setup-test-db)

(deftest test-console-asset-url
  (testing "Generate correct URL"
    (is (= "/console/storage/vault-123/site/logo/abc.png"
           (common/console-asset-url "vault-123" "site/logo/abc.png")))))

(deftest test-serve-console-asset-success
  (testing "Serve asset successfully"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          user-id (utils/generate-uuid)
          _ (db/create-user! user-id tenant-id "asset-console" "hash")
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
              response (common/serve-console-asset request)]
          (is (= 200 (:status response)))
          (is (= "image/png" (get-in response [:headers "Content-Type"])))
          (is (bytes? (:body response))))))))

(deftest test-serve-console-asset-not-found
  (testing "Serve asset for non-existent vault returns 404"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          user-id (utils/generate-uuid)
          _ (db/create-user! user-id tenant-id "notfound-asset-console" "hash")

          request (-> (authenticated-request :get "/console/storage/non-existent-id/site/logo/test.png"
                                             tenant-id user-id)
                      (assoc :path-params {:id "non-existent-id" :path "site/logo/test.png"}))
          response (common/serve-console-asset request)]
      (is (= 404 (:status response))))))

(deftest test-serve-console-asset-wrong-tenant
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
          response (common/serve-console-asset request)]
      (is (= 403 (:status response))))))

(deftest test-serve-console-asset-missing-path
  (testing "Serve asset with missing path returns 400"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          user-id (utils/generate-uuid)
          _ (db/create-user! user-id tenant-id "missing-path-console" "hash")
          vault-id (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Missing Path Test" "missingpath.com" (utils/generate-uuid))

          request (-> (authenticated-request :get (str "/console/storage/" vault-id "/")
                                             tenant-id user-id)
                      (assoc :path-params {:id vault-id :path nil}))
          response (common/serve-console-asset request)]
      (is (= 400 (:status response))))))

(deftest test-serve-console-asset-not-found-in-storage
  (testing "Serve non-existent asset returns 404"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          user-id (utils/generate-uuid)
          _ (db/create-user! user-id tenant-id "nonexist-asset-console" "hash")
          vault-id (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "NonExist Asset Test" "nonexistasset.com" (utils/generate-uuid))]
      (with-redefs [object-store/get-object (fn [_ _] nil)]
        (let [request (-> (authenticated-request :get (str "/console/storage/" vault-id "/site/logo/notfound.png")
                                                 tenant-id user-id)
                          (assoc :path-params {:id vault-id :path "site/logo/notfound.png"}))
              response (common/serve-console-asset request)]
          (is (= 404 (:status response))))))))
