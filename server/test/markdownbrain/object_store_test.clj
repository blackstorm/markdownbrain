(ns markdownbrain.object-store-test
  (:require [clojure.test :refer [deftest is testing]]
            [markdownbrain.object-store :as store]
            [markdownbrain.object-store.s3 :as s3]))

;; =============================================================================
;; Pure Function Tests (no mocking needed)
;; =============================================================================

(deftest test-parse-endpoint
  (testing "parses http URL with port"
    (is (= {:hostname "localhost" :port 9000}
           (s3/parse-endpoint "http://localhost:9000"))))
  
  (testing "parses https URL with port"
    (is (= {:hostname "minio.example.com" :port 443}
           (s3/parse-endpoint "https://minio.example.com:443"))))
  
  (testing "defaults to port 9000 when no port specified"
    (is (= {:hostname "localhost" :port 9000}
           (s3/parse-endpoint "http://localhost"))))
  
  (testing "handles URL with trailing path"
    (is (= {:hostname "storage.local" :port 9000}
           (s3/parse-endpoint "http://storage.local:9000/path/ignored")))))

(deftest test-normalize-path
  (testing "nil input returns nil"
    (is (nil? (store/normalize-path nil))))
  
  (testing "removes leading slash"
    (is (= "images/photo.png" (store/normalize-path "/images/photo.png"))))
  
  (testing "converts backslashes to forward slashes"
    (is (= "images/subfolder/photo.png" (store/normalize-path "images\\subfolder\\photo.png"))))
  
  (testing "removes parent directory traversal (..) for security"
    (is (= "images/photo.png" (store/normalize-path "../images/photo.png")))
    (is (= "images/photo.png" (store/normalize-path "images/../images/photo.png")))
    (is (= "photo.png" (store/normalize-path "../../photo.png"))))
  
  (testing "removes dot segments (./)"
    (is (= "images/photo.png" (store/normalize-path "./images/photo.png")))
    (is (= "images/photo.png" (store/normalize-path "images/./photo.png"))))
  
  (testing "removes multiple consecutive slashes"
    (is (= "images/photo.png" (store/normalize-path "images//photo.png")))
    (is (= "a/b/c.png" (store/normalize-path "a///b//c.png"))))
  
  (testing "handles mixed cases"
    (is (= "assets/images/logo.svg" 
           (store/normalize-path "../assets\\images/./logo.svg")))))

(deftest test-vault-prefix
  (testing "generates vault prefix without dashes"
    (is (= "abc123def456/" 
           (store/vault-prefix "abc-123-def-456"))))
  
  (testing "handles vault-id without dashes"
    (is (= "abc123/" 
           (store/vault-prefix "abc123"))))
  
  (testing "handles UUID format"
    (is (= "550e8400e29b41d4a716446655440000/"
           (store/vault-prefix "550e8400-e29b-41d4-a716-446655440000")))))

(deftest test-asset-object-key
  (testing "generates key from client_id"
    (is (= "assets/abc123"
           (store/asset-object-key "abc123"))))
  
  (testing "handles UUID format"
    (is (= "assets/550e8400-e29b-41d4-a716-446655440000"
           (store/asset-object-key "550e8400-e29b-41d4-a716-446655440000")))))

(deftest test-logo-object-key
  (testing "prefixes with site/logo/"
    (is (= "site/logo/logo.png"
           (store/logo-object-key "logo.png"))))
  
  (testing "handles complex filenames"
    (is (= "site/logo/my-company-logo-2024.svg"
           (store/logo-object-key "my-company-logo-2024.svg")))))

;; =============================================================================
;; S3 Client Mock Tests (using with-redefs)
;; =============================================================================

;; Note: The following tests would require mocking the AWS client.
;; For now, we test the pure functions above. Integration tests with 
;; actual S3/RustFS can be added later with test containers or a 
;; dedicated test S3 endpoint.

;; TODO: Add integration tests when RustFS is available in test environment
;; - test-put-object!
;; - test-get-object
;; - test-delete-object!
;; - test-ensure-bucket!
;; - test-object-exists?
