(ns markdownbrain.object-store-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.string :as str]
            [markdownbrain.object-store :as store]
            [markdownbrain.object-store.s3 :as s3]))

;; =============================================================================
;; Pure Function Tests (no mocking needed)
;; =============================================================================

(deftest test-parse-endpoint
  (testing "parses http URL with port"
    (is (= {:protocol :http :hostname "localhost" :port 9000}
           (s3/parse-endpoint "http://localhost:9000"))))
  
  (testing "parses https URL with port"
    (is (= {:protocol :https :hostname "minio.example.com" :port 443}
           (s3/parse-endpoint "https://minio.example.com:443"))))
  
  (testing "defaults to port 9000 when no port specified (http)"
    (is (= {:protocol :http :hostname "localhost" :port 9000}
           (s3/parse-endpoint "http://localhost"))))

  (testing "defaults to port 443 when no port specified (https)"
    (is (= {:protocol :https :hostname "s3.amazonaws.com" :port 443}
           (s3/parse-endpoint "https://s3.amazonaws.com"))))
  
  (testing "handles URL with trailing path"
    (is (= {:protocol :http :hostname "storage.local" :port 9000}
           (s3/parse-endpoint "http://storage.local:9000/path/ignored"))))

  (testing "defaults to http:// when scheme is missing"
    (is (= {:protocol :http :hostname "storage.local" :port 9000}
           (s3/parse-endpoint "storage.local:9000/path/ignored")))))

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
  (testing "generates key from client_id with extension"
    (is (= "assets/abc123.png"
           (store/asset-object-key "abc123" "png"))))

  (testing "generates key from client_id without extension"
    (is (= "assets/abc123"
           (store/asset-object-key "abc123"))))

  (testing "omits extension when it sanitizes to empty"
    (is (= "assets/abc123"
           (store/asset-object-key "abc123" ".."))))
  
  (testing "handles UUID format with extension"
    (is (= "assets/550e8400-e29b-41d4-a716-446655440000.webp"
           (store/asset-object-key "550e8400-e29b-41d4-a716-446655440000" "webp"))))
  
  (testing "handles various extensions"
    (is (= "assets/client-id.jpg" (store/asset-object-key "client-id" "jpg")))
    (is (= "assets/client-id.gif" (store/asset-object-key "client-id" "gif")))
    (is (= "assets/client-id.svg" (store/asset-object-key "client-id" "svg")))
    (is (= "assets/client-id.pdf" (store/asset-object-key "client-id" "pdf")))))

(deftest test-extension-from-path
  (testing "extracts extension from typical paths"
    (is (= "png" (store/extension-from-path "assets/photo.png")))
    (is (= "png" (store/extension-from-path "assets/photo.PNG")))
    (is (= "gz" (store/extension-from-path "backup/archive.tar.gz"))))

  (testing "returns nil when no extension"
    (is (nil? (store/extension-from-path "assets/README")))
    (is (nil? (store/extension-from-path "assets/.gitignore"))))

  (testing "sanitizes extension"
    (is (= "x-icon" (store/extension-from-path "assets/favicon.x-icon")))
    (is (= "bin" (or (store/extension-from-path "assets/file.%$#") "bin")))))

(deftest test-logo-object-key
  (testing "generates key with content hash and extension"
    (is (= "site/logo/abc123def456.png"
           (store/logo-object-key "abc123def456" "png"))))
  
  (testing "handles various extensions"
    (is (= "site/logo/hash123.jpg" (store/logo-object-key "hash123" "jpg")))
    (is (= "site/logo/hash123.gif" (store/logo-object-key "hash123" "gif")))
    (is (= "site/logo/hash123.webp" (store/logo-object-key "hash123" "webp")))
    (is (= "site/logo/hash123.svg" (store/logo-object-key "hash123" "svg"))))
  
  (testing "handles full SHA256 hash"
    (is (= "site/logo/e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855.png"
           (store/logo-object-key "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855" "png")))))

;; =============================================================================
;; S3 Client Mock Tests (using with-redefs)
;; =============================================================================

;; Note: The following tests would require mocking the AWS client.
;; For now, we test the pure functions above. Integration tests with 
;; actual S3/RustFS can be added later with test containers or a 
;; dedicated test S3 endpoint.

;; =============================================================================
;; Logo Thumbnail Cache Tests
;; =============================================================================
