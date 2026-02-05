(ns mdbrain.utils.crypto
  (:import
   [java.security MessageDigest]))

(defn sha256-hex
  "Compute SHA-256 hash of byte array and return as hex string.
   Optional length parameter specifies number of bytes to use (default: 32 = full hash)."
  ([^bytes data]
   (sha256-hex data 32))
  ([^bytes data length]
   (let [md (MessageDigest/getInstance "SHA-256")
         hash-bytes (.digest md data)
         truncated (take length hash-bytes)]
     (apply str (map #(format "%02x" %) truncated)))))

