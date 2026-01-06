(ns markdownbrain.utils-test
  (:require [clojure.test :refer [deftest is testing]]
            [markdownbrain.utils :as utils]))

(deftest test-generate-uuid
  (testing "UUID generation"
    (let [uuid1 (utils/generate-uuid)
          uuid2 (utils/generate-uuid)]
      (is (string? uuid1))
      (is (= 36 (count uuid1)))
      (is (not= uuid1 uuid2))
      (is (re-matches #"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$" uuid1)))))

(deftest test-hash-password
  (testing "Password hashing"
    (let [password "test123"
          hash (utils/hash-password password)]
      (is (string? hash))
      (is (not= password hash))
      (is (> (count hash) 50)))))

(deftest test-verify-password
  (testing "Password verification - correct password"
    (let [password "test123"
          hash (utils/hash-password password)]
      (is (true? (utils/verify-password password hash)))))

  (testing "Password verification - wrong password"
    (let [password "test123"
          hash (utils/hash-password password)]
      (is (false? (utils/verify-password "wrong" hash)))))

  (testing "Password verification - empty password"
    (let [password ""
          hash (utils/hash-password password)]
      (is (true? (utils/verify-password "" hash))))))

(deftest test-parse-auth-header
  (testing "Valid Bearer token"
    (let [vault-id (utils/generate-uuid)
          sync-token (utils/generate-uuid)
          header (str "Bearer " vault-id ":" sync-token)
          result (utils/parse-auth-header header)]
      (is (map? result))
      (is (= vault-id (:vault-id result)))
      (is (= sync-token (:sync-token result)))))

  (testing "Valid token without Bearer prefix"
    (let [vault-id "vault-123"
          sync-token "token-456"
          header (str vault-id ":" sync-token)
          result (utils/parse-auth-header header)]
      (is (map? result))
      (is (= vault-id (:vault-id result)))
      (is (= sync-token (:sync-token result)))))

  (testing "Invalid token - no colon"
    (let [header "Bearer invalid-token"
          result (utils/parse-auth-header header)]
      (is (nil? result))))

  (testing "Invalid token - only vault-id"
    (let [header "Bearer vault-123:"
          result (utils/parse-auth-header header)]
      (is (nil? result))))

  (testing "Nil header"
    (is (nil? (utils/parse-auth-header nil))))

  (testing "Empty header"
    (is (nil? (utils/parse-auth-header "")))))
