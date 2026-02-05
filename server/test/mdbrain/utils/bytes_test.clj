(ns mdbrain.utils.bytes-test
  "Tests for bytes utilities."
  (:require
   [clojure.test :refer [deftest is testing]]
   [mdbrain.utils.bytes :as utils.bytes]))

(deftest test-format-storage-size
  (testing "Format nil as 0 B"
    (is (= "0 B" (utils.bytes/format-storage-size nil))))

  (testing "Format bytes"
    (is (= "512 B" (utils.bytes/format-storage-size 512))))

  (testing "Format KB"
    (is (= "1.5 KB" (utils.bytes/format-storage-size 1536))))

  (testing "Format MB"
    (is (= "2.5 MB" (utils.bytes/format-storage-size (* 2.5 1024 1024)))))

  (testing "Format GB"
    (is (= "1.50 GB" (utils.bytes/format-storage-size (* 1.5 1024 1024 1024))))))
