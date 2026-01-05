(ns markdownbrain.validation-test
  (:require [clojure.test :refer [deftest is testing]]
            [markdownbrain.validation :as validation]))

;; ============================================================
;; Full Sync 请求验证测试
;; ============================================================

(deftest test-validate-full-sync-request
  (testing "有效请求 - 正常的 clientIds 数组"
    (let [result (validation/validate-full-sync-request {:clientIds ["id1" "id2" "id3"]})]
      (is (true? (:valid? result)))
      (is (= ["id1" "id2" "id3"] (get-in result [:data :clientIds])))))

  (testing "有效请求 - 单个 clientId"
    (let [result (validation/validate-full-sync-request {:clientIds ["only-id"]})]
      (is (true? (:valid? result)))
      (is (= ["only-id"] (get-in result [:data :clientIds])))))

  (testing "无效请求 - 缺少 clientIds 字段"
    (let [result (validation/validate-full-sync-request {})]
      (is (false? (:valid? result)))
      (is (:message result))
      (is (:errors result))))

  (testing "无效请求 - clientIds 为空数组"
    (let [result (validation/validate-full-sync-request {:clientIds []})]
      (is (false? (:valid? result)))
      (is (:message result))
      (is (:errors result))))

  (testing "无效请求 - clientIds 包含空字符串"
    (let [result (validation/validate-full-sync-request {:clientIds ["id1" "" "id3"]})]
      (is (false? (:valid? result)))
      (is (:message result))
      (is (:errors result))))

  (testing "无效请求 - clientIds 类型错误 (不是数组)"
    (let [result (validation/validate-full-sync-request {:clientIds "not-an-array"})]
      (is (false? (:valid? result)))
      (is (:message result))
      (is (:errors result))))

  (testing "无效请求 - clientIds 包含非字符串元素"
    (let [result (validation/validate-full-sync-request {:clientIds ["id1" 123 "id3"]})]
      (is (false? (:valid? result)))
      (is (:message result))
      (is (:errors result)))))
