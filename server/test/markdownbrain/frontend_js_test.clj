(ns markdownbrain.frontend-js-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing]]))

(deftest frontend-js-does-not-hide-notes-on-mobile
  (testing "frontend.js should not hide previous notes on small screens"
    (let [res (io/resource "publics/frontend/js/frontend.js")]
      (is (some? res))
      (let [content (slurp res)]
        (is (not (.contains content "classList.add(\"hidden\")")))))))

