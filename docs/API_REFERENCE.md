# 完整 API 参考手册

## 概述

MarkdownBrain API 分为三类：
1. **管理员 API** - 需要 Session 认证
2. **同步 API** - 需要 Token 认证（vault_id + sync_token）
3. **前端 API** - 基于域名路由

**Base URL**: `https://api.yourdomain.com`

---

## 1. 管理员 API

### 1.1 初始化管理员

**首次部署时创建管理员用户和租户**

```http
POST /api/admin/init
Content-Type: application/json
```

**请求体**:
```json
{
  "username": "admin",
  "password": "your-secure-password",
  "tenant_name": "My Organization"
}
```

**响应 200**:
```json
{
  "success": true,
  "tenant_id": "550e8400-e29b-41d4-a716-446655440000",
  "user_id": "660e8400-e29b-41d4-a716-446655440000"
}
```

**响应 400** (用户已存在):
```json
{
  "success": false,
  "error": "用户名已存在"
}
```

---

### 1.2 管理员登录

```http
POST /api/admin/login
Content-Type: application/json
```

**请求体**:
```json
{
  "username": "admin",
  "password": "your-password"
}
```

**响应 200**:
```json
{
  "success": true,
  "user": {
    "id": "660e8400-e29b-41d4-a716-446655440000",
    "username": "admin",
    "tenant_id": "550e8400-e29b-41d4-a716-446655440000"
  }
}
```

**响应 401** (认证失败):
```json
{
  "success": false,
  "error": "用户名或密码错误"
}
```

**说明**:
- 登录成功后会设置 Session Cookie
- Cookie 名称: `markdownbrain-session`
- 有效期: 7 天

---

### 1.3 管理员登出

```http
POST /api/admin/logout
Cookie: markdownbrain-session=<session-id>
```

**响应 200**:
```json
{
  "success": true
}
```

---

### 1.4 列出所有 Vault

**需要认证**

```http
GET /api/admin/vaults
Cookie: markdownbrain-session=<session-id>
```

**响应 200**:
```json
[
  {
    "id": "vault-uuid-1",
    "name": "My Blog",
    "domain": "blog.example.com",
    "sync_token": "token-uuid-1",
    "domain_record": "请在您的 DNS 服务商添加以下记录：\n\n类型: A\n主机: blog\n值: 123.45.67.89\nTTL: 3600",
    "created_at": "2025-12-21T10:00:00Z"
  },
  {
    "id": "vault-uuid-2",
    "name": "Documentation",
    "domain": "docs.example.com",
    "sync_token": "token-uuid-2",
    "domain_record": "...",
    "created_at": "2025-12-20T15:30:00Z"
  }
]
```

**响应 401** (未认证):
```json
{
  "error": "Unauthorized",
  "message": "请先登录"
}
```

---

### 1.5 创建 Vault

**需要认证**

```http
POST /api/admin/vaults
Content-Type: application/json
Cookie: markdownbrain-session=<session-id>
```

**请求体**:
```json
{
  "name": "My Blog",
  "domain": "blog.example.com"
}
```

**响应 200**:
```json
{
  "success": true,
  "vault": {
    "id": "vault-uuid-1",
    "name": "My Blog",
    "domain": "blog.example.com",
    "sync_token": "token-uuid-1",
    "domain_record": "请在您的 DNS 服务商添加以下记录：\n\n类型: A\n主机: blog\n值: 123.45.67.89\nTTL: 3600\n\n或\n\n类型: CNAME\n主机: blog\n值: server.yourdomain.com\nTTL: 3600"
  }
}
```

**字段说明**:
- `id`: Vault 唯一标识符
- `sync_token`: 用于 Obsidian 插件同步的 Token（保密）
- `domain_record`: DNS 配置说明（管理员需手动配置 DNS）

---

## 2. 同步 API

### 2.1 同步文件

**由 Obsidian 插件调用，使用 Token 认证**

```http
POST /api/sync
Content-Type: application/json
Authorization: Bearer <vault-id>:<sync-token>
```

**请求体** (创建/修改):
```json
{
  "vault_id": "vault-uuid-1",
  "sync_token": "token-uuid-1",
  "path": "notes/2025/daily-note.md",
  "content": "# Daily Note\n\nToday I learned...",
  "metadata": "{\"tags\":[\"daily\",\"learning\"]}",
  "hash": "a1b2c3d4",
  "mtime": "2025-12-21T10:30:00Z",
  "action": "create"
}
```

**请求体** (删除):
```json
{
  "vault_id": "vault-uuid-1",
  "sync_token": "token-uuid-1",
  "path": "notes/2025/old-note.md",
  "action": "delete"
}
```

**字段说明**:
- `vault_id`: Vault ID（从管理后台获取）
- `sync_token`: 同步 Token（从管理后台获取）
- `path`: 文件路径（相对于 Vault 根目录）
- `content`: 文件内容（Markdown 文本）
- `metadata`: JSON 字符串，存储元数据（可选）
- `hash`: 文件内容哈希（用于检测变化）
- `mtime`: 文件修改时间（ISO 8601 格式）
- `action`: 操作类型
  - `create`: 创建新文件
  - `modify`: 修改现有文件
  - `delete`: 删除文件

**响应 200**:
```json
{
  "success": true,
  "vault_id": "vault-uuid-1",
  "path": "notes/2025/daily-note.md",
  "action": "create"
}
```

**响应 401** (Token 无效):
```json
{
  "success": false,
  "error": "Invalid vault_id or sync_token"
}
```

**认证方式**:
1. **推荐**: 使用 `Authorization` header
2. **备用**: 在请求体中包含 `vault_id` 和 `sync_token`

---

## 3. 前端 API

### 3.1 获取 Vault 首页

**通过域名访问，Nginx 根据 Host header 路由**

```http
GET /
Host: blog.example.com
```

**响应 200**: HTML 页面（包含 Vault 信息和文档列表）

**响应 404**: Vault 不存在

---

### 3.2 获取文档列表

```http
GET /api/documents
Host: blog.example.com
```

**响应 200**:
```json
[
  {
    "id": "doc-uuid-1",
    "path": "README.md",
    "hash": "a1b2c3d4",
    "mtime": "2025-12-21T10:00:00Z"
  },
  {
    "id": "doc-uuid-2",
    "path": "notes/2025/daily-note.md",
    "hash": "e5f6g7h8",
    "mtime": "2025-12-21T11:30:00Z"
  }
]
```

**响应 404**:
```json
{
  "error": "Vault not found"
}
```

---

### 3.3 获取单个文档

```http
GET /api/documents/{document-id}
Host: blog.example.com
```

**响应 200**:
```json
{
  "id": "doc-uuid-1",
  "path": "README.md",
  "content": "# Welcome\n\nThis is my vault.",
  "metadata": "{\"tags\":[\"welcome\"]}",
  "hash": "a1b2c3d4",
  "mtime": "2025-12-21T10:00:00Z"
}
```

**响应 404**:
```json
{
  "error": "Document not found"
}
```

---

## 4. 错误码

| HTTP 状态码 | 说明 |
|------------|------|
| 200 | 请求成功 |
| 400 | 请求参数错误 |
| 401 | 未授权（未登录或 Token 无效）|
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

---

## 5. 数据模型

### 5.1 Tenant（租户）

```json
{
  "id": "string (UUID)",
  "name": "string",
  "created_at": "string (ISO 8601)"
}
```

### 5.2 User（用户）

```json
{
  "id": "string (UUID)",
  "tenant_id": "string (UUID)",
  "username": "string",
  "role": "string (admin)",
  "created_at": "string (ISO 8601)"
}
```

### 5.3 Vault

```json
{
  "id": "string (UUID)",
  "tenant_id": "string (UUID)",
  "name": "string",
  "domain": "string",
  "sync_token": "string (UUID)",
  "domain_record": "string",
  "created_at": "string (ISO 8601)"
}
```

### 5.4 Document（文档）

```json
{
  "id": "string (UUID)",
  "tenant_id": "string (UUID)",
  "vault_id": "string (UUID)",
  "path": "string",
  "content": "string (Markdown)",
  "metadata": "string (JSON)",
  "hash": "string",
  "mtime": "string (ISO 8601)",
  "updated_at": "string (ISO 8601)"
}
```

---

## 6. 使用示例

### 6.1 完整工作流（curl）

```bash
# 1. 初始化管理员
curl -X POST http://localhost:3000/api/admin/init \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123","tenant_name":"My Org"}'

# 2. 登录（保存 cookies）
curl -X POST http://localhost:3000/api/admin/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  -c cookies.txt

# 3. 创建 Vault
curl -X POST http://localhost:3000/api/admin/vaults \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{"name":"Blog","domain":"blog.example.com"}'

# 输出包含 vault_id 和 sync_token

# 4. 同步文件（模拟 Obsidian 插件）
curl -X POST http://localhost:3000/api/sync \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <vault-id>:<sync-token>" \
  -d '{
    "path": "test.md",
    "content": "# Test",
    "hash": "abc123",
    "mtime": "2025-12-21T10:00:00Z",
    "action": "create"
  }'

# 5. 查看文档列表
curl http://localhost:3000/api/documents \
  -H "Host: blog.example.com"
```

### 6.2 JavaScript 示例

```javascript
// 登录
async function login(username, password) {
  const response = await fetch('/api/admin/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
    credentials: 'include' // 包含 cookies
  });
  return response.json();
}

// 创建 Vault
async function createVault(name, domain) {
  const response = await fetch('/api/admin/vaults', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, domain }),
    credentials: 'include'
  });
  return response.json();
}

// 同步文件
async function syncFile(vaultId, syncToken, file) {
  const response = await fetch('/api/sync', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${vaultId}:${syncToken}`
    },
    body: JSON.stringify(file)
  });
  return response.json();
}
```

---

## 7. 速率限制（待实现）

当前版本无速率限制。未来版本可能添加：
- 管理员 API: 100 请求/分钟
- 同步 API: 1000 请求/分钟/vault
- 前端 API: 无限制

---

## 8. Webhook（待实现）

未来版本可能支持：
- 文档更新 webhook
- Vault 创建 webhook
- 同步状态 webhook
