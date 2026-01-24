(ns markdownbrain.utils.bytes)

(defn format-storage-size
  "Format bytes into human-readable size string."
  [bytes]
  (cond
    (nil? bytes) "0 B"
    (< bytes 1024) (str bytes " B")
    (< bytes (* 1024 1024)) (format "%.1f KB" (/ bytes 1024.0))
    (< bytes (* 1024 1024 1024)) (format "%.1f MB" (/ bytes (* 1024.0 1024)))
    :else (format "%.2f GB" (/ bytes (* 1024.0 1024 1024)))))

