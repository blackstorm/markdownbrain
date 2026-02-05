(ns mdbrain.utils.stream
  (:require
   [clojure.java.io :as io]))

(defn input-stream->bytes
  "Convert InputStream to byte array."
  [is]
  (let [baos (java.io.ByteArrayOutputStream.)]
    (io/copy is baos)
    (.toByteArray baos)))

