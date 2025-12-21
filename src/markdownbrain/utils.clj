(ns markdownbrain.utils
  (:require [buddy.hashers :as hashers]
            [clojure.string :as str])
  (:import [java.util UUID]))

;; UUID 生成
(defn generate-uuid []
  (str (UUID/randomUUID)))

;; 密码哈希
(defn hash-password [password]
  (hashers/derive password {:alg :bcrypt+sha512}))

(defn verify-password [password hash]
  (hashers/check password hash))

;; 生成 DNS 记录信息
(defn generate-dns-record [domain server-ip]
  (let [subdomain (first (str/split domain #"\."))]
    (str "请在您的 DNS 服务商添加以下记录：\n\n"
         "类型: A\n"
         "主机: " subdomain "\n"
         "值: " server-ip "\n"
         "TTL: 3600\n\n"
         "或\n\n"
         "类型: CNAME\n"
         "主机: " subdomain "\n"
         "值: server.yourdomain.com\n"
         "TTL: 3600")))

;; 从 Authorization header 解析 vault_id 和 sync_token
(defn parse-auth-header [header]
  (when header
    (let [token (str/replace header #"^Bearer\s+" "")
          [vault-id sync-token] (str/split token #":" 2)]
      (when (and vault-id sync-token
                 (not (str/blank? vault-id))
                 (not (str/blank? sync-token)))
        {:vault-id vault-id
         :sync-token sync-token}))))
