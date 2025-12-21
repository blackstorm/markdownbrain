# 后端 Clojure 代码示例

## 1. deps.edn - 依赖配置

```clojure
{:paths ["src" "resources"]
 :deps {org.clojure/clojure {:mvn/version "1.11.1"}

        ;; Web 框架
        metosin/reitit {:mvn/version "0.7.0"}
        metosin/reitit-ring {:mvn/version "0.7.0"}
        metosin/reitit-middleware {:mvn/version "0.7.0"}
        ring/ring-core {:mvn/version "1.11.0"}
        ring/ring-jetty-adapter {:mvn/version "1.11.0"}
        ring/ring-json {:mvn/version "0.5.1"}

        ;; 数据库
        com.github.seancorfield/next.jdbc {:mvn/version "1.3.909"}
        org.xerial/sqlite-jdbc {:mvn/version "3.45.0.0"}

        ;; 安全
        buddy/buddy-hashers {:mvn/version "2.0.167"}
        buddy/buddy-sign {:mvn/version "3.5.351"}

        ;; 模板
        selmer/selmer {:mvn/version "1.12.59"}

        ;; 工具
        metosin/jsonista {:mvn/version "0.3.8"}
        clj-time/clj-time {:mvn/version "0.15.2"}}

 :aliases
 {:dev {:extra-paths ["dev"]
        :extra-deps {ring/ring-devel {:mvn/version "1.11.0"}}}
  :build {:deps {io.github.clojure/tools.build {:git/tag "v0.9.6" :git/sha "8e78bcc"}}
          :ns-default build}}}
```

## 2. src/markdownbrain/config.clj - 配置

```clojure
(ns markdownbrain.config
  (:require [clojure.java.io :as io]))

(def config
  {:server
   {:port (or (System/getenv "PORT") 3000)
    :host (or (System/getenv "HOST") "0.0.0.0")}

   :database
   {:dbtype "sqlite"
    :dbname (or (System/getenv "DB_PATH") "markdownbrain.db")}

   :session
   {:secret (or (System/getenv "SESSION_SECRET") "change-me-in-production")
    :cookie-name "markdownbrain-session"
    :max-age (* 60 60 24 7)} ; 7 days

   :server-ip
   (or (System/getenv "SERVER_IP") "123.45.67.89")})

(defn get-config [& path]
  (get-in config path))
```

## 3. src/markdownbrain/db.clj - 数据库层

```clojure
(ns markdownbrain.db
  (:require [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [next.jdbc.result-set :as rs]
            [markdownbrain.config :as config]
            [clojure.java.io :as io]))

(def datasource
  (delay
    (jdbc/get-datasource (config/get-config :database))))

(defn execute! [sql-vec]
  (jdbc/execute! @datasource sql-vec))

(defn execute-one! [sql-vec]
  (jdbc/execute-one! @datasource sql-vec {:builder-fn rs/as-unqualified-lower-maps}))

;; 初始化数据库
(defn init-db! []
  (let [schema (slurp (io/resource "migrations/001-initial-schema.sql"))]
    (jdbc/execute! @datasource [schema])))

;; Tenant 操作
(defn create-tenant! [id name]
  (sql/insert! @datasource :tenants
               {:id id :name name}
               {:builder-fn rs/as-unqualified-lower-maps}))

(defn get-tenant [id]
  (execute-one! ["SELECT * FROM tenants WHERE id = ?" id]))

;; User 操作
(defn create-user! [id tenant-id username password-hash]
  (sql/insert! @datasource :users
               {:id id
                :tenant_id tenant-id
                :username username
                :password_hash password-hash}
               {:builder-fn rs/as-unqualified-lower-maps}))

(defn get-user-by-username [username]
  (execute-one! ["SELECT * FROM users WHERE username = ?" username]))

(defn get-user-by-id [id]
  (execute-one! ["SELECT * FROM users WHERE id = ?" id]))

;; Vault 操作
(defn create-vault! [id tenant-id name domain sync-token domain-record]
  (sql/insert! @datasource :vaults
               {:id id
                :tenant_id tenant-id
                :name name
                :domain domain
                :sync_token sync-token
                :domain_record domain-record}
               {:builder-fn rs/as-unqualified-lower-maps}))

(defn get-vault-by-id [id]
  (execute-one! ["SELECT * FROM vaults WHERE id = ?" id]))

(defn get-vault-by-domain [domain]
  (execute-one! ["SELECT * FROM vaults WHERE domain = ?" domain]))

(defn get-vault-by-sync-token [sync-token]
  (execute-one! ["SELECT * FROM vaults WHERE sync_token = ?" sync-token]))

(defn list-vaults-by-tenant [tenant-id]
  (execute! ["SELECT * FROM vaults WHERE tenant_id = ? ORDER BY created_at DESC" tenant-id]))

;; Document 操作
(defn upsert-document! [id tenant-id vault-id path content metadata hash mtime]
  (execute-one!
    ["INSERT INTO documents (id, tenant_id, vault_id, path, content, metadata, hash, mtime)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(vault_id, path) DO UPDATE SET
        content = excluded.content,
        metadata = excluded.metadata,
        hash = excluded.hash,
        mtime = excluded.mtime,
        updated_at = CURRENT_TIMESTAMP"
     id tenant-id vault-id path content metadata hash mtime]))

(defn delete-document! [vault-id path]
  (execute-one! ["DELETE FROM documents WHERE vault_id = ? AND path = ?" vault-id path]))

(defn get-document [id]
  (execute-one! ["SELECT * FROM documents WHERE id = ?" id]))

(defn list-documents-by-vault [vault-id]
  (execute! ["SELECT id, path, hash, mtime FROM documents
              WHERE vault_id = ?
              ORDER BY path ASC" vault-id]))

(defn get-document-by-path [vault-id path]
  (execute-one! ["SELECT * FROM documents WHERE vault_id = ? AND path = ?" vault-id path]))
```

## 4. src/markdownbrain/utils.clj - 工具函数

```clojure
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
      (when (and vault-id sync-token)
        {:vault-id vault-id
         :sync-token sync-token}))))
```

## 5. src/markdownbrain/middleware.clj - 中间件

```clojure
(ns markdownbrain.middleware
  (:require [ring.middleware.session :as session]
            [ring.middleware.session.cookie :as cookie]
            [ring.middleware.params :as params]
            [ring.middleware.json :as json]
            [ring.middleware.keyword-params :as keyword-params]
            [ring.util.response :as response]
            [markdownbrain.config :as config]))

;; Session 配置
(defn wrap-session-middleware [handler]
  (session/wrap-session
    handler
    {:store (cookie/cookie-store {:key (config/get-config :session :secret)})
     :cookie-name (config/get-config :session :cookie-name)
     :cookie-attrs {:max-age (config/get-config :session :max-age)
                    :http-only true
                    :same-site :lax}}))

;; 认证中间件（检查管理员登录）
(defn wrap-auth [handler]
  (fn [request]
    (if (get-in request [:session :user-id])
      (handler request)
      {:status 401
       :headers {"Content-Type" "application/json"}
       :body {:error "Unauthorized" :message "请先登录"}})))

;; CORS 中间件
(defn wrap-cors [handler]
  (fn [request]
    (let [response (handler request)]
      (-> response
          (assoc-in [:headers "Access-Control-Allow-Origin"] "*")
          (assoc-in [:headers "Access-Control-Allow-Methods"] "GET, POST, PUT, DELETE, OPTIONS")
          (assoc-in [:headers "Access-Control-Allow-Headers"] "Content-Type, Authorization")))))

;; 完整中间件栈
(defn wrap-middleware [handler]
  (-> handler
      wrap-session-middleware
      keyword-params/wrap-keyword-params
      params/wrap-params
      json/wrap-json-response
      json/wrap-json-body
      wrap-cors))
```

## 6. src/markdownbrain/handlers/admin.clj - 管理员处理器

```clojure
(ns markdownbrain.handlers.admin
  (:require [markdownbrain.db :as db]
            [markdownbrain.utils :as utils]
            [markdownbrain.config :as config]
            [ring.util.response :as response]))

;; 初始化管理员用户
(defn init-admin [request]
  (let [{:keys [username password tenant_name]} (:body-params request)
        existing-user (db/get-user-by-username username)]
    (if existing-user
      {:status 400
       :body {:success false :error "用户名已存在"}}
      (let [tenant-id (utils/generate-uuid)
            user-id (utils/generate-uuid)
            password-hash (utils/hash-password password)]
        (db/create-tenant! tenant-id tenant_name)
        (db/create-user! user-id tenant-id username password-hash)
        {:status 200
         :body {:success true
                :tenant_id tenant-id
                :user_id user-id}}))))

;; 管理员登录
(defn login [request]
  (let [{:keys [username password]} (:body-params request)
        user (db/get-user-by-username username)]
    (if (and user (utils/verify-password password (:password_hash user)))
      {:status 200
       :session {:user-id (:id user)
                 :tenant-id (:tenant_id user)}
       :body {:success true
              :user {:id (:id user)
                     :username (:username user)
                     :tenant_id (:tenant_id user)}}}
      {:status 401
       :body {:success false :error "用户名或密码错误"}})))

;; 管理员登出
(defn logout [request]
  {:status 200
   :session nil
   :body {:success true}})

;; 列出 vault
(defn list-vaults [request]
  (let [tenant-id (get-in request [:session :tenant-id])
        vaults (db/list-vaults-by-tenant tenant-id)]
    {:status 200
     :body (mapv #(select-keys % [:id :name :domain :sync_token :domain_record :created_at])
                 vaults)}))

;; 创建 vault
(defn create-vault [request]
  (let [tenant-id (get-in request [:session :tenant-id])
        {:keys [name domain]} (:body-params request)
        vault-id (utils/generate-uuid)
        sync-token (utils/generate-uuid)
        server-ip (config/get-config :server-ip)
        domain-record (utils/generate-dns-record domain server-ip)]
    (db/create-vault! vault-id tenant-id name domain sync-token domain-record)
    {:status 200
     :body {:success true
            :vault {:id vault-id
                    :name name
                    :domain domain
                    :sync_token sync-token
                    :domain_record domain-record}}}))
```

## 7. src/markdownbrain/handlers/sync.clj - 同步处理器

```clojure
(ns markdownbrain.handlers.sync
  (:require [markdownbrain.db :as db]
            [markdownbrain.utils :as utils]
            [clojure.data.json :as json]))

;; 验证同步 token
(defn validate-sync-token [vault-id sync-token]
  (when-let [vault (db/get-vault-by-sync-token sync-token)]
    (when (= (:id vault) vault-id)
      vault)))

;; 同步文件
(defn sync-file [request]
  (let [auth-header (get-in request [:headers "authorization"])
        auth (utils/parse-auth-header auth-header)
        {:keys [vault-id sync-token]} (or auth (:body-params request))
        {:keys [path content metadata hash mtime action]} (:body-params request)]

    (if-let [vault (validate-sync-token vault-id sync-token)]
      (do
        (case action
          "delete"
          (db/delete-document! vault-id path)

          ;; "create" 和 "modify" 都使用 upsert
          (let [doc-id (utils/generate-uuid)
                tenant-id (:tenant_id vault)
                metadata-str (when metadata (json/write-str metadata))]
            (db/upsert-document! doc-id tenant-id vault-id path content metadata-str hash mtime)))

        {:status 200
         :body {:success true
                :vault_id vault-id
                :path path
                :action action}})

      {:status 401
       :body {:success false
              :error "Invalid vault_id or sync_token"}})))
```

## 8. src/markdownbrain/handlers/frontend.clj - 前端处理器

```clojure
(ns markdownbrain.handlers.frontend
  (:require [markdownbrain.db :as db]
            [selmer.parser :as selmer]
            [ring.util.response :as response]))

;; 获取当前域名对应的 vault
(defn get-current-vault [request]
  (let [host (get-in request [:headers "host"])]
    (db/get-vault-by-domain host)))

;; 首页 - 显示 vault 信息
(defn home [request]
  (if-let [vault (get-current-vault request)]
    (let [documents (db/list-documents-by-vault (:id vault))]
      {:status 200
       :headers {"Content-Type" "text/html"}
       :body (selmer/render-file "templates/frontend/home.html"
                                   {:vault vault
                                    :documents documents})})
    {:status 404
     :body "Vault not found"}))

;; API: 获取文档列表
(defn get-documents [request]
  (if-let [vault (get-current-vault request)]
    {:status 200
     :body (db/list-documents-by-vault (:id vault))}
    {:status 404
     :body {:error "Vault not found"}}))

;; API: 获取单个文档
(defn get-document [request]
  (let [doc-id (get-in request [:path-params :id])]
    (if-let [doc (db/get-document doc-id)]
      {:status 200
       :body doc}
      {:status 404
       :body {:error "Document not found"}})))

;; 管理后台首页
(defn admin-home [request]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (selmer/render-file "templates/admin/vaults.html" {})})

;; 登录页面
(defn login-page [request]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (selmer/render-file "templates/admin/login.html" {})})
```

## 9. src/markdownbrain/routes.clj - 路由定义

```clojure
(ns markdownbrain.routes
  (:require [reitit.ring :as ring]
            [reitit.ring.middleware.parameters :as parameters]
            [markdownbrain.handlers.admin :as admin]
            [markdownbrain.handlers.sync :as sync]
            [markdownbrain.handlers.frontend :as frontend]
            [markdownbrain.middleware :as middleware]))

(def routes
  [["/" {:get frontend/home}]

   ["/api"
    ["/admin"
     ["/init" {:post admin/init-admin}]
     ["/login" {:post admin/login}]
     ["/logout" {:post {:middleware [middleware/wrap-auth]
                        :handler admin/logout}}]
     ["/vaults" {:get {:middleware [middleware/wrap-auth]
                       :handler admin/list-vaults}
                 :post {:middleware [middleware/wrap-auth]
                        :handler admin/create-vault}}]]

    ["/sync" {:post sync/sync-file}]

    ["/documents" {:get frontend/get-documents}]
    ["/documents/:id" {:get frontend/get-document}]]

   ["/admin"
    ["" {:get frontend/admin-home}]
    ["/login" {:get frontend/login-page}]]])

(def app
  (ring/ring-handler
    (ring/router routes
                 {:data {:middleware [parameters/parameters-middleware]}})
    (ring/create-default-handler)))
```

## 10. src/markdownbrain/core.clj - 主入口

```clojure
(ns markdownbrain.core
  (:require [ring.adapter.jetty :as jetty]
            [markdownbrain.routes :as routes]
            [markdownbrain.middleware :as middleware]
            [markdownbrain.db :as db]
            [markdownbrain.config :as config])
  (:gen-class))

(defn start-server []
  (let [port (config/get-config :server :port)
        host (config/get-config :server :host)]
    (println "Initializing database...")
    (db/init-db!)
    (println "Starting server on" host ":" port)
    (jetty/run-jetty
      (middleware/wrap-middleware routes/app)
      {:port port
       :host host
       :join? false})))

(defn -main [& args]
  (start-server))
```

## 运行后端

```bash
# 安装依赖
clj -P

# 运行服务器
clj -M -m markdownbrain.core

# 或使用开发模式
clj -M:dev -m markdownbrain.core
```

## 测试 API

```bash
# 初始化管理员
curl -X POST http://localhost:3000/api/admin/init \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password123","tenant_name":"My Organization"}'

# 登录
curl -X POST http://localhost:3000/api/admin/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password123"}' \
  -c cookies.txt

# 创建 vault（需要 session）
curl -X POST http://localhost:3000/api/admin/vaults \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{"name":"My Blog","domain":"blog.example.com"}'

# 同步文件（从 Obsidian 插件）
curl -X POST http://localhost:3000/api/sync \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <vault-id>:<sync-token>" \
  -d '{"path":"README.md","content":"# Hello","hash":"abc123","mtime":"2025-12-21T10:00:00Z","action":"create"}'
```
