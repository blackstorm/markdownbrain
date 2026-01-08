(ns markdownbrain.object-store.s3-test
  "Tests for S3ObjectStore implementation with mocked AWS client."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [markdownbrain.object-store :as store]
            [markdownbrain.object-store.s3 :as s3]
            [markdownbrain.config :as config]
            [cognitect.aws.client.api :as aws]))

;; =============================================================================
;; Test Fixtures and Helpers
;; =============================================================================

(def test-vault-id "test-vault-123")
(def test-bucket "test-bucket")
(def test-endpoint "http://localhost:9000")
(def test-public-url "https://s3.example.com")

(def mock-s3-config
  {:endpoint test-endpoint
   :access-key "test-access-key"
   :secret-key "test-secret-key"
   :region "us-east-1"
   :bucket test-bucket
   :public-url test-public-url})

;; Track aws/invoke calls for verification
(def ^:dynamic *invoke-calls* nil)

(defn with-mock-config
  "Fixture that mocks S3 config."
  [f]
  (with-redefs [config/s3-config (constantly mock-s3-config)]
    (f)))

(use-fixtures :each with-mock-config)

;; =============================================================================
;; Helper to create mock store with captured invocations
;; =============================================================================

(defn create-mock-store
  "Create an S3ObjectStore with a mock client that records invocations."
  [invoke-fn]
  (let [mock-client (reify Object)]
    (with-redefs [aws/invoke (fn [client request]
                               (invoke-fn request))]
      (s3/->S3ObjectStore mock-client))))

(defn make-invoke-handler
  "Create an invoke handler that returns specified responses for operations."
  [responses]
  (fn [request]
    (let [op (:op request)
          response (get responses op {})]
      (if (fn? response)
        (response request)
        response))))

;; =============================================================================
;; parse-endpoint Tests (already in object_store_test.clj, but adding more)
;; =============================================================================

(deftest test-parse-endpoint-edge-cases
  (testing "handles https with custom port"
    (is (= {:hostname "s3.amazonaws.com" :port 443}
           (s3/parse-endpoint "https://s3.amazonaws.com:443"))))
  
  (testing "handles IP address"
    (is (= {:hostname "192.168.1.100" :port 9000}
           (s3/parse-endpoint "http://192.168.1.100:9000"))))
  
  (testing "handles subdomain"
    (is (= {:hostname "minio.internal.example.com" :port 9000}
           (s3/parse-endpoint "http://minio.internal.example.com:9000")))))

;; =============================================================================
;; put-object!* Tests
;; =============================================================================

(deftest test-put-object-success
  (testing "puts object with string content"
    (let [captured (atom nil)
          responses {:PutObject {:ETag "\"abc123\""}}
          store (create-mock-store (fn [req]
                                     (reset! captured req)
                                     (get responses (:op req))))]
      (with-redefs [aws/invoke (fn [_ req]
                                 (reset! captured req)
                                 (get responses (:op req)))]
        (let [result (store/put-object!* store test-vault-id "test.txt" "Hello" "text/plain")]
          (is (some? result) "Should return result")
          (is (= :PutObject (:op @captured)) "Should call PutObject")
          (is (= test-bucket (get-in @captured [:request :Bucket])) "Should use correct bucket")
          (is (= (str (store/vault-prefix test-vault-id) "test.txt")
                 (get-in @captured [:request :Key])) "Should use correct key")
          (is (= "text/plain" (get-in @captured [:request :ContentType])) "Should set content type"))))))

(deftest test-put-object-with-bytes
  (testing "puts object with byte array content"
    (let [captured (atom nil)
          responses {:PutObject {:ETag "\"def456\""}}
          content (.getBytes "Binary data" "UTF-8")]
      (let [store (s3/->S3ObjectStore :mock-client)]
        (with-redefs [aws/invoke (fn [_ req]
                                   (reset! captured req)
                                   {:ETag "\"def456\""})]
          (let [result (store/put-object!* store test-vault-id "data.bin" content "application/octet-stream")]
            (is (some? result))
            (is (bytes? (get-in @captured [:request :Body])) "Body should be bytes")))))))

(deftest test-put-object-failure
  (testing "returns nil on S3 error"
    (let [store (s3/->S3ObjectStore :mock-client)]
      (with-redefs [aws/invoke (fn [_ _]
                                 {:cognitect.anomalies/category :cognitect.anomalies/fault
                                  :cognitect.anomalies/message "Access Denied"})]
        (let [result (store/put-object!* store test-vault-id "fail.txt" "content" "text/plain")]
          (is (nil? result) "Should return nil on error"))))))

;; =============================================================================
;; get-object* Tests
;; =============================================================================

(deftest test-get-object-success
  (testing "gets existing object"
    (let [captured (atom nil)
          mock-body (java.io.ByteArrayInputStream. (.getBytes "File content" "UTF-8"))
          store (s3/->S3ObjectStore :mock-client)]
      (with-redefs [aws/invoke (fn [_ req]
                                 (reset! captured req)
                                 {:Body mock-body
                                  :ContentLength 12
                                  :ContentType "text/plain"})]
        (let [result (store/get-object* store test-vault-id "existing.txt")]
          (is (some? result) "Should return result")
          (is (= :GetObject (:op @captured)) "Should call GetObject")
          (is (some? (:Body result)) "Should have Body")
          (is (= 12 (:ContentLength result)) "Should have ContentLength"))))))

(deftest test-get-object-not-found
  (testing "returns nil for non-existent object"
    (let [store (s3/->S3ObjectStore :mock-client)]
      (with-redefs [aws/invoke (fn [_ _]
                                 {:cognitect.anomalies/category :cognitect.anomalies/not-found})]
        (let [result (store/get-object* store test-vault-id "missing.txt")]
          (is (nil? result) "Should return nil for missing object"))))))

;; =============================================================================
;; delete-object!* Tests
;; =============================================================================

(deftest test-delete-object-success
  (testing "deletes object successfully"
    (let [captured (atom nil)
          store (s3/->S3ObjectStore :mock-client)]
      (with-redefs [aws/invoke (fn [_ req]
                                 (reset! captured req)
                                 {:DeleteMarker true})]
        (let [result (store/delete-object!* store test-vault-id "to-delete.txt")]
          (is (some? result) "Should return result")
          (is (= :DeleteObject (:op @captured)) "Should call DeleteObject")
          (is (= (str (store/vault-prefix test-vault-id) "to-delete.txt")
                 (get-in @captured [:request :Key])) "Should use correct key"))))))

(deftest test-delete-object-failure
  (testing "returns nil on S3 error"
    (let [store (s3/->S3ObjectStore :mock-client)]
      (with-redefs [aws/invoke (fn [_ _]
                                 {:cognitect.anomalies/category :cognitect.anomalies/fault})]
        (let [result (store/delete-object!* store test-vault-id "fail.txt")]
          (is (nil? result) "Should return nil on error"))))))

;; =============================================================================
;; head-object* Tests
;; =============================================================================

(deftest test-head-object-success
  (testing "returns metadata for existing object"
    (let [captured (atom nil)
          store (s3/->S3ObjectStore :mock-client)]
      (with-redefs [aws/invoke (fn [_ req]
                                 (reset! captured req)
                                 {:ContentLength 1024
                                  :ContentType "image/png"
                                  :ETag "\"abc123\""})]
        (let [result (store/head-object* store test-vault-id "image.png")]
          (is (some? result) "Should return metadata")
          (is (= :HeadObject (:op @captured)) "Should call HeadObject")
          (is (= 1024 (:ContentLength result)) "Should have ContentLength")
          (is (= "image/png" (:ContentType result)) "Should have ContentType"))))))

(deftest test-head-object-not-found
  (testing "returns nil for non-existent object"
    (let [store (s3/->S3ObjectStore :mock-client)]
      (with-redefs [aws/invoke (fn [_ _]
                                 {:cognitect.anomalies/category :cognitect.anomalies/not-found})]
        (let [result (store/head-object* store test-vault-id "missing.png")]
          (is (nil? result) "Should return nil"))))))

;; =============================================================================
;; delete-vault-objects!* Tests
;; =============================================================================

(deftest test-delete-vault-objects-success
  (testing "deletes all objects with vault prefix"
    (let [delete-calls (atom [])
          list-call-count (atom 0)
          store (s3/->S3ObjectStore :mock-client)]
      (with-redefs [aws/invoke (fn [_ req]
                                 (case (:op req)
                                   :ListObjectsV2
                                   (do
                                     (swap! list-call-count inc)
                                     {:Contents [{:Key "testvault123/file1.txt"}
                                                 {:Key "testvault123/file2.txt"}]
                                      :IsTruncated false})
                                   :DeleteObject
                                   (do
                                     (swap! delete-calls conj (get-in req [:request :Key]))
                                     {:DeleteMarker true})
                                   {}))]
        (store/delete-vault-objects!* store test-vault-id)
        (is (= 1 @list-call-count) "Should call ListObjectsV2 once")
        (is (= 2 (count @delete-calls)) "Should delete 2 objects")
        (is (some #(= "testvault123/file1.txt" %) @delete-calls))
        (is (some #(= "testvault123/file2.txt" %) @delete-calls))))))

(deftest test-delete-vault-objects-pagination
  (testing "handles paginated results"
    (let [list-call-count (atom 0)
          delete-calls (atom [])
          store (s3/->S3ObjectStore :mock-client)]
      (with-redefs [aws/invoke (fn [_ req]
                                 (case (:op req)
                                   :ListObjectsV2
                                   (let [call-num (swap! list-call-count inc)]
                                     (if (= 1 call-num)
                                       {:Contents [{:Key "testvault123/file1.txt"}]
                                        :IsTruncated true}
                                       {:Contents [{:Key "testvault123/file2.txt"}]
                                        :IsTruncated false}))
                                   :DeleteObject
                                   (do
                                     (swap! delete-calls conj (get-in req [:request :Key]))
                                     {:DeleteMarker true})
                                   {}))]
        (store/delete-vault-objects!* store test-vault-id)
        (is (= 2 @list-call-count) "Should call ListObjectsV2 twice for pagination")
        (is (= 2 (count @delete-calls)) "Should delete 2 objects total")))))

(deftest test-delete-vault-objects-empty
  (testing "handles empty vault"
    (let [list-called (atom false)
          store (s3/->S3ObjectStore :mock-client)]
      (with-redefs [aws/invoke (fn [_ req]
                                 (when (= :ListObjectsV2 (:op req))
                                   (reset! list-called true))
                                 {:Contents nil :IsTruncated false})]
        (store/delete-vault-objects!* store test-vault-id)
        (is @list-called "Should call ListObjectsV2")))))

;; =============================================================================
;; public-url* Tests
;; =============================================================================

(deftest test-public-url-success
  (testing "generates correct public URL"
    (let [store (s3/->S3ObjectStore :mock-client)]
      (let [result (store/public-url* store test-vault-id "assets/image.png")]
        (is (= "https://s3.example.com/test-bucket/testvault123/assets/image.png" result))))))

(deftest test-public-url-strips-trailing-slash
  (testing "strips trailing slash from public URL"
    (with-redefs [config/s3-config (constantly (assoc mock-s3-config :public-url "https://s3.example.com/"))]
      (let [store (s3/->S3ObjectStore :mock-client)]
        (let [result (store/public-url* store test-vault-id "file.txt")]
          (is (= "https://s3.example.com/test-bucket/testvault123/file.txt" result)))))))

(deftest test-public-url-nil-when-not-configured
  (testing "returns nil when public-url not configured"
    (with-redefs [config/s3-config (constantly (dissoc mock-s3-config :public-url))]
      (let [store (s3/->S3ObjectStore :mock-client)]
        (let [result (store/public-url* store test-vault-id "file.txt")]
          (is (nil? result) "Should return nil when public-url not set"))))))

;; =============================================================================
;; Factory Function Tests
;; =============================================================================

(deftest test-create-s3-store-missing-endpoint
  (testing "throws when endpoint not configured"
    (with-redefs [config/s3-config (constantly (dissoc mock-s3-config :endpoint))]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"S3_ENDPOINT is required"
                            (s3/create-s3-store))))))

;; =============================================================================
;; Integration Test - Vault Prefix Correctness
;; =============================================================================

(deftest test-vault-prefix-in-all-operations
  (testing "all operations use correct vault prefix"
    (let [captured-keys (atom [])
          store (s3/->S3ObjectStore :mock-client)]
      (with-redefs [aws/invoke (fn [_ req]
                                 (when-let [key (get-in req [:request :Key])]
                                   (swap! captured-keys conj key))
                                 (case (:op req)
                                   :PutObject {:ETag "\"123\""}
                                   :GetObject {:Body nil}
                                   :HeadObject {:ContentLength 0}
                                   :DeleteObject {:DeleteMarker true}
                                   {}))]
        ;; Test each operation
        (store/put-object!* store test-vault-id "put.txt" "content" "text/plain")
        (store/get-object* store test-vault-id "get.txt")
        (store/head-object* store test-vault-id "head.txt")
        (store/delete-object!* store test-vault-id "delete.txt")
        
        ;; All keys should have vault prefix
        (let [expected-prefix (store/vault-prefix test-vault-id)]
          (doseq [key @captured-keys]
            (is (clojure.string/starts-with? key expected-prefix)
                (str "Key should start with vault prefix: " key))))))))
