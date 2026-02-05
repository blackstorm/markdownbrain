(ns mdbrain.utils.paths
  (:require
   [clojure.string :as str]))

(defn normalize-link-path
  [link-path]
  (let [path (str/trim link-path)]
    (if (str/ends-with? path ".md")
      path
      (str path ".md"))))

