(ns markdownbrain.object-store-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.string :as str]
            [markdownbrain.object-store :as store]
            [markdownbrain.object-store.s3 :as s3]))

;; Clear logo thumbnail cache before each test to prevent state leakage
(defn clear-cache-fixture [f]
  (store/clear-logo-cache!)
  (f))

(use-fixtures :each clear-cache-fixture)

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
  (testing "generates key from client_id with extension"
    (is (= "assets/abc123.png"
           (store/asset-object-key "abc123" "png"))))
  
  (testing "handles UUID format with extension"
    (is (= "assets/550e8400-e29b-41d4-a716-446655440000.webp"
           (store/asset-object-key "550e8400-e29b-41d4-a716-446655440000" "webp"))))
  
  (testing "handles various extensions"
    (is (= "assets/client-id.jpg" (store/asset-object-key "client-id" "jpg")))
    (is (= "assets/client-id.gif" (store/asset-object-key "client-id" "gif")))
    (is (= "assets/client-id.svg" (store/asset-object-key "client-id" "svg")))
    (is (= "assets/client-id.pdf" (store/asset-object-key "client-id" "pdf")))))

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

(deftest test-content-type->extension
  (testing "maps common image content types"
    (is (= "png" (store/content-type->extension "image/png")))
    (is (= "jpg" (store/content-type->extension "image/jpeg")))
    (is (= "gif" (store/content-type->extension "image/gif")))
    (is (= "webp" (store/content-type->extension "image/webp")))
    (is (= "svg" (store/content-type->extension "image/svg+xml"))))
  
  (testing "maps document content types"
    (is (= "pdf" (store/content-type->extension "application/pdf")))
    (is (= "json" (store/content-type->extension "application/json"))))
  
  (testing "defaults to bin for unknown types"
    (is (= "bin" (store/content-type->extension "application/octet-stream")))
    (is (= "bin" (store/content-type->extension "unknown/type")))
    (is (= "bin" (store/content-type->extension nil)))))

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

(deftest test-thumbnail-object-key-for-logo-simple
  (testing "Generate thumbnail key for simple filename"
    (is (= "site/logo/abc123@256x256.png"
           (store/thumbnail-object-key-for-logo "site/logo/abc123.png" 256))
        "Should insert @256x256 before extension")

    (is (= "site/logo/abc123@512x512.jpg"
           (store/thumbnail-object-key-for-logo "site/logo/abc123.jpg" 512))
        "Should work with JPEG extension")

    (is (= "site/logo/abc123@32x32.webp"
           (store/thumbnail-object-key-for-logo "site/logo/abc123.webp" 32))
        "Should work with WebP extension")))

(deftest test-thumbnail-object-key-for-logo-multi-segment
  (testing "Generate thumbnail key for filename with multiple dots"
    (is (= "site/logo/v2.0@256x256.png"
           (store/thumbnail-object-key-for-logo "site/logo/v2.0.png" 256))
        "Should handle version numbers with dots")

    (is (= "site/logo/my.logo.file@128x128.png"
           (store/thumbnail-object-key-for-logo "site/logo/my.logo.file.png" 128))
        "Should handle multiple dots in filename")

    (is (= "site/logo/test.image.final@64x64.jpg"
           (store/thumbnail-object-key-for-logo "site/logo/test.image.final.jpg" 64))
        "Should handle complex filenames")))

(deftest test-thumbnail-object-key-for-logo-all-sizes
  (testing "Generate thumbnail keys for all supported sizes"
    (let [base-key "site/logo/test.png"]
      (doseq [size [512 256 128 64 32]]
        (let [thumb-key (store/thumbnail-object-key-for-logo base-key size)]
          (is (str/includes? thumb-key (str size "x" size))
              (str "Should contain " size "x" size))
          (is (str/ends-with? thumb-key ".png")
              "Should end with .png")
          (is (str/includes? thumb-key "@")
              "Should contain @ separator"))))))

(deftest test-clear-logo-cache-all
  (testing "Clear all logo cache"
    ;; Note: We can't directly manipulate the private cache atom,
    ;; but we can verify that calling clear-logo-cache! doesn't throw
    (store/clear-logo-cache!)
    (is true "Clear all cache should not throw")))

(deftest test-clear-logo-cache-specific
  (testing "Clear cache for specific logo"
    ;; Note: We can't directly manipulate the private cache atom,
    ;; but we can verify that calling clear-logo-cache! doesn't throw
    (store/clear-logo-cache! "site/logo/a.png")
    (is true "Clear specific logo cache should not throw")))

(deftest test-get-available-thumbnail-size-cache-hit
  (testing "Get available thumbnail size from cache"
    ;; Note: Can't test cache directly without accessing private atom,
    ;; but we can test the get-available-thumbnail-size function with mocking
    (with-redefs [store/object-exists? (fn [_ _] false)]

      ;; When no objects exist, should return nil
      (let [result (store/get-available-thumbnail-size "vault-id" "site/logo/test.png" 256)]
        (is (nil? result)
            "Should return nil when no thumbnail exists")))))

(deftest test-get-available-thumbnail-size-cache-miss-storage-hit
  (testing "Get available thumbnail size from storage when not in cache"
    (with-redefs [store/object-exists? (fn [vault-id key]
                                         (and (= vault-id "vault-id")
                                              (= key "site/logo/test@256x256.png")))]

      ;; Request 256 - should check storage and find it
      (let [result (store/get-available-thumbnail-size "vault-id" "site/logo/test.png" 256)]
        (is (= 256 result)
            "Should return 256 from storage")))))

(deftest test-get-available-thumbnail-size-fallback-to-smaller
  (testing "Fallback to smaller size when requested size not available"
    (with-redefs [store/object-exists? (fn [vault-id key]
                                         (cond
                                           ;; Only 256 exists
                                           (= key "site/logo/test@256x256.png") true
                                           ;; Others don't exist
                                           :else false))]

      ;; Request 512 - should fall back to 256
      (let [result (store/get-available-thumbnail-size "vault-id" "site/logo/test.png" 512)]
        (is (= 256 result)
            "Should return 256 as fallback")))))

(deftest test-get-available-thumbnail-size-no-match
  (testing "Return nil when no thumbnail available"
    (with-redefs [store/object-exists? (fn [_ _] false)]

      ;; Request 512 - nothing available
      (let [result (store/get-available-thumbnail-size "vault-id" "site/logo/test.png" 512)]
        (is (nil? result)
            "Should return nil when no thumbnail available")))))

(deftest test-get-available-thumbnail-size-cascade-through-sizes
  (testing "Cascade through sizes in descending order"
    (let [checked-sizes (atom [])]
      (with-redefs [store/object-exists? (fn [vault-id key]
                                           (when-let [[_ size] (re-find #"(\d+)x\d+" key)]
                                             (swap! checked-sizes conj (Integer/parseInt size))
                                             ;; Only 64 exists
                                             (= size "64")))]

        ;; Request 512 - should check 512, 256, 128, then find 64
        (let [result (store/get-available-thumbnail-size "vault-id" "site/logo/test.png" 512)]
          (is (= 64 result)
              "Should find 64 after checking larger sizes")

          (is (= [512 256 128 64] @checked-sizes)
              "Should have checked sizes in descending order"))))))

(deftest test-get-available-thumbnail-size-respects-requested-limit
  (testing "Only check sizes <= requested size"
    (let [checked-sizes (atom [])]
      (with-redefs [store/object-exists? (fn [vault-id key]
                                           (when-let [[_ size] (re-find #"(\d+)x\d+" key)]
                                             (swap! checked-sizes conj (Integer/parseInt size))
                                             false))]

        ;; Request 64 - should only check sizes <= 64
        (let [result (store/get-available-thumbnail-size "vault-id" "site/logo/test.png" 64)]
          (is (nil? result))
          (is (= [64 32] @checked-sizes)
              "Should only check 64 and 32 (both <= 64)"))))))

;; TODO: Add integration tests when RustFS is available in test environment
;; - test-put-object!
;; - test-get-object
;; - test-delete-object!
;; - test-ensure-bucket!
;; - test-object-exists?
