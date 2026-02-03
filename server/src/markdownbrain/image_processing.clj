(ns markdownbrain.image-processing
  "Image processing utilities for favicon generation.
   Simplified to only generate 32x32 favicon for website logos."
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log])
  (:import [javax.imageio ImageIO]
           [java.awt Dimension]
           [java.io ByteArrayInputStream ByteArrayOutputStream]
           [net.coobird.thumbnailator Thumbnails]
           [net.coobird.thumbnailator.geometry Positions]))

;; Favicon size (1:1 aspect ratio)
(def ^:private favicon-size 32)

(defn- format-from-content-type
  "Get image format name from content-type.
   Returns 'png', 'jpeg' or nil for unsupported."
  [content-type]
  (case content-type
    "image/png" "png"
    "image/jpeg" "jpeg"
    "image/jpg" "jpeg"
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
    (with-open [in (ByteArrayInputStream. bytes)]
      (let [out (ByteArrayOutputStream.)
            builder (Thumbnails/of (into-array ByteArrayInputStream [in]))]
        (.size builder target-size target-size)
        (.crop builder Positions/CENTER)
        (.outputFormat builder format)
        (.toOutputStream builder out)
        (.toByteArray out)))
    (catch Exception e
      (log/error "Failed to generate thumbnail:" target-size (.getMessage e))
      (throw e))))

(defn generate-favicon
  "Generate 32x32 favicon for a logo.
   Returns nil if image is too small or format is unsupported.
   
   Object key format: site/logo/{hash}.favicon.{ext}
   This matches how serve-favicon constructs the key from logo-object-key."
  [bytes content-type content-hash extension]
  (let [format (format-from-content-type content-type)
        original-dims (get-image-dimensions bytes)]
    (when (and format (can-generate-thumbnail? original-dims favicon-size))
      (try
        (let [thumb-bytes (generate-square-thumbnail bytes favicon-size format)
              object-key (str "site/logo/" content-hash ".favicon." extension)]
          (log/debug "Generated favicon:" favicon-size "x" favicon-size)
          {:object-key object-key
           :bytes thumb-bytes})
        (catch Exception e
          (log/warn "Failed to generate favicon:" (.getMessage e))
          nil)))))
