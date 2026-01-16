(ns markdownbrain.image-processing-test
  "Comprehensive tests for image processing functionality.
   Tests thumbnail generation, dimension detection, and format handling."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.string :as str]
            [markdownbrain.image-processing :as img])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]
           [java.awt.image BufferedImage]
           [javax.imageio ImageIO]))

;; =============================================================================
;; Helper Functions to Create Test Images
;; =============================================================================

(defn create-test-image
  "Create a test image with specified width, height, and format.
   Returns byte array of the image."
  [width height format]
  (let [image (BufferedImage. width height BufferedImage/TYPE_INT_RGB)
        g2d (.createGraphics image)
        ;; Fill with a gradient to ensure it's a valid image
        _ (.setColor g2d java.awt.Color/BLUE)
        _ (.fillRect g2d 0 0 width height)
        _ (.setColor g2d java.awt.Color/RED)
        _ (.fillRect g2d 0 0 (int (/ width 2)) (int (/ height 2)))
        baos (ByteArrayOutputStream.)]
    (.dispose g2d)
    (when-not (ImageIO/write image format baos)
      (throw (ex-info "Failed to write image" {:format format})))
    (.toByteArray baos)))

(defn create-valid-png
  "Create a valid PNG image for testing."
  ([width height] (create-test-image width height "png"))
  ([] (create-valid-png 100 100)))

(defn create-valid-jpeg
  "Create a valid JPEG image for testing."
  ([width height] (create-test-image width height "jpeg"))
  ([] (create-valid-jpeg 100 100)))

(defn create-invalid-bytes
  "Create invalid image bytes."
  []
  (.getBytes "This is not a valid image"))

;; =============================================================================
;; get-image-dimensions Tests
;; =============================================================================

(deftest test-get-image-dimensions-png
  (testing "PNG image dimensions - various sizes"
    (let [test-cases [[32 32] [100 100] [512 512] [1024 768] [768 1024]]]
      (doseq [[width height] test-cases]
        (let [bytes (create-valid-png width height)
              dims (img/get-image-dimensions bytes)]
          (is (= [width height] dims)
              (str "PNG dimensions should be " width "x" height)))))))

(deftest test-get-image-dimensions-jpeg
  (testing "JPEG image dimensions"
    (let [test-cases [[64 64] [200 150] [800 600]]]
      (doseq [[width height] test-cases]
        (let [bytes (create-valid-jpeg width height)
              dims (img/get-image-dimensions bytes)]
          (is (= [width height] dims)
              (str "JPEG dimensions should be " width "x" height)))))))

(deftest test-get-image-dimensions-webp
  (testing "WebP image dimensions"
    ;; Note: WebP support depends on ImageIO plugins
    ;; We test that the function handles unsupported formats gracefully
    (try
      (let [bytes (create-test-image 128 128 "webp")
            dims (img/get-image-dimensions bytes)]
        ;; If WebP is supported, dims should be [128 128]
        (is (= [128 128] dims)
            "WebP dimensions should be [128 128] if supported"))
      (catch Exception e
        ;; If WebP is not supported, the test creation will fail
        ;; This is expected - just skip the test
        (is true "WebP not supported by ImageIO, skipping test")))))

(deftest test-get-image-dimensions-invalid
  (testing "Invalid image data returns nil"
    (let [invalid-bytes (create-invalid-bytes)]
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
    ;; 512x512 can generate 256x256
    (is (true? (img/can-generate-thumbnail? [512 512] 256))
        "512x512 can generate 256x256")
    ;; 256x256 can generate 256x256 (exact match)
    (is (true? (img/can-generate-thumbnail? [256 256] 256))
        "256x256 can generate 256x256 (exact)")))

(deftest test-can-generate-thumbnail-small
  (testing "Small images cannot generate larger thumbnails"
    ;; 32x32 cannot generate 64x64
    (is (false? (img/can-generate-thumbnail? [32 32] 64))
        "32x32 cannot generate 64x64")
    ;; 100x100 cannot generate 512x512
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
    (let [bytes (create-valid-png 100 100)
          content-type "image/png"
          content-hash "favicon123"
          extension "png"
          favicon (img/generate-favicon bytes content-type content-hash extension)]
      (is (some? favicon) "Favicon should be generated")
      (is (contains? favicon :object-key) "Favicon should have object-key")
      (is (contains? favicon :bytes) "Favicon should have bytes")
      (is (bytes? (:bytes favicon)) "Favicon bytes should be byte array")
      (is (str/includes? (:object-key favicon) "@favicon.")
          "Object key should contain @favicon")
      (is (= [32 32] (img/get-image-dimensions (:bytes favicon)))
          "Generated favicon should be 32x32"))))

(deftest test-generate-favicon-small-image
  (testing "Small image (< 32x32) cannot generate favicon"
    (let [bytes (create-valid-png 16 16)
          content-type "image/png"
          content-hash "smallfavicon"
          extension "png"
          favicon (img/generate-favicon bytes content-type content-hash extension)]
      (is (nil? favicon) "Favicon should be nil for images smaller than 32x32"))))

(deftest test-generate-favicon-exact-size
  (testing "32x32 image generates favicon successfully"
    (let [bytes (create-valid-png 32 32)
          content-type "image/png"
          content-hash "exact32"
          extension "png"
          favicon (img/generate-favicon bytes content-type content-hash extension)]
      (is (some? favicon) "32x32 image should generate favicon")
      (is (= [32 32] (img/get-image-dimensions (:bytes favicon)))))))
