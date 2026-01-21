(ns markdownbrain.handlers.console.logo-test
  "Tests for logo upload and serving handlers."
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [markdownbrain.db :as db]
   [markdownbrain.handlers.console.logo :as logo]
   [markdownbrain.handlers.console.test-utils :as test-utils
    :refer [authenticated-request create-test-png create-temp-file bytes=]]
   [markdownbrain.image-processing :as image-processing]
   [markdownbrain.object-store :as object-store]
   [markdownbrain.utils :as utils]))

(use-fixtures :each test-utils/setup-test-db)

(deftest test-upload-vault-logo-success
  (testing "Successful logo upload generates original and favicon"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          user-id (utils/generate-uuid)
          _ (db/create-user! user-id tenant-id "logo-console" "hash")
          vault-id (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Logo Vault" "logo.com" (utils/generate-uuid))
          logo-bytes (create-test-png 100 100)
          logo-file (create-temp-file logo-bytes "image/png")
          uploaded-logo-key (atom nil)
          uploaded-favicon-key (atom nil)]

      (with-redefs [image-processing/generate-favicon (fn [_bytes _content-type _content-hash _extension]
                                                        {:object-key "site/logo/fake.png@favicon.png"
                                                         :bytes (byte-array [1 2 3])})
                    object-store/put-object! (fn [vid key content content-type]
                                               (when (= vid vault-id)
                                                 (if (str/includes? key "@favicon.")
                                                   (reset! uploaded-favicon-key key)
                                                   (reset! uploaded-logo-key key))))
                    object-store/delete-object! (fn [vid key] nil)]

        (let [request (-> (authenticated-request :post (str "/console/vaults/" vault-id "/logo")
                                                 tenant-id user-id)
                          (assoc :path-params {:id vault-id})
                          (assoc :multipart-params {"logo" logo-file}))
              response (logo/upload-vault-logo request)]

          (is (= 200 (:status response)))
          (is (true? (get-in response [:body :success])))
          (is @uploaded-logo-key "Original logo should be uploaded")
          (is @uploaded-favicon-key "Favicon should be generated and uploaded")

          (let [vault (db/get-vault-by-id vault-id)]
            (is (= @uploaded-logo-key (:logo-object-key vault))
                "Database should reference the original logo key")))))))

(deftest test-upload-vault-logo-replaces-old
  (testing "Uploading new logo deletes old one"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          user-id (utils/generate-uuid)
          _ (db/create-user! user-id tenant-id "logo-replace-console" "hash")
          vault-id (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Logo Replace Vault" "logoreplace.com" (utils/generate-uuid))
          deleted-keys (atom [])
          first-logo-key "site/logo/oldhash.png"
          logo-bytes (create-test-png 100 100)
          logo-file (create-temp-file logo-bytes "image/png")]

      (db/update-vault-logo! vault-id first-logo-key)

      (with-redefs [image-processing/generate-favicon (fn [& _] nil)
                    object-store/put-object! (fn [vid key content content-type] nil)
                    object-store/delete-object! (fn [vid key]
                                                  (swap! deleted-keys conj key))]

        (let [request (-> (authenticated-request :post (str "/console/vaults/" vault-id "/logo")
                                                 tenant-id user-id)
                          (assoc :path-params {:id vault-id})
                          (assoc :multipart-params {"logo" logo-file}))
              _ (logo/upload-vault-logo request)]

          (is (some #(str/includes? % "@favicon.") @deleted-keys)
              "Old favicon should be deleted")
          (is (some #(= % first-logo-key) @deleted-keys)
              "Old logo should be deleted"))))))

(deftest test-upload-vault-logo-small-image
  (testing "Small images that cannot generate favicon still work"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          user-id (utils/generate-uuid)
          _ (db/create-user! user-id tenant-id "small-logo-console" "hash")
          vault-id (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Small Logo Vault" "smalllogo.com" (utils/generate-uuid))
          logo-bytes (create-test-png 10 10)
          logo-file (create-temp-file logo-bytes "image/png")
          uploaded-logo-key (atom nil)
          uploaded-favicon-key (atom nil)]

      (with-redefs [image-processing/generate-favicon (fn [& _] nil)
                    object-store/put-object! (fn [vid key content content-type]
                                               (when (= vid vault-id)
                                                 (if (str/includes? key "@favicon.")
                                                   (reset! uploaded-favicon-key key)
                                                   (reset! uploaded-logo-key key))))
                    object-store/delete-object! (fn [vid key] nil)]

        (let [request (-> (authenticated-request :post (str "/console/vaults/" vault-id "/logo")
                                                 tenant-id user-id)
                          (assoc :path-params {:id vault-id})
                          (assoc :multipart-params {"logo" logo-file}))
              response (logo/upload-vault-logo request)]

          (is (= 200 (:status response)))
          (is (true? (get-in response [:body :success])))
          (is @uploaded-logo-key "Original logo should be uploaded")
          (is (nil? @uploaded-favicon-key) "Favicon should not be generated for tiny images"))))))

(deftest test-upload-vault-logo-validation
  (testing "Upload without file returns 400"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          user-id (utils/generate-uuid)
          _ (db/create-user! user-id tenant-id "no-file-console" "hash")
          vault-id (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "No File Vault" "nofile.com" (utils/generate-uuid))
          request (-> (authenticated-request :post (str "/console/vaults/" vault-id "/logo")
                                             tenant-id user-id)
                      (assoc :path-params {:id vault-id}))
          response (logo/upload-vault-logo request)]
      (is (= 400 (:status response)))
      (is (= "No file uploaded" (get-in response [:body :error])))))

  (testing "Upload with invalid content-type returns 400"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          user-id (utils/generate-uuid)
          _ (db/create-user! user-id tenant-id "bad-type-console" "hash")
          vault-id (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Bad Type Vault" "badtype.com" (utils/generate-uuid))
          logo-bytes (create-test-png 100 100)
          logo-file (create-temp-file logo-bytes "text/plain")
          request (-> (authenticated-request :post (str "/console/vaults/" vault-id "/logo")
                                             tenant-id user-id)
                      (assoc :path-params {:id vault-id})
                      (assoc :multipart-params {"logo" logo-file}))
          response (logo/upload-vault-logo request)]
      (is (= 400 (:status response)))
      (is (str/includes? (get-in response [:body :error]) "Invalid file type")))))

(deftest test-serve-vault-favicon-with-favicon
  (testing "Serve favicon when it exists"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          user-id (utils/generate-uuid)
          _ (db/create-user! user-id tenant-id "favicon-console" "hash")
          vault-id (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Favicon Vault" "favicon.com" (utils/generate-uuid))
          logo-key "site/logo/testhash.png"
          favicon-bytes (byte-array (range 100))]

      (db/update-vault-logo! vault-id logo-key)

      (with-redefs [object-store/get-object (fn [vid key]
                                              (when (= vid vault-id)
                                                (if (str/includes? key "@favicon.")
                                                  {:Body (java.io.ByteArrayInputStream. favicon-bytes)
                                                   :ContentType "image/png"}
                                                  nil)))]

        (let [request (-> (authenticated-request :get (str "/console/vaults/" vault-id "/favicon")
                                                 tenant-id user-id)
                          (assoc :path-params {:id vault-id}))
              response (logo/serve-vault-favicon request)]

          (is (= 200 (:status response)))
          (is (= "image/png" (get-in response [:headers "Content-Type"])))
          (is (bytes= favicon-bytes (:body response))))))))

(deftest test-serve-vault-favicon-fallback-to-logo
  (testing "Serve original logo when favicon doesn't exist"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          user-id (utils/generate-uuid)
          _ (db/create-user! user-id tenant-id "fallback-console" "hash")
          vault-id (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Fallback Vault" "fallback.com" (utils/generate-uuid))
          logo-key "site/logo/testhash.png"
          logo-bytes (byte-array (range 200))]

      (db/update-vault-logo! vault-id logo-key)

      (with-redefs [object-store/get-object (fn [vid key]
                                              (when (and (= vid vault-id)
                                                         (= key logo-key))
                                                {:Body (java.io.ByteArrayInputStream. logo-bytes)
                                                 :ContentType "image/png"}))]

        (let [request (-> (authenticated-request :get (str "/console/vaults/" vault-id "/favicon")
                                                 tenant-id user-id)
                          (assoc :path-params {:id vault-id}))
              response (logo/serve-vault-favicon request)]

          (is (= 200 (:status response)))
          (is (bytes= logo-bytes (:body response))
              "Should fallback to original logo when favicon doesn't exist"))))))

(deftest test-serve-vault-favicon-no-logo
  (testing "Serve favicon when no logo is set returns 404"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          user-id (utils/generate-uuid)
          _ (db/create-user! user-id tenant-id "no-logo-console" "hash")
          vault-id (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "No Logo Vault" "nologo.com" (utils/generate-uuid))
          request (-> (authenticated-request :get (str "/console/vaults/" vault-id "/favicon")
                                             tenant-id user-id)
                      (assoc :path-params {:id vault-id}))
          response (logo/serve-vault-favicon request)]
      (is (= 404 (:status response))))))

(deftest test-delete-vault-logo
  (testing "Delete logo successfully"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          user-id (utils/generate-uuid)
          _ (db/create-user! user-id tenant-id "delete-logo-console" "hash")
          vault-id (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "Delete Logo Vault" "deletelogo.com" (utils/generate-uuid))
          logo-key "site/logo/testhash.png"
          deleted-keys (atom [])]

      (db/update-vault-logo! vault-id logo-key)

      (with-redefs [object-store/delete-object! (fn [vid key]
                                                  (swap! deleted-keys conj key))]

        (let [request (-> (authenticated-request :delete (str "/console/vaults/" vault-id "/logo")
                                                 tenant-id user-id)
                          (assoc :path-params {:id vault-id}))
              response (logo/delete-vault-logo request)]

          (is (= 200 (:status response)))
          (is (true? (get-in response [:body :success])))
          (is (some #(= % logo-key) @deleted-keys) "Logo should be deleted")
          (is (some #(str/includes? % "@favicon.") @deleted-keys) "Favicon should be deleted")

          (let [vault (db/get-vault-by-id vault-id)]
            (is (nil? (:logo-object-key vault)) "Logo key should be cleared in DB")))))))

(deftest test-delete-vault-logo-no-logo
  (testing "Delete logo when no logo exists returns success"
    (let [tenant-id (utils/generate-uuid)
          _ (db/create-tenant! tenant-id "Test Org")
          user-id (utils/generate-uuid)
          _ (db/create-user! user-id tenant-id "no-logo-delete-console" "hash")
          vault-id (utils/generate-uuid)
          _ (db/create-vault! vault-id tenant-id "No Logo Delete Vault" "nologodelete.com" (utils/generate-uuid))
          request (-> (authenticated-request :delete (str "/console/vaults/" vault-id "/logo")
                                             tenant-id user-id)
                      (assoc :path-params {:id vault-id}))
          response (logo/delete-vault-logo request)]
      (is (= 200 (:status response)))
      (is (true? (get-in response [:body :success]))))))
