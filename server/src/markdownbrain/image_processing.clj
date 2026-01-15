(ns markdownbrain.image-processing
  "Image processing utilities for thumbnail generation.
   Supports PNG, JPEG, WebP formats only (no SVG/GIF for logos)."
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log])
  (:import [javax.imageio ImageIO]
           [java.awt Dimension]
           [java.io ByteArrayInputStream ByteArrayOutputStream]
           [net.coobird.thumbnailator Thumbnails]
           [net.coobird.thumbnailator.geometry Positions]))

;; Supported thumbnail sizes (1:1 aspect ratio)
(def ^:private thumbnail-sizes
  "Supported thumbnail sizes for logo generation."
  [512 256 128 64 32])

(def thumbnail-sizes
  "Public constant for thumbnail sizes, used by other modules."
  thumbnail-sizes)

(defn- format-from-content-type
  "Get image format name from content-type.
   Returns 'png', 'jpeg', 'webp' or nil for unsupported."
  [content-type]
  (case content-type
    "image/png" "png"
    "image/jpeg" "jpeg"
    "image/jpg" "jpeg"
    "image/webp" "webp"
    nil))

(defn get-image-dimensions
  "Get [width height] of image from bytes.
   Returns nil for unsupported formats."
  [bytes]
  (try
    (with-open [in (ByteArrayInputStream. bytes)]
      (let [image (ImageIO/read in)]
        (when image
          [(.getWidth image) (.getHeight image)])))
    (catch Exception e
      (log/debug "Could not read image dimensions:" (.getMessage e))
      nil)))

(defn can-generate-thumbnail?
  "Check if thumbnail can be generated (original dimensions >= target).
   Returns false if original-dims is nil or dimensions are too small."
  [original-dims target-size]
  (boolean
   (and original-dims
        (>= (min (first original-dims) (second original-dims)) target-size))))

(defn generate-square-thumbnail
  "Generate square thumbnail using scale-then-crop approach.
   Returns byte array of thumbnail.
   Throws if target-size > original-dimensions."
  [bytes target-size format]
  (try
    (let [in (ByteArrayInputStream. bytes)
          out (ByteArrayOutputStream.)
          builder (Thumbnails/of (into-array ByteArrayInputStream [in]))]
      (.size builder target-size target-size)
      (.crop builder Positions/CENTER)
      (.outputFormat builder format)
      (.toOutputStream builder out)
      (.toByteArray out))
    (catch Exception e
      (log/error "Failed to generate thumbnail:" target-size (.getMessage e))
      (throw e))))

(defn thumbnail-object-key
  "Generate object key for a thumbnail.
   Example: site/logo/abc123@256x256.png"
  [content-hash size extension]
  (str "site/logo/" content-hash "@" size "x" size "." extension))

(defn generate-thumbnails
  "Generate all applicable thumbnails for a logo.
   Returns map of {size {:object-key key :bytes bytes}}.
   Skips sizes larger than original dimensions."
  [bytes content-type content-hash extension]
  (let [format (format-from-content-type content-type)
        original-dims (get-image-dimensions bytes)]
    (if (and format original-dims)
      (into {}
            (for [size thumbnail-sizes
                  :when (can-generate-thumbnail? original-dims size)]
              (try
                (let [thumb-bytes (generate-square-thumbnail bytes size format)
                      object-key (thumbnail-object-key content-hash size extension)]
                  (log/debug "Generated thumbnail:" size "x" size)
                  [(keyword (str size))
                   {:object-key object-key
                    :bytes thumb-bytes}])
                (catch Exception e
                  (log/warn "Failed to generate" size "x" size "thumbnail:" (.getMessage e))
                  nil))))
      (do
        (log/warn "Could not generate thumbnails - unsupported format or could not read dimensions")
        {}))))
