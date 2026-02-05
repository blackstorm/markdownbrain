(ns mdbrain.utils.id
  (:import
   [java.util UUID]))

(defn generate-uuid []
  (str (UUID/randomUUID)))
