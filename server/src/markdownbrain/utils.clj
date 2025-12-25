(ns markdownbrain.utils
  (:require
   [buddy.hashers :as hashers]
   [clojure.string :as str])
  (:import
   [java.security MessageDigest]
   [java.util UUID]))

;; UUID 生成
(defn generate-uuid []
  (str (UUID/randomUUID)))

;; 生成确定性文档 ID（基于 vault-id 和 path）
(defn generate-document-id
  "根据 vault-id 和 path 生成确定性的文档 ID
   使用简单的 hash 方式：SHA-256(vault-id + path) 的十六进制表示前32位转为UUID格式"
  [vault-id path]
  (let [md (MessageDigest/getInstance "SHA-256")
        input (str vault-id ":" path)
        hash-bytes (.digest md (.getBytes input "UTF-8"))
        ;; 取前16字节转为UUID格式
        hex (apply str (map #(format "%02x" %) (take 16 hash-bytes)))
        ;; 格式化为 UUID: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
        uuid-str (str (subs hex 0 8) "-"
                      (subs hex 8 12) "-"
                      (subs hex 12 16) "-"
                      (subs hex 16 20) "-"
                      (subs hex 20 32))]
    uuid-str))

;; 生成客户端 ID（基于 path）
(defn generate-client-id
  "根据 path 生成客户端确定性 ID（与 Obsidian 插件保持一致）
   使用 SHA-256(path) 的十六进制表示转为 UUID 格式"
  [path]
  (let [md (MessageDigest/getInstance "SHA-256")
        hash-bytes (.digest md (.getBytes path "UTF-8"))
        hex (apply str (map #(format "%02x" %) hash-bytes))
        ;; 格式化为 UUID
        uuid-str (str (subs hex 0 8) "-"
                      (subs hex 8 12) "-"
                      (subs hex 12 16) "-"
                      (subs hex 16 20) "-"
                      (subs hex 20 32))]
    uuid-str))

;; 标准化 Obsidian 链接路径
(defn normalize-link-path
  "标准化 Obsidian 链接路径，确保以 .md 结尾"
  [link-path]
  (let [path (str/trim link-path)]
    (if (str/ends-with? path ".md")
      path
      (str path ".md"))))

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

(defn is-htmx? [req]
  (boolean (get-in req [:headers "hx-request"])))
