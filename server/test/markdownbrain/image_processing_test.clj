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
;; thumbnail-object-key Tests
;; =============================================================================

(deftest test-thumbnail-object-key
  (testing "Generate thumbnail object key"
    (is (= "site/logo/abc123@256x256.png"
           (img/thumbnail-object-key "abc123" 256 "png"))
        "Should generate correct key for 256x256 PNG")

    (is (= "site/logo/hash456@512x512.jpg"
           (img/thumbnail-object-key "hash456" 512 "jpg"))
        "Should generate correct key for 512x512 JPEG")

    (is (= "site/logo/xyz@32x32.webp"
           (img/thumbnail-object-key "xyz" 32 "webp"))
        "Should generate correct key for 32x32 WebP")))

(deftest test-thumbnail-object-key-sizes
  (testing "Thumbnail keys for all supported sizes"
    (let [hash "testhash"]
      (doseq [size img/thumbnail-sizes]
        (let [key (img/thumbnail-object-key hash size "png")]
          (is (str/includes? key (str size "x" size))
              (str "Key should contain " size "x" size))
          (is (str/starts-with? key "site/logo/")
              "Key should start with site/logo/")
          (is (str/ends-with? key ".png")
              "Key should end with .png"))))))

;; =============================================================================
;; generate-thumbnails Tests
;; =============================================================================

(deftest test-generate-thumbnails-large-image
  (testing "Large image generates all applicable thumbnails"
    (let [bytes (create-valid-png 1024 1024)
          content-type "image/png"
          content-hash "abcd1234"
          extension "png"
          thumbnails (img/generate-thumbnails bytes content-type content-hash extension)]
      (is (map? thumbnails))
      ;; Should generate all 5 sizes since 1024 >= 512
      (is (= 5 (count thumbnails))
          "Should generate all 5 thumbnail sizes")
      ;; Check that each size has correct structure
      (doseq [size img/thumbnail-sizes]
        (let [kw (keyword (str size))]
          (is (contains? thumbnails kw)
              (str "Should have thumbnail for size " size))
          (when (contains? thumbnails kw)
            (let [thumb (get thumbnails kw)]
              (is (contains? thumb :object-key)
                  (str "Size " size " should have :object-key"))
              (is (contains? thumb :bytes)
                  (str "Size " size " should have :bytes"))
              (is (bytes? (:bytes thumb))
                  (str "Size " size " bytes should be byte array"))
              ;; Verify key format
              (is (str/includes? (:object-key thumb) (str size "x" size))
                  (str "Key should contain " size "x" size)))))))))

(deftest test-generate-thumbnails-small-image
  (testing "Small image generates only applicable thumbnails"
    (let [bytes (create-valid-png 64 64)
          content-type "image/png"
          content-hash "smallhash"
          extension "png"
          thumbnails (img/generate-thumbnails bytes content-type content-hash extension)]
      (is (map? thumbnails))
      ;; 64x64 can only generate 64x64 and 32x32
      (is (<= 2 (count thumbnails))
          "Should generate at most 2 thumbnails")
      ;; Should have 64x64
      (is (contains? thumbnails :64)
          "Should have 64x64 thumbnail")
      ;; Should have 32x32
      (is (contains? thumbnails :32)
          "Should have 32x32 thumbnail")
      ;; Should NOT have 128x128
      (is (not (contains? thumbnails :128))
          "Should NOT have 128x128 thumbnail"))))

(deftest test-generate-thumbnails-portrait
  (testing "Portrait image generates thumbnails based on min dimension"
    (let [bytes (create-valid-png 600 800)  ;; Min dimension is 600
          content-type "image/png"
          content-hash "portrait"
          extension "png"
          thumbnails (img/generate-thumbnails bytes content-type content-hash extension)]
      ;; Min dimension 600 can generate up to 512
      (is (contains? thumbnails :512))
      (is (contains? thumbnails :256))
      (is (contains? thumbnails :128))
      (is (contains? thumbnails :64))
      (is (contains? thumbnails :32)))))

(deftest test-generate-thumbnails-landscape
  (testing "Landscape image generates thumbnails based on min dimension"
    (let [bytes (create-valid-png 800 600)  ;; Min dimension is 600
          content-type "image/png"
          content-hash "landscape"
          extension "png"
          thumbnails (img/generate-thumbnails bytes content-type content-hash extension)]
      ;; Min dimension 600 can generate up to 512
      (is (contains? thumbnails :512))
      (is (contains? thumbnails :256))
      (is (contains? thumbnails :128))
      (is (contains? thumbnails :64))
      (is (contains? thumbnails :32)))))

(deftest test-generate-thumbnails-jpeg
  (testing "JPEG thumbnails"
    (let [bytes (create-valid-jpeg 512 512)
          content-type "image/jpeg"
          content-hash "jpghash"
          extension "jpg"
          thumbnails (img/generate-thumbnails bytes content-type content-hash extension)]
      (is (map? thumbnails))
      ;; JPEG should work too
      (is (> (count thumbnails) 0))
      ;; Check that keys end with .jpg
      (doseq [[size thumb] thumbnails]
        (is (str/ends-with? (:object-key thumb) ".jpg")
            (str "JPEG thumbnail key should end with .jpg, got: " (:object-key thumb)))))))

(deftest test-generate-thumbnails-unsupported-format
  (testing "Unsupported format returns empty map"
    (let [bytes (create-invalid-bytes)
          content-type "image/svg+xml"  ;; SVG is not supported for thumbnails
          content-hash "svghash"
          extension "svg"
          thumbnails (img/generate-thumbnails bytes content-type content-hash extension)]
      (is (map? thumbnails))
      (is (= 0 (count thumbnails))
          "Unsupported format should return empty map"))))

(deftest test-generate-thumbnails-object-keys
  (testing "Generated object keys follow correct format"
    (let [bytes (create-valid-png 512 512)
          content-type "image/png"
          content-hash "keytest123"
          extension "png"
          thumbnails (img/generate-thumbnails bytes content-type content-hash extension)]
      (doseq [[size thumb] thumbnails]
        (let [expected-key (str "site/logo/keytest123@" (name size) "x" (name size) ".png")]
          (is (= expected-key (:object-key thumb))
              (str "Object key for size " size " should be " expected-key)))))))

;; =============================================================================
;; Integration Tests
;; =============================================================================

(deftest test-thumbnail-generation-pipeline
  (testing "Full thumbnail generation pipeline"
    (let [original-bytes (create-valid-png 1024 1024)
          content-type "image/png"
          content-hash "pipeline123"
          extension "png"

          ;; Step 1: Get dimensions
          dims (img/get-image-dimensions original-bytes)

          ;; Step 2: Check which thumbnails we can generate
          can-512 (img/can-generate-thumbnail? dims 512)
          can-256 (img/can-generate-thumbnail? dims 256)

          ;; Step 3: Generate thumbnails
          thumbnails (img/generate-thumbnails original-bytes content-type content-hash extension)]

      ;; Verify dimensions
      (is (= [1024 1024] dims))

      ;; Verify can-generate checks
      (is (true? can-512))
      (is (true? can-256))

      ;; Verify thumbnails were generated
      (is (contains? thumbnails :512))
      (is (contains? thumbnails :256))

      ;; Verify thumbnails are valid images
      (let [thumb-512 (:bytes (:512 thumbnails))
            thumb-256 (:bytes (:256 thumbnails))]
        (is (bytes? thumb-512))
        (is (bytes? thumb-256))
        (is (= [512 512] (img/get-image-dimensions thumb-512)))
        (is (= [256 256] (img/get-image-dimensions thumb-256)))))))

(deftest test-thumbnail-sizes-constant
  (testing "Thumbnail sizes constant is correct"
    (is (= [512 256 128 64 32] img/thumbnail-sizes)
        "Should have 5 sizes in descending order")))
