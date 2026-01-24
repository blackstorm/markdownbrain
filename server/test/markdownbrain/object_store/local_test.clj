(ns markdownbrain.object-store.local-test
  "Tests for LocalObjectStore implementation using real temp directory."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.java.io :as io]
            [markdownbrain.object-store :as store]
            [markdownbrain.object-store.local :as local])
  (:import [java.io File]
           [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]))

;; =============================================================================
;; Test Fixtures - Temp Directory Setup
;; =============================================================================

(def ^:dynamic *test-store* nil)
(def ^:dynamic *temp-dir* nil)

(defn- delete-recursively
  "Recursively delete a directory and all its contents."
  [^File f]
  (when (.exists f)
    (when (.isDirectory f)
      (doseq [child (.listFiles f)]
        (delete-recursively child)))
    (.delete f)))

(defn- create-temp-dir
  "Create a temporary directory for testing."
  []
  (let [temp-path (Files/createTempDirectory "markdownbrain-test-" (into-array FileAttribute []))]
    (.toFile temp-path)))

(defn with-temp-storage
  "Fixture that creates a temp directory and LocalObjectStore for each test."
  [f]
  (let [temp-dir (create-temp-dir)
        temp-path (.getAbsolutePath temp-dir)]
    (try
      ;; Mock the config to return our temp directory
      (with-redefs [markdownbrain.config/storage-config (constantly {:type :local
                                                                      :local-path temp-path})]
        (let [test-store (local/->LocalObjectStore temp-path)]
          (binding [*test-store* test-store
                    *temp-dir* temp-dir]
            (f))))
      (finally
        (delete-recursively temp-dir)))))

(use-fixtures :each with-temp-storage)

;; =============================================================================
;; Helper Functions
;; =============================================================================

(def test-vault-id "test-vault-123")
(def test-vault-id-2 "test-vault-456")

(defn- read-stream-to-string
  "Read an InputStream to a string."
  [input-stream]
  (slurp input-stream))

(defn- file-exists?
  "Check if a file exists at the expected path."
  [vault-id object-key]
  (let [vault-prefix (store/vault-prefix vault-id)
        file (io/file *temp-dir* vault-prefix object-key)]
    (.exists file)))

;; =============================================================================
;; put-object!* Tests
;; =============================================================================

(deftest test-put-object-string-content
  (testing "puts string content successfully"
    (let [result (store/put-object!* *test-store* test-vault-id "test.txt" "Hello, World!" "text/plain")]
      (is (some? result) "Should return result on success")
      (is (some? (:ETag result)) "Should have ETag")
      (is (= "text/plain" (:ContentType result)) "Should have correct ContentType")
      (is (file-exists? test-vault-id "test.txt") "File should exist on disk"))))

(deftest test-put-object-bytes-content
  (testing "puts byte array content successfully"
    (let [content (.getBytes "Binary content" "UTF-8")
          result (store/put-object!* *test-store* test-vault-id "binary.bin" content "application/octet-stream")]
      (is (some? result) "Should return result on success")
      (is (file-exists? test-vault-id "binary.bin") "File should exist on disk"))))

(deftest test-put-object-creates-directories
  (testing "creates nested directories automatically"
    (let [result (store/put-object!* *test-store* test-vault-id "assets/images/photo.png" "fake-image-data" "image/png")]
      (is (some? result) "Should return result on success")
      (is (file-exists? test-vault-id "assets/images/photo.png") "Nested file should exist"))))

(deftest test-put-object-overwrites-existing
  (testing "overwrites existing file"
    (store/put-object!* *test-store* test-vault-id "overwrite.txt" "Original" "text/plain")
    (store/put-object!* *test-store* test-vault-id "overwrite.txt" "Updated" "text/plain")
    
    (let [result (store/get-object* *test-store* test-vault-id "overwrite.txt")
          content (read-stream-to-string (:Body result))]
      (is (= "Updated" content) "Content should be updated"))))

;; =============================================================================
;; get-object* Tests
;; =============================================================================

(deftest test-get-object-success
  (testing "gets existing object successfully"
    (store/put-object!* *test-store* test-vault-id "readable.txt" "Test content" "text/plain")
    
    (let [result (store/get-object* *test-store* test-vault-id "readable.txt")]
      (is (some? result) "Should return result for existing object")
      (is (some? (:Body result)) "Should have Body")
      (is (= "Test content" (read-stream-to-string (:Body result))) "Content should match")
      (is (pos? (:ContentLength result)) "Should have ContentLength")
      (is (some? (:ContentType result)) "Should have ContentType")
      (is (some? (:LastModified result)) "Should have LastModified"))))

(deftest test-get-object-not-found
  (testing "returns nil for non-existent object"
    (let [result (store/get-object* *test-store* test-vault-id "non-existent.txt")]
      (is (nil? result) "Should return nil for missing object"))))

(deftest test-get-object-content-type-detection
  (testing "detects content type from extension"
    (doseq [[ext expected-type] [["png" "image/png"]
                                  ["jpg" "image/jpeg"]
                                  ["jpeg" "image/jpeg"]
                                  ["gif" "image/gif"]
                                  ["webp" "image/webp"]
                                  ["svg" "image/svg+xml"]
                                  ["bmp" "image/bmp"]
                                  ["ico" "image/x-icon"]
                                  ["pdf" "application/pdf"]
                                  ["md" "text/markdown"]
                                  ["json" "application/json"]
                                  ["ogg" "audio/ogg"]
                                  ["unknown" "application/octet-stream"]]]
      (let [filename (str "test." ext)]
        (store/put-object!* *test-store* test-vault-id filename "content" "text/plain")
        (let [result (store/get-object* *test-store* test-vault-id filename)]
          (is (= expected-type (:ContentType result)) 
              (str "Should detect " expected-type " for ." ext)))))))

;; =============================================================================
;; delete-object!* Tests
;; =============================================================================

(deftest test-delete-object-success
  (testing "deletes existing object successfully"
    (store/put-object!* *test-store* test-vault-id "to-delete.txt" "Delete me" "text/plain")
    (is (file-exists? test-vault-id "to-delete.txt") "File should exist before delete")
    
    (let [result (store/delete-object!* *test-store* test-vault-id "to-delete.txt")]
      (is (some? result) "Should return result")
      (is (:DeleteMarker result) "DeleteMarker should be true")
      (is (not (file-exists? test-vault-id "to-delete.txt")) "File should not exist after delete"))))

(deftest test-delete-object-not-found
  (testing "returns result with DeleteMarker false for non-existent object"
    (let [result (store/delete-object!* *test-store* test-vault-id "never-existed.txt")]
      (is (some? result) "Should return result")
      (is (false? (:DeleteMarker result)) "DeleteMarker should be false"))))

;; =============================================================================
;; head-object* Tests
;; =============================================================================

(deftest test-head-object-success
  (testing "returns metadata for existing object"
    (store/put-object!* *test-store* test-vault-id "head-test.txt" "Head content" "text/plain")
    
    (let [result (store/head-object* *test-store* test-vault-id "head-test.txt")]
      (is (some? result) "Should return metadata")
      (is (= 12 (:ContentLength result)) "ContentLength should match")
      (is (some? (:ContentType result)) "Should have ContentType")
      (is (some? (:LastModified result)) "Should have LastModified"))))

(deftest test-head-object-not-found
  (testing "returns nil for non-existent object"
    (let [result (store/head-object* *test-store* test-vault-id "no-head.txt")]
      (is (nil? result) "Should return nil for missing object"))))

;; =============================================================================
;; delete-vault-objects!* Tests
;; =============================================================================

(deftest test-delete-vault-objects-success
  (testing "deletes all objects for a vault"
    ;; Create multiple objects in the vault
    (store/put-object!* *test-store* test-vault-id "file1.txt" "Content 1" "text/plain")
    (store/put-object!* *test-store* test-vault-id "file2.txt" "Content 2" "text/plain")
    (store/put-object!* *test-store* test-vault-id "subdir/file3.txt" "Content 3" "text/plain")
    
    ;; Verify files exist
    (is (file-exists? test-vault-id "file1.txt"))
    (is (file-exists? test-vault-id "file2.txt"))
    (is (file-exists? test-vault-id "subdir/file3.txt"))
    
    ;; Delete all vault objects
    (store/delete-vault-objects!* *test-store* test-vault-id)
    
    ;; Verify all files are deleted
    (is (not (file-exists? test-vault-id "file1.txt")) "file1 should be deleted")
    (is (not (file-exists? test-vault-id "file2.txt")) "file2 should be deleted")
    (is (not (file-exists? test-vault-id "subdir/file3.txt")) "file3 should be deleted")))

(deftest test-delete-vault-objects-isolates-vaults
  (testing "only deletes objects from specified vault"
    ;; Create objects in two different vaults
    (store/put-object!* *test-store* test-vault-id "vault1-file.txt" "Vault 1" "text/plain")
    (store/put-object!* *test-store* test-vault-id-2 "vault2-file.txt" "Vault 2" "text/plain")
    
    ;; Delete only first vault
    (store/delete-vault-objects!* *test-store* test-vault-id)
    
    ;; Verify only first vault's files are deleted
    (is (not (file-exists? test-vault-id "vault1-file.txt")) "Vault 1 file should be deleted")
    (is (file-exists? test-vault-id-2 "vault2-file.txt") "Vault 2 file should still exist")))

(deftest test-delete-vault-objects-empty-vault
  (testing "handles empty vault gracefully"
    ;; Should not throw error for non-existent vault directory
    (store/delete-vault-objects!* *test-store* "non-existent-vault")
    (is true "Should complete without error")))

;; =============================================================================
;; public-url* Tests
;; =============================================================================

(deftest test-public-url-returns-storage-path
  (testing "local storage returns /storage/{object-key} path"
    (let [result (store/public-url* *test-store* test-vault-id "some-file.txt")]
      (is (= "/storage/some-file.txt" result) "Local storage should return /storage/ path")))
  
  (testing "handles nested paths"
    (let [result (store/public-url* *test-store* test-vault-id "assets/image.png")]
      (is (= "/storage/assets/image.png" result))))
  
  (testing "handles logo paths"
    (let [result (store/public-url* *test-store* test-vault-id "site/logo/abc123.png")]
      (is (= "/storage/site/logo/abc123.png" result)))))

;; =============================================================================
;; Factory Function Tests
;; =============================================================================

(deftest test-create-local-store-success
  (testing "creates store with valid directory"
    (let [temp-dir (create-temp-dir)
          temp-path (.getAbsolutePath temp-dir)]
      (try
        (with-redefs [markdownbrain.config/storage-config (constantly {:type :local
                                                                        :local-path temp-path})]
          (let [store (local/create-local-store)]
            (is (some? store) "Should create store")
            (is (instance? markdownbrain.object_store.local.LocalObjectStore store))))
        (finally
          (delete-recursively temp-dir))))))

(deftest test-create-local-store-creates-directory
  (testing "creates directory if it doesn't exist"
    (let [temp-dir (create-temp-dir)
          new-path (str (.getAbsolutePath temp-dir) "/new-storage-dir")]
      (try
        (with-redefs [markdownbrain.config/storage-config (constantly {:type :local
                                                                        :local-path new-path})]
          (let [store (local/create-local-store)]
            (is (some? store) "Should create store")
            (is (.exists (io/file new-path)) "Directory should be created")))
        (finally
          (delete-recursively temp-dir))))))

;; =============================================================================
;; Integration Test - Full CRUD Cycle
;; =============================================================================

(deftest test-full-crud-cycle
  (testing "complete create, read, update, delete cycle"
    (let [object-key "crud-test.txt"]
      ;; Create
      (let [create-result (store/put-object!* *test-store* test-vault-id object-key "Initial content" "text/plain")]
        (is (some? create-result) "Create should succeed"))
      
      ;; Read
      (let [read-result (store/get-object* *test-store* test-vault-id object-key)
            content (read-stream-to-string (:Body read-result))]
        (is (= "Initial content" content) "Read should return initial content"))
      
      ;; Update
      (let [update-result (store/put-object!* *test-store* test-vault-id object-key "Updated content" "text/plain")]
        (is (some? update-result) "Update should succeed"))
      
      ;; Read again to verify update
      (let [read-result (store/get-object* *test-store* test-vault-id object-key)
            content (read-stream-to-string (:Body read-result))]
        (is (= "Updated content" content) "Read should return updated content"))
      
      ;; Delete
      (let [delete-result (store/delete-object!* *test-store* test-vault-id object-key)]
        (is (:DeleteMarker delete-result) "Delete should succeed"))
      
      ;; Verify deleted
      (let [read-result (store/get-object* *test-store* test-vault-id object-key)]
        (is (nil? read-result) "Read after delete should return nil")))))
