(ns markdownbrain.config-test
  (:require [clojure.test :refer [deftest is testing]]
            [markdownbrain.config :as config]))

;; Get Config 测试
(deftest test-get-config
  (testing "Get server host"
    (let [host (config/get-config :server :host)]
      (is (string? host))
      (is (not (clojure.string/blank? host)))))

  (testing "Get server port"
    (let [port (config/get-config :server :port)]
      (is (number? port))
      (is (pos? port))))

  (testing "Get database name"
    (let [db-name (config/get-config :database :dbname)]
      (is (string? db-name))))

  (testing "Get session secret"
    (let [secret (config/get-config :session :secret)]
      (is (bytes? secret))
      (is (= 16 (alength secret)))))

  (testing "Get server IP"
    (let [server-ip (config/get-config :server-ip)]
      (is (string? server-ip))))

  (testing "Get nested config with multiple keys"
    (let [host (config/get-config :server :host)
          port (config/get-config :server :port)]
      (is (string? host))
      (is (number? port))))

  (testing "Get top-level config"
    (let [server-config (config/get-config :server)]
      (is (map? server-config))
      (is (contains? server-config :host))
      (is (contains? server-config :port)))))

;; Default Config Values 测试
(deftest test-default-config-values
  (testing "Default server configuration exists"
    (is (some? (config/get-config :server))))

  (testing "Default database configuration exists"
    (is (some? (config/get-config :database))))

  (testing "Default session configuration exists"
    (is (some? (config/get-config :session))))

  (testing "Default server IP exists"
    (is (some? (config/get-config :server-ip)))))

;; Config Structure 测试
(deftest test-config-structure
  (testing "Server config has required keys"
    (let [server-config (config/get-config :server)]
      (is (contains? server-config :host))
      (is (contains? server-config :port))))

  (testing "Database config has required keys"
    (let [db-config (config/get-config :database)]
      (is (contains? db-config :dbtype))
      (is (contains? db-config :dbname))))

  (testing "Session config has required keys"
    (let [session-config (config/get-config :session)]
      (is (contains? session-config :secret))
      (is (contains? session-config :cookie-name))
      (is (contains? session-config :max-age)))))

;; Config Values Type Check 测试
(deftest test-config-value-types
  (testing "Server host is string"
    (is (string? (config/get-config :server :host))))

  (testing "Server port is number"
    (is (number? (config/get-config :server :port))))

  (testing "Database name is string"
    (is (string? (config/get-config :database :dbname))))

  (testing "Session secret is bytes"
    (is (bytes? (config/get-config :session :secret))))

  (testing "Cookie name is string"
    (is (string? (config/get-config :session :cookie-name))))

  (testing "Max age is number"
    (is (number? (config/get-config :session :max-age)))))

;; Environment-based Config 测试
(deftest test-environment-config
  (testing "Config responds to environment variables"
    ;; Test that config can be overridden (if implemented)
    (let [host (config/get-config :server :host)]
      (is (or (= "0.0.0.0" host) (= "localhost" host)))))

  (testing "Database name is configurable"
    (let [db-name (config/get-config :database :dbname)]
      (is (or (clojure.string/ends-with? db-name ".db")
              (= ":memory:" db-name))))))

