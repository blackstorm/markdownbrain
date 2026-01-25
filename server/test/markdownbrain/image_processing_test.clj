(ns markdownbrain.image-processing-test
  "Comprehensive tests for image processing functionality.
   Tests thumbnail generation, dimension detection, and format handling."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [markdownbrain.image-processing :as img])
  (:import [java.nio.file Files]))

;; Force headless mode during tests to avoid JVM crashes in environments without AWT.
(System/setProperty "java.awt.headless" "true")

(def ^:private fixtures-dir
  (io/file "test/fixtures/images"))

(defn- fixture-bytes [name]
  (Files/readAllBytes (.toPath (io/file fixtures-dir name))))

;; =============================================================================
;; get-image-dimensions Tests
;; =============================================================================

(deftest test-get-image-dimensions-png
  (testing "PNG image dimensions - various sizes"
    (let [test-cases [["png_32x32.png" [32 32]]
                      ["png_100x100.png" [100 100]]
                      ["png_512x512.png" [512 512]]
                      ["png_1024x768.png" [1024 768]]
                      ["png_768x1024.png" [768 1024]]]]
      (doseq [[filename expected] test-cases]
        (let [bytes (fixture-bytes filename)
              dims (img/get-image-dimensions bytes)]
          (is (= expected dims)
              (str "PNG dimensions should be " expected)))))))

(deftest test-get-image-dimensions-invalid
  (testing "Invalid image data returns nil"
    (let [invalid-bytes (fixture-bytes "invalid.bin")]
      (is (nil? (img/get-image-dimensions invalid-bytes))
          "Invalid image bytes should return nil"))))

(deftest test-get-image-dimensions-empty
  (testing "Empty byte array returns nil"
    (is (nil? (img/get-image-dimensions (byte-array 0)))
        "Empty byte array should return nil")))

(deftest test-get-image-dimensions-nil
  (testing "nil input returns nil"
    (is (nil? (img/get-image-dimensions nil))
        "nil input should return nil")))

;; =============================================================================
;; can-generate-thumbnail? Tests
;; =============================================================================

(deftest test-can-generate-thumbnail-square
  (testing "Square images can generate thumbnails"
    (is (true? (img/can-generate-thumbnail? [512 512] 256))
        "512x512 can generate 256x256")
    (is (true? (img/can-generate-thumbnail? [256 256] 256))
        "256x256 can generate 256x256 (exact)")))

(deftest test-can-generate-thumbnail-small
  (testing "Small images cannot generate larger thumbnails"
    (is (false? (img/can-generate-thumbnail? [32 32] 64))
        "32x32 cannot generate 64x64")
    (is (false? (img/can-generate-thumbnail? [100 100] 512))
        "100x100 cannot generate 512x512")))

(deftest test-can-generate-thumbnail-landscape
  (testing "Landscape images (width > height)"
    ;; 800x600 can generate 512x512 (min dimension is 600 >= 512)
    (is (true? (img/can-generate-thumbnail? [800 600] 512))
        "800x600 can generate 512x512")
    ;; 800x400 cannot generate 512x512 (min dimension is 400 < 512)
    (is (false? (img/can-generate-thumbnail? [800 400] 512))
        "800x400 cannot generate 512x512")))

(deftest test-can-generate-thumbnail-portrait
  (testing "Portrait images (height > width)"
    ;; 600x800 can generate 512x512 (min dimension is 600 >= 512)
    (is (true? (img/can-generate-thumbnail? [600 800] 512))
        "600x800 can generate 512x512")
    ;; 400x800 cannot generate 512x512 (min dimension is 400 < 512)
    (is (false? (img/can-generate-thumbnail? [400 800] 512))
        "400x800 cannot generate 512x512")))

(deftest test-can-generate-thumbnail-nil-dimensions
  (testing "nil dimensions return false"
    (is (false? (img/can-generate-thumbnail? nil 256))
        "nil dimensions should return false")))

;; =============================================================================
;; Favicon Generation Tests (Simplified from multi-size thumbnails)
;; =============================================================================


(deftest test-generate-favicon-large-image
  (testing "Large image generates favicon successfully"
    (let [bytes (fixture-bytes "png_100x100.png")
          content-type "image/png"
          content-hash "favicon123"
          extension "png"
          favicon (img/generate-favicon bytes content-type content-hash extension)]
      (is (some? favicon) "Favicon should be generated")
      (is (contains? favicon :object-key) "Favicon should have object-key")
      (is (contains? favicon :bytes) "Favicon should have bytes")
      (is (bytes? (:bytes favicon)) "Favicon bytes should be byte array")
      (is (str/includes? (:object-key favicon) ".favicon.")
          "Object key should contain .favicon.")
      (is (= [32 32] (img/get-image-dimensions (:bytes favicon)))
          "Generated favicon should be 32x32"))))

(deftest test-generate-favicon-small-image
  (testing "Small image (< 32x32) cannot generate favicon"
    (let [bytes (fixture-bytes "png_16x16.png")
          content-type "image/png"
          content-hash "smallfavicon"
          extension "png"
          favicon (img/generate-favicon bytes content-type content-hash extension)]
      (is (nil? favicon) "Favicon should be nil for images smaller than 32x32"))))

(deftest test-generate-favicon-exact-size
  (testing "32x32 image generates favicon successfully"
    (let [bytes (fixture-bytes "png_32x32.png")
          content-type "image/png"
          content-hash "exact32"
          extension "png"
          favicon (img/generate-favicon bytes content-type content-hash extension)]
      (is (some? favicon) "32x32 image should generate favicon")
      (is (= [32 32] (img/get-image-dimensions (:bytes favicon)))))))
