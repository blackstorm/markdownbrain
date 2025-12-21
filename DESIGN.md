# MarkdownBrain MVP - 完整设计文档

## 技术栈确认

- **后端**: Clojure + Reitit + Ring + SQLite
- **前端**: ClojureScript helpers + HTMX + TailwindCSS
- **Obsidian 插件**: TypeScript wrapper + ClojureScript 核心逻辑
- **认证**: Session-based (管理员)
- **同步认证**: UUID sync_token
- **文件存储**: SQLite documents.content 字段
- **域名路由**: Nginx/Caddy 反向代理 + Host header
- **DNS**: 显示待配置记录（手动配置）

---

## 1. 数据库 Schema (SQLite)

```sql
-- 租户表（每个用户/组织一个）
CREATE TABLE tenants (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 管理员用户
CREATE TABLE users (
  id TEXT PRIMARY KEY,
  tenant_id TEXT NOT NULL,
  username TEXT UNIQUE NOT NULL,
  password_hash TEXT NOT NULL,
  role TEXT DEFAULT 'admin',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (tenant_id) REFERENCES tenants(id)
);

-- Vault 表（一个租户多个 vault）
CREATE TABLE vaults (
  id TEXT PRIMARY KEY,
  tenant_id TEXT NOT NULL,
  name TEXT NOT NULL,
  domain TEXT UNIQUE,
  sync_token TEXT UNIQUE NOT NULL,
  domain_record TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (tenant_id) REFERENCES tenants(id)
);

CREATE INDEX idx_vaults_domain ON vaults(domain);

-- 文档表（Markdown 文件）
CREATE TABLE documents (
  id TEXT PRIMARY KEY,
  tenant_id TEXT NOT NULL,
  vault_id TEXT NOT NULL,
  path TEXT NOT NULL,
  content TEXT,
  metadata TEXT,
  hash TEXT,
  mtime TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(vault_id, path),
  FOREIGN KEY (tenant_id) REFERENCES tenants(id),
  FOREIGN KEY (vault_id) REFERENCES vaults(id) ON DELETE CASCADE
);

CREATE INDEX idx_documents_vault ON documents(vault_id);
CREATE INDEX idx_documents_mtime ON documents(mtime);
```

---

## 2. 后端 API 设计

### 2.1 管理员 API（需要 Session 认证）

| 端点 | 方法 | 描述 | 请求体 | 响应 |
|------|------|------|--------|------|
| `/api/admin/init` | POST | 初始化管理员 | `{username, password, tenant_name}` | `{success: true}` |
| `/api/admin/login` | POST | 管理员登录 | `{username, password}` | `{success: true, user: {...}}` |
| `/api/admin/logout` | POST | 管理员登出 | - | `{success: true}` |
| `/api/admin/vaults` | GET | 列出所有 vault | - | `[{id, name, domain, sync_token, domain_record}]` |
| `/api/admin/vaults` | POST | 创建 vault | `{name, domain}` | `{id, sync_token, domain_record}` |

### 2.2 同步 API（Token 认证）

| 端点 | 方法 | 描述 | 请求 | 响应 |
|------|------|------|------|------|
| `/api/sync` | POST | 同步文件 | `{vault_id, sync_token, path, content, metadata, hash, mtime, action}` | `{success: true}` |

**action 值**: `create`, `modify`, `delete`

**认证方式**:
- Header: `Authorization: Bearer {vault_id}:{sync_token}`
- 或 Body 中包含 `vault_id` 和 `sync_token`

### 2.3 前端展示 API（基于域名路由）

| 端点 | 方法 | 描述 | 响应 |
|------|------|------|------|
| `/` | GET | 首页 - 展示当前域名对应的 vault | HTML |
| `/api/documents` | GET | 获取当前 vault 的文档列表 | `[{id, path, mtime, hash}]` |
| `/api/documents/:id` | GET | 获取单个文档内容 | `{path, content, metadata}` |

---

## 3. 项目结构

```
markdownbrain/
├── deps.edn                          # Clojure 依赖
├── shadow-cljs.edn                   # ClojureScript 构建配置
├── tailwind.config.js                # TailwindCSS 配置
├── package.json                      # NPM 依赖（TailwindCSS, HTMX）
├── resources/
│   ├── migrations/
│   │   └── 001-initial-schema.sql   # 数据库初始化
│   ├── public/
│   │   ├── css/
│   │   │   └── app.css              # 编译后的 Tailwind
│   │   └── js/
│   │       ├── htmx.min.js          # HTMX
│   │       └── app.js               # 编译后的 ClojureScript
│   └── templates/
│       ├── base.html                # 基础模板
│       ├── admin/
│       │   ├── login.html           # 管理员登录
│       │   └── vaults.html          # Vault 管理
│       └── frontend/
│           ├── home.html            # Vault 首页
│           └── documents.html       # 文档列表
├── src/
│   ├── markdownbrain/
│   │   ├── core.clj                 # 主入口
│   │   ├── config.clj               # 配置
│   │   ├── db.clj                   # 数据库层
│   │   ├── routes.clj               # Reitit 路由
│   │   ├── middleware.clj           # Ring 中间件
│   │   ├── utils.clj                # 工具函数
│   │   └── handlers/
│   │       ├── admin.clj            # 管理员处理器
│   │       ├── sync.clj             # 同步处理器
│   │       └── frontend.clj         # 前端处理器
│   └── markdownbrain_frontend/
│       └── core.cljs                # ClojureScript 辅助函数
└── obsidian-plugin/
    ├── package.json                 # NPM 依赖
    ├── tsconfig.json                # TypeScript 配置
    ├── manifest.json                # Obsidian 插件 manifest
    ├── main.ts                      # TypeScript 入口
    └── src/
        └── sync.cljs                # ClojureScript 同步逻辑
```

---

## 4. 数据流图

```
┌─────────────────────────────────────────────────────────┐
│                   Obsidian Vault                        │
│               (用户编辑 Markdown 文件)                   │
└───────────────────────┬─────────────────────────────────┘
                        │ 文件变化事件 (create/modify/delete)
                        ▼
┌─────────────────────────────────────────────────────────┐
│         Obsidian Plugin (TypeScript Wrapper)            │
│  - 监听 vault.on('create', 'modify', 'delete')          │
│  - 读取文件内容、路径、修改时间                          │
│  - 传递给 ClojureScript 核心逻辑                        │
└───────────────────────┬─────────────────────────────────┘
                        │ 调用 ClojureScript
                        ▼
┌─────────────────────────────────────────────────────────┐
│           sync.cljs (ClojureScript 核心)                │
│  - 准备数据: {vault_id, sync_token, path, content...}  │
│  - POST /api/sync                                       │
│  - 处理响应、错误重试                                    │
└───────────────────────┬─────────────────────────────────┘
                        │ HTTPS Request
                        ▼
┌─────────────────────────────────────────────────────────┐
│              Server: /api/sync Handler                  │
│  1. 验证 sync_token                                     │
│  2. 从 vaults 表获取 tenant_id                          │
│  3. 根据 action 操作 documents 表                       │
│     - create/modify: UPSERT                             │
│     - delete: DELETE                                    │
└───────────────────────┬─────────────────────────────────┘
                        │ SQL 操作
                        ▼
┌─────────────────────────────────────────────────────────┐
│                  SQLite Database                        │
│  documents(tenant_id, vault_id, path, content, mtime)  │
└───────────────────────┬─────────────────────────────────┘
                        │ 查询
                        ▼
┌─────────────────────────────────────────────────────────┐
│        前端用户访问 vault.example.com                    │
│  1. Nginx 根据 Host header 转发到后端                   │
│  2. 后端根据 domain 查询 vaults 表获取 vault_id         │
│  3. 查询 documents 表获取文件列表                        │
│  4. 使用 HTMX 渲染 HTML                                 │
└─────────────────────────────────────────────────────────┘
```

---

## 5. 多租户安全流程

```
Plugin 同步请求
  ├─ POST /api/sync
  ├─ Header: Authorization: Bearer {vault_id}:{sync_token}
  └─ Body: {path, content, metadata, hash, mtime, action}
       │
       ▼
  验证 sync_token
       │
       ├─ SQL: SELECT tenant_id, id FROM vaults
       │       WHERE id = ? AND sync_token = ?
       │
       ├─ 无效 → 401 Unauthorized
       │
       └─ 有效 → 提取 tenant_id + vault_id
            │
            ▼
       操作 documents 表
            ├─ tenant_id（租户隔离）
            ├─ vault_id（vault 分组）
            ├─ path, content, mtime
            └─ UNIQUE(vault_id, path) 约束
```

---

## 6. 域名路由实现

### 6.1 Nginx 配置示例

```nginx
# 泛域名配置 - 用户的 vault 域名
server {
    listen 80;
    server_name *.example.com;

    location / {
        proxy_pass http://localhost:3000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}

# 管理后台域名
server {
    listen 80;
    server_name admin.example.com;

    location / {
        proxy_pass http://localhost:3000;
        proxy_set_header Host $host;
    }
}
```

### 6.2 后端路由逻辑

```clojure
(defn get-vault-by-domain [domain]
  (db/query-one ["SELECT * FROM vaults WHERE domain = ?" domain]))

(defn frontend-handler [req]
  (let [host (get-in req [:headers "host"])
        vault (get-vault-by-domain host)]
    (if vault
      {:status 200
       :body (render-template "frontend/home.html" {:vault vault})}
      {:status 404
       :body "Vault not found"})))
```

---

## 7. DNS 记录显示

当管理员创建 vault 时，系统生成 DNS 配置信息：

```
Vault: my-blog
Domain: blog.example.com
Sync Token: a1b2c3d4-e5f6-7890-abcd-ef1234567890

请在您的 DNS 服务商添加以下记录：

类型: A
主机: blog
值: 123.45.67.89 (服务器 IP)
TTL: 3600

或

类型: CNAME
主机: blog
值: server.example.com
TTL: 3600
```

---

## 下一步

本文档提供了完整的架构设计。接下来将提供：

1. ✅ 完整的 Clojure 后端代码示例
2. ✅ ClojureScript 前端 HTMX 集成示例
3. ✅ Obsidian 插件（TypeScript + ClojureScript）示例
4. ✅ 构建配置文件（deps.edn, shadow-cljs.edn）
5. ✅ 部署说明
