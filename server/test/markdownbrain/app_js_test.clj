(ns markdownbrain.app-js-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing]]))

(deftest app-js-does-not-hide-notes-on-mobile
  (testing "app.js should not hide previous notes on small screens"
    (let [res (io/resource "publics/app/js/app.js")]
      (is (some? res))
      (let [content (slurp res)]
        (is (not (.contains content "classList.add(\"hidden\")")))))))
