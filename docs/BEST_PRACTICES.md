# MarkdownBrain 开发规范和最佳实践

## 代码风格

### Clojure 代码风格

```clojure
;; ✅ 推荐：使用有意义的命名
(defn create-vault! [tenant-id name domain]
  (let [vault-id (generate-uuid)
        sync-token (generate-uuid)]
    (db/insert-vault! vault-id tenant-id name domain sync-token)))

;; ❌ 不推荐：缩写和不清晰的命名
(defn cr-v [tid n d]
  (let [vid (gen-uuid)
        tok (gen-uuid)]
    (db/ins-v vid tid n d tok)))

;; ✅ 推荐：使用 threading macros
(defn process-document [doc]
  (-> doc
      (parse-markdown)
      (extract-metadata)
      (validate-content)
      (save-to-db)))

;; ❌ 不推荐：深层嵌套
(defn process-document [doc]
  (save-to-db
    (validate-content
      (extract-metadata
        (parse-markdown doc)))))

;; ✅ 推荐：使用 spec 验证（可选）
(s/def ::vault-name string?)
(s/def ::domain string?)
(s/def ::create-vault-request (s/keys :req-un [::vault-name ::domain]))
```

### ClojureScript 代码风格

```clojure
;; ✅ 推荐：使用 defn ^:export 导出函数给 JS
(defn ^:export init [config]
  (reset! app-state (js->clj config :keywordize-keys true)))

;; ✅ 推荐：使用 go blocks 处理异步
(defn fetch-data [url]
  (go
    (let [response (<! (http/get url))]
      (if (:success response)
        (:body response)
        (js/console.error "Failed to fetch")))))

;; ✅ 推荐：使用 js->clj 和 clj->js 转换
(defn process-js-data [js-data]
  (let [clj-data (js->clj js-data :keywordize-keys true)
        processed (process clj-data)]
    (clj->js processed)))
```

### TypeScript 代码风格

```typescript
// ✅ 推荐：使用 interface 定义类型
interface SyncConfig {
    serverUrl: string;
    vaultId: string;
    syncToken: string;
}

// ✅ 推荐：使用 async/await
async handleFileChange(file: TFile): Promise<void> {
    try {
        const content = await this.app.vault.read(file);
        await this.syncFile(file.path, content);
    } catch (error) {
        console.error('Sync failed:', error);
    }
}

// ❌ 不推荐：使用 any
function processData(data: any) { // 应该定义具体类型
    return data.value;
}
```

---

## 数据库操作规范

### 使用参数化查询（防止 SQL 注入）

```clojure
;; ✅ 推荐：参数化查询
(defn get-vault-by-domain [domain]
  (db/execute-one! ["SELECT * FROM vaults WHERE domain = ?" domain]))

;; ❌ 危险：字符串拼接
(defn get-vault-by-domain [domain]
  (db/execute-one! [(str "SELECT * FROM vaults WHERE domain = '" domain "'")]))
```

### 使用事务处理关联操作

```clojure
(defn create-vault-with-settings! [tenant-id vault-data settings]
  (jdbc/with-transaction [tx datasource]
    (let [vault-id (create-vault! tx tenant-id vault-data)]
      (create-vault-settings! tx vault-id settings)
      vault-id)))
```

### 使用索引优化查询

```sql
-- ✅ 在常用查询字段上创建索引
CREATE INDEX idx_vaults_domain ON vaults(domain);
CREATE INDEX idx_documents_vault_path ON documents(vault_id, path);

-- ✅ 使用 EXPLAIN 分析查询性能
EXPLAIN QUERY PLAN SELECT * FROM documents WHERE vault_id = ?;
```

---

## API 设计规范

### RESTful API 原则

```clojure
;; ✅ 推荐：使用正确的 HTTP 方法
GET    /api/vaults           ; 列出资源
GET    /api/vaults/:id       ; 获取单个资源
POST   /api/vaults           ; 创建资源
PUT    /api/vaults/:id       ; 更新资源（完整替换）
PATCH  /api/vaults/:id       ; 更新资源（部分更新）
DELETE /api/vaults/:id       ; 删除资源

;; ❌ 不推荐：所有操作都用 POST
POST /api/getVaults
POST /api/createVault
POST /api/deleteVault
```

### 统一的响应格式

```clojure
;; ✅ 成功响应
{:status 200
 :body {:success true
        :data {...}}}

;; ✅ 错误响应
{:status 400
 :body {:success false
        :error "Invalid input"
        :details {:field "domain" :message "Domain already exists"}}}
```

### 使用中间件验证

```clojure
;; ✅ 推荐：使用中间件统一处理认证
(def admin-routes
  ["/api/admin"
   {:middleware [wrap-auth]}
   ["/vaults" {:get list-vaults
               :post create-vault}]])

;; ❌ 不推荐：在每个 handler 中重复验证
(defn list-vaults [req]
  (if-not (authenticated? req)
    {:status 401 :body "Unauthorized"}
    ;; 处理逻辑
    ))
```

---

## 错误处理规范

### 后端错误处理

```clojure
;; ✅ 推荐：使用 try-catch 捕获异常
(defn sync-file [request]
  (try
    (let [result (process-sync request)]
      {:status 200 :body {:success true :data result}})
    (catch Exception e
      (log/error e "Sync failed")
      {:status 500 :body {:success false :error (.getMessage e)}})))

;; ✅ 推荐：区分不同类型的错误
(defn get-vault [vault-id]
  (if-let [vault (db/get-vault-by-id vault-id)]
    {:status 200 :body vault}
    {:status 404 :body {:error "Vault not found"}}))
```

### 前端错误处理

```clojure
;; ✅ ClojureScript 错误处理
(defn sync-file [file-data]
  (go
    (try
      (let [response (<! (http/post url {:json-params file-data}))]
        (if (:success response)
          (show-notification "同步成功" :success)
          (show-notification (:error response) :error)))
      (catch js/Error e
        (show-notification (str "网络错误: " (.-message e)) :error)))))
```

---

## 安全规范

### 密码处理

```clojure
;; ✅ 推荐：使用 bcrypt 哈希密码
(defn create-user [username password]
  (let [hash (buddy.hashers/derive password {:alg :bcrypt+sha512})]
    (db/insert-user! username hash)))

;; ❌ 危险：明文存储密码
(defn create-user [username password]
  (db/insert-user! username password))
```

### Session 安全

```clojure
;; ✅ 推荐：使用安全的 session 配置
{:store (cookie/cookie-store {:key secret-key})
 :cookie-attrs {:http-only true    ; 防止 XSS 攻击
                :secure true        ; 仅 HTTPS
                :same-site :lax}}   ; 防止 CSRF
```

### 输入验证

```clojure
;; ✅ 推荐：验证所有输入
(defn create-vault [request]
  (let [{:keys [name domain]} (:body-params request)]
    (cond
      (str/blank? name)
      {:status 400 :body {:error "Vault name is required"}}

      (not (valid-domain? domain))
      {:status 400 :body {:error "Invalid domain format"}}

      :else
      (do-create-vault name domain))))
```

---

## 测试规范

### 单元测试

```clojure
;; ✅ 使用 clojure.test
(ns markdownbrain.utils-test
  (:require [clojure.test :refer [deftest is testing]]
            [markdownbrain.utils :as utils]))

(deftest test-generate-uuid
  (testing "UUID generation"
    (let [uuid (utils/generate-uuid)]
      (is (string? uuid))
      (is (= 36 (count uuid))))))

(deftest test-hash-password
  (testing "Password hashing"
    (let [password "test123"
          hash (utils/hash-password password)]
      (is (not= password hash))
      (is (utils/verify-password password hash)))))
```

### 集成测试

```clojure
;; ✅ 测试 API 端点
(deftest test-create-vault-api
  (testing "POST /api/admin/vaults"
    (let [response (app {:request-method :post
                        :uri "/api/admin/vaults"
                        :body-params {:name "Test" :domain "test.com"}
                        :session {:tenant-id "tenant-123"}})]
      (is (= 200 (:status response)))
      (is (get-in response [:body :success])))))
```

---

## 性能优化建议

### 数据库优化

```clojure
;; ✅ 批量插入
(defn batch-insert-documents! [documents]
  (jdbc/with-transaction [tx datasource]
    (doseq [doc documents]
      (insert-document! tx doc))))

;; ✅ 使用连接池
(def datasource
  (hikari-cp/make-datasource
    {:adapter "sqlite"
     :database-name "markdownbrain.db"
     :maximum-pool-size 10}))
```

### 前端优化

```clojure
;; ✅ 使用 HTMX 减少 JavaScript
<button hx-post="/api/sync"
        hx-target="#result"
        hx-swap="innerHTML">
  同步
</button>

;; ✅ 懒加载文档列表
<div hx-get="/api/documents"
     hx-trigger="intersect once"
     hx-target="this">
  加载中...
</div>
```

---

## Git 提交规范

### Commit Message 格式

```bash
# ✅ 推荐格式
<type>(<scope>): <subject>

<body>

<footer>

# 示例
feat(backend): add vault creation API

- Implement POST /api/admin/vaults endpoint
- Add UUID generation for sync token
- Add DNS record generation

Closes #123
```

### Type 类型

- `feat`: 新功能
- `fix`: Bug 修复
- `docs`: 文档更新
- `style`: 代码格式（不影响功能）
- `refactor`: 重构
- `test`: 测试相关
- `chore`: 构建工具、依赖更新

---

## 文档规范

### 代码注释

```clojure
;; ✅ 推荐：简洁清晰的注释
(defn generate-dns-record
  "生成 DNS 配置说明文本。

  参数:
    domain - 域名（如 blog.example.com）
    server-ip - 服务器 IP 地址

  返回:
    包含 A 记录和 CNAME 记录配置的字符串"
  [domain server-ip]
  ...)

;; ❌ 不推荐：多余的注释
(defn add [x y]
  ;; 将 x 和 y 相加
  (+ x y))  ; 执行加法操作
```

### README 文档

每个子模块应包含 README：
- 模块用途说明
- 安装/构建步骤
- 使用示例
- API 文档链接

---

## 开发工具推荐

### Clojure 开发

- **IDE**: IntelliJ IDEA + Cursive / Emacs + CIDER / VS Code + Calva
- **REPL**: 实时开发和调试
- **Linter**: clj-kondo
- **Formatter**: cljfmt

### 调试技巧

```clojure
;; ✅ 使用 tap>
(tap> {:vault vault :documents docs})

;; ✅ 使用 println 调试（开发环境）
(println "DEBUG:" vault-id sync-token)

;; ✅ 使用 log（生产环境）
(log/debug "Processing vault" {:vault-id vault-id})
```

---

## 持续集成建议

```yaml
# .github/workflows/test.yml
name: Test

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - uses: actions/setup-java@v2
        with:
          java-version: '11'
      - uses: DeLaGuardo/setup-clojure@v1
        with:
          cli: latest
      - run: clj -M:test
```

---

**遵循这些规范可以提高代码质量和团队协作效率！**
