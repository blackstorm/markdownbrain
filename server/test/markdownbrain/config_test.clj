(ns markdownbrain.config-test
  (:require [clojure.test :refer [deftest is testing]]
            [markdownbrain.config :as config]))

;; Get Config 测试
(deftest test-get-config
  (testing "Get frontend server host"
    (let [host (config/get-config :server :frontend :host)]
      (is (string? host))
      (is (not (clojure.string/blank? host)))))

  (testing "Get frontend server port"
    (let [port (config/get-config :server :frontend :port)]
      (is (number? port))
      (is (pos? port))))

  (testing "Get admin server host"
    (let [host (config/get-config :server :admin :host)]
      (is (string? host))
      (is (not (clojure.string/blank? host)))))

  (testing "Get admin server port"
    (let [port (config/get-config :server :admin :port)]
      (is (number? port))
      (is (pos? port))))

  (testing "Get database jdbcUrl"
    (let [jdbc-url (config/get-config :database :jdbcUrl)]
      (is (string? jdbc-url))
      (is (clojure.string/starts-with? jdbc-url "jdbc:sqlite:"))))

  (testing "Get top-level config"
    (let [server-config (config/get-config :server)]
      (is (map? server-config))
      (is (contains? server-config :frontend))
      (is (contains? server-config :admin)))))

;; Default Config Values 测试
(deftest test-default-config-values
  (testing "Default server configuration exists"
    (is (some? (config/get-config :server))))

  (testing "Default database configuration exists"
    (is (some? (config/get-config :database))))

  (testing "Default environment configuration exists"
    (is (some? (config/get-config :environment)))))

;; Config Structure 测试
(deftest test-config-structure
  (testing "Server config has required keys"
    (let [server-config (config/get-config :server)]
      (is (contains? server-config :frontend))
      (is (contains? server-config :admin))))

  (testing "Frontend server config has required keys"
    (let [frontend-config (config/get-config :server :frontend)]
      (is (contains? frontend-config :host))
      (is (contains? frontend-config :port))))

  (testing "Admin server config has required keys"
    (let [admin-config (config/get-config :server :admin)]
      (is (contains? admin-config :host))
      (is (contains? admin-config :port))))

  (testing "Database config has required keys"
    (let [db-config (config/get-config :database)]
      (is (contains? db-config :jdbcUrl)))))

;; Config Values Type Check 测试
(deftest test-config-value-types
  (testing "Frontend server host is string"
    (is (string? (config/get-config :server :frontend :host))))

  (testing "Frontend server port is number"
    (is (number? (config/get-config :server :frontend :port))))

  (testing "Admin server host is string"
    (is (string? (config/get-config :server :admin :host))))

  (testing "Admin server port is number"
    (is (number? (config/get-config :server :admin :port))))

  (testing "Database jdbcUrl is string"
    (is (string? (config/get-config :database :jdbcUrl)))))

;; Session & Token 函数测试
(deftest test-session-function
  (testing "Session secret is bytes with 16 length"
    (let [secret (config/session-secret)]
      (is (bytes? secret))
      (is (= 16 (alength secret))))))

;; Environment helpers 测试
(deftest test-environment-helpers
  (testing "production? returns boolean"
    (is (boolean? (config/production?))))

  (testing "development? returns boolean"
    (is (boolean? (config/development?))))

  (testing "Either production or development is true"
    ;; In test, environment is :development by default
    (is (or (config/production?) (config/development?)))))

;; Environment-based Config 测试
(deftest test-environment-config
  (testing "Config responds to environment variables"
    (let [host (config/get-config :server :frontend :host)]
      (is (or (= "0.0.0.0" host) (= "localhost" host)))))

  (testing "Database jdbcUrl is configurable"
    (let [jdbc-url (config/get-config :database :jdbcUrl)]
      (is (clojure.string/starts-with? jdbc-url "jdbc:sqlite:")))))
