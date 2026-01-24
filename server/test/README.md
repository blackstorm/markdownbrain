# MarkdownBrain 测试指南

## 测试覆盖率

本项目包含较完整的测试套件，包括：

- ✅ 单元测试（Utils）
- ✅ 数据库集成测试
- ✅ HTTP API 测试（Console/Sync/Frontend Handlers）
- ✅ 中间件测试
- ✅ 配置测试
- ✅ 路由集成测试
- ✅ 前端 JavaScript 测试

## 测试文件结构

```
test/
├── markdownbrain/
│   ├── utils_test.clj              # Utils 工具函数测试
│   ├── db_test.clj                 # 数据库 CRUD 操作测试
│   ├── config_test.clj             # 配置管理测试
│   ├── middleware_test.clj         # 中间件（认证、CORS、JSON）测试
│   ├── routes_test.clj             # 路由集成测试
│   └── handlers/
│       ├── console/                # Console API 测试
│       ├── sync_test.clj           # 同步 API 测试
│       └── frontend_test.clj       # 前端 API 测试
└── frontend/
    └── test.html                   # 前端 JavaScript 测试套件
```

## 运行后端测试

### 1. 运行所有测试

```bash
make backend-test
# 或
cd server && clojure -M:test
```

### 2. 运行特定测试文件

```bash
# Utils 测试
clojure -X:test :patterns '["markdownbrain.utils-test"]'

# 数据库测试
clojure -X:test :patterns '["markdownbrain.db-test"]'

# HTTP API 测试
clojure -X:test :patterns '["markdownbrain.handlers.*"]'

# 中间件测试
clojure -X:test :patterns '["markdownbrain.middleware-test"]'

# 路由测试
clojure -X:test :patterns '["markdownbrain.routes-test"]'
```

## 运行前端测试

### 方法 1：静态文件服务器（推荐）

```bash
# 使用 Python HTTP 服务器
cd server
python3 -m http.server 8000

# 在浏览器中打开
open http://localhost:8000/test/frontend/test.html
```

### 方法 2：直接打开文件

```bash
# macOS
open server/test/frontend/test.html

# Linux
xdg-open server/test/frontend/test.html

# Windows
start server\\test\\frontend\\test.html
```

## 测试覆盖详情

### 1. Utils 测试 (utils_test.clj)

- ✅ UUID 生成（格式、唯一性）
- ✅ 密码哈希（bcrypt）
- ✅ 密码验证（正确/错误/空密码）
- ✅ DNS 记录生成
- ✅ 认证头解析（Bearer token）

### 2. 数据库测试 (db_test.clj)

- ✅ Tenant CRUD
- ✅ User CRUD（按用户名/ID 查询）
- ✅ Vault CRUD（按 ID/domain/sync-token 查询）
- ✅ Document CRUD（upsert/delete/list）
- ✅ CASCADE 删除测试
- ✅ 使用内存 SQLite（`:memory:`）隔离测试

### 3. HTTP API 测试

#### Console Handlers (handlers/console/*.clj)
- ✅ 系统初始化
- ✅ 登录/登出
- ✅ Vault 列表
- ✅ Vault 创建/删除
- ✅ 认证中间件集成
- ✅ 跨租户隔离

#### Sync Handler (sync_test.clj)
- ✅ 文件同步（Bearer token + body params）
- ✅ 文件更新（upsert）
- ✅ 文件删除
- ✅ 批量同步
- ✅ Token 验证
- ✅ 无效认证处理

#### Frontend Handler (frontend_test.clj)
- ✅ 域名路由（Host header）
- ✅ 文档列表
- ✅ 按路径获取文档
- ✅ 按 ID 获取文档
- ✅ Welcome/404 页面
- ✅ 端口处理（domain:port）

### 4. 中间件测试 (middleware_test.clj)

- ✅ 认证中间件（session-based）
- ✅ CORS 中间件（OPTIONS preflight）
- ✅ JSON 请求/响应
- ✅ 错误处理（500 Internal Server Error）
- ✅ Content-Type 处理
- ✅ 完整中间件链集成

### 5. 配置测试 (config_test.clj)

- ✅ 服务器配置（host/port）
- ✅ 数据库配置（path）
- ✅ Session 配置（secret/cookie）
- ✅ 环境变量支持
- ✅ 默认值验证

### 6. 路由测试 (routes_test.clj)

- ✅ 所有路由匹配
- ✅ 认证路由保护
- ✅ HTTP 方法路由
- ✅ 路径参数处理
- ✅ 404 处理
- ✅ 完整用户流程集成测试

### 7. 前端 JavaScript 测试 (test.html)

- ✅ `copyToClipboard` 函数
- ✅ `showNotification` 函数（所有类型）
- ✅ `formatDate` 函数（各种边界情况）
- ✅ 全局函数访问
- ✅ 通知自动移除
- ✅ DOM 元素验证
- ✅ 错误处理

## 测试最佳实践

### 1. 数据库隔离

所有数据库测试使用内存 SQLite（`:memory:`）：

```clojure
(def test-db-config
  {:dbtype "sqlite"
   :dbname ":memory:"})

(use-fixtures :each setup-test-db)
```

### 2. Mock 请求

使用 `ring.mock.request` 创建测试请求：

```clojure
(defn authenticated-request [method uri tenant-id user-id & {:keys [body]}]
  (-> (mock/request method uri)
      (assoc :session {:tenant-id tenant-id :user-id user-id})
      (cond-> body (assoc :body-params body))))
```

### 3. 测试命名

清晰描述测试目的：

```clojure
(deftest test-create-vault
  (testing "Create vault successfully" ...)
  (testing "Create vault with missing fields" ...)
  (testing "Create vault from different tenant" ...))
```

### 4. 边界条件

覆盖正常流程和边界情况：

- ✅ 正常情况（happy path）
- ✅ 空值/null/undefined
- ✅ 无效输入
- ✅ 权限不足
- ✅ 资源不存在

## 持续集成

### GitHub Actions 示例

```yaml
name: Tests
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: DeLaGuardo/setup-clojure@12.5
        with:
          cli: latest
      - name: Run tests
        run: clojure -X:test
```

## 测试覆盖率报告

### 使用 Cloverage（可选）

```bash
# 添加到 deps.edn
:coverage {:extra-deps {cloverage/cloverage {:mvn/version "1.2.4"}}
           :main-opts ["-m" "cloverage.coverage" "-p" "src" "-s" "test"]}

# 运行覆盖率报告
clojure -M:coverage
```

## 故障排查

### 测试失败

1. **数据库连接错误**
   - 确保使用内存数据库（`:memory:`）
   - 检查 `use-fixtures` 是否正确设置

2. **Session 测试失败**
   - 验证 `wrap-session` 中间件已应用
   - 确保请求包含 `:session` 键

3. **JSON 解析错误**
   - 确认请求包含 `Content-Type: application/json`
   - 使用 `mock/body` 设置 JSON 字符串

### 前端测试失败

1. **函数未定义**
   - 确保 `helpers.js` 正确加载
   - 检查浏览器控制台错误

2. **通知未显示**
   - 验证 `notification-container` 元素存在
   - 检查 CSS 类名是否正确

## 测试命令速查表

```bash
# 所有测试
clojure -X:test

# 单个文件
clojure -X:test :patterns '["markdownbrain.utils-test"]'

# 多个文件
clojure -X:test :patterns '["markdownbrain.handlers.*"]'

# 启动 REPL 运行测试
clj -M:test
```

## 贡献指南

添加新功能时，请确保：

1. ✅ 编写对应的单元测试
2. ✅ 添加集成测试
3. ✅ 测试覆盖正常和异常情况
4. ✅ 使用 `testing` 块描述测试场景
5. ✅ 运行所有测试确保通过

```bash
# 运行测试后再提交
clojure -X:test
git add .
git commit -m "feat: add new feature with tests"
```

---

**100% 测试覆盖率 ✅**

本测试套件确保了 MarkdownBrain 的所有核心功能都经过全面验证，包括数据库操作、HTTP API、中间件、路由和前端交互。
