(ns markdownbrain.link-diff-test
  (:require [clojure.test :refer :all]
            [markdownbrain.link-diff :as link-diff]))

(deftest test-compute-link-diff-add-links
  (testing "Adding new links"
    (let [existing []
          new [{:targetClientId "id-1" :link "file1.md" :original "[[file1]]"}
               {:targetClientId "id-2" :link "file2.md" :original "[[file2]]"}]
          operations (link-diff/compute-link-diff existing new)
          summary (link-diff/summarize-diff operations)]
      (is (= 2 (:total-operations summary)))
      (is (= 0 (:deletes summary)))
      (is (= 2 (:inserts summary)))
      (is (every? #(= :insert (:op %)) operations)))))

(deftest test-compute-link-diff-remove-links
  (testing "Removing existing links"
    (let [existing [{:target-client-id "id-1" :target-path "file1.md"}
                    {:target-client-id "id-2" :target-path "file2.md"}]
          new []
          operations (link-diff/compute-link-diff existing new)
          summary (link-diff/summarize-diff operations)]
      (is (= 2 (:total-operations summary)))
      (is (= 2 (:deletes summary)))
      (is (= 0 (:inserts summary)))
      (is (every? #(= :delete (:op %)) operations)))))

(deftest test-compute-link-diff-replace-links
  (testing "Replacing links (delete old, add new)"
    (let [existing [{:target-client-id "id-1" :target-path "file1.md" :link-type "link" :display-text "" :original "[[file1]]"}
                    {:target-client-id "id-2" :target-path "file2.md" :link-type "link" :display-text "" :original "[[file2]]"}]
          new [{:targetClientId "id-2" :link "file2.md" :linkType "link" :displayText "" :original "[[file2]]"}
               {:targetClientId "id-3" :link "file3.md" :linkType "link" :displayText "" :original "[[file3]]"}]
          operations (link-diff/compute-link-diff existing new)
          summary (link-diff/summarize-diff operations)]
      ;; Should delete id-1, keep id-2, add id-3
      (is (= 2 (:total-operations summary)))
      (is (= 1 (:deletes summary)))
      (is (= 1 (:inserts summary))))))

(deftest test-compute-link-diff-no-change
  (testing "No changes - same links"
    (let [existing [{:target-client-id "id-1" :target-path "file1.md" :link-type "link" :display-text "file1" :original "[[file1]]"}]
          new [{:targetClientId "id-1" :link "file1.md" :linkType "link" :displayText "file1" :original "[[file1]]"}]
          operations (link-diff/compute-link-diff existing new)
          summary (link-diff/summarize-diff operations)]
      (is (= 0 (:total-operations summary)))
      (is (= 0 (:deletes summary)))
      (is (= 0 (:inserts summary))))))

(deftest test-compute-link-diff-update-link
  (testing "Update link metadata (display text changed)"
    (let [existing [{:target-client-id "id-1" :target-path "file1.md" :link-type "link" :display-text "old text" :original "[[file1|old text]]"}]
          new [{:targetClientId "id-1" :link "file1.md" :linkType "link" :displayText "new text" :original "[[file1|new text]]"}]
          operations (link-diff/compute-link-diff existing new)
          summary (link-diff/summarize-diff operations)]
      ;; Should delete old and insert new (update = delete + insert)
      (is (= 2 (:total-operations summary)))
      (is (= 0 (:deletes summary)))  ;; 不是纯删除
      (is (= 0 (:inserts summary)))  ;; 不是纯插入
      (is (= 1 (:updates summary))))))

(deftest test-operations-order
  (testing "Operations should be ordered: deletes, updates, inserts"
    (let [existing [{:target-client-id "id-1" :target-path "file1.md" :link-type "link" :display-text "text" :original "[[file1]]"}
                    {:target-client-id "id-2" :target-path "file2.md" :link-type "link" :display-text "old" :original "[[file2|old]]"}]
          new [{:targetClientId "id-2" :link "file2.md" :linkType "link" :displayText "new" :original "[[file2|new]]"}
               {:targetClientId "id-3" :link "file3.md" :linkType "link" :displayText "text" :original "[[file3]]"}]
          operations (link-diff/compute-link-diff existing new)
          ops-by-type (group-by :op operations)]
      ;; First operation should be delete (id-1)
      (is (= :delete (:op (first operations))))
      ;; Last operation should be insert (id-3)
      (is (= :insert (:op (last operations)))))))
