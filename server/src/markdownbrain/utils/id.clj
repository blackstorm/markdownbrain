(ns markdownbrain.utils.id
  (:import
   [java.util UUID]))

(defn generate-uuid []
  (str (UUID/randomUUID)))
