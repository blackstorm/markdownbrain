# MarkdownBrain MVP - 快速开始指南

## 项目概述

MarkdownBrain 是一个基于 Clojure/ClojureScript 的全栈系统，用于将 Obsidian Vault 同步到服务器并展示为网站。

**核心特性**:
- 单向同步：Obsidian → Server
- 多租户支持：一个租户多个 Vault
- 域名路由：每个 Vault 独立域名访问
- 管理后台：创建 Vault、查看 DNS 配置
- 安全认证：Session（管理员）+ Token（插件同步）

---

## 文档导航

| 文档 | 说明 |
|------|------|
| [DESIGN.md](DESIGN.md) | 完整架构设计 |
| [BACKEND_CODE.md](docs/BACKEND_CODE.md) | 后端 Clojure 代码示例 |
| [FRONTEND_CODE.md](docs/FRONTEND_CODE.md) | 前端 ClojureScript + HTMX 示例 |
| [OBSIDIAN_PLUGIN.md](docs/OBSIDIAN_PLUGIN.md) | Obsidian 插件代码示例 |
| [API_REFERENCE.md](docs/API_REFERENCE.md) | 完整 API 参考手册 |
| [DEPLOYMENT.md](docs/DEPLOYMENT.md) | 部署和运维指南 |

---

## 5 分钟快速开始

### 1. 环境准备

```bash
# 安装依赖
# - Java 11+ (Clojure)
# - Node.js 18+ (ClojureScript, TailwindCSS)
# - Clojure CLI (https://clojure.org/guides/install_clojure)

# 验证安装
java -version
node -version
clj -version
```

### 2. 克隆项目

```bash
git clone https://github.com/yourname/markdownbrain.git
cd markdownbrain
```

### 3. 后端启动

```bash
# 下载 Clojure 依赖
clj -P

# 启动服务器（自动初始化数据库）
clj -M -m markdownbrain.core
```

输出：
```
Initializing database...
Starting server on 0.0.0.0 : 3000
```

### 4. 前端构建

```bash
# 安装 NPM 依赖
npm install

# 开发模式（自动监听文件变化）
npm run watch          # ClojureScript
npm run tailwind:watch # TailwindCSS

# 复制 HTMX
cp node_modules/htmx.org/dist/htmx.min.js resources/public/js/
```

### 5. 初始化管理员

```bash
curl -X POST http://localhost:3000/api/admin/init \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "admin123",
    "tenant_name": "My Organization"
  }'
```

### 6. 访问管理后台

打开浏览器：`http://localhost:3000/admin/login`

- 用户名: `admin`
- 密码: `admin123`

### 7. 创建 Vault

在管理后台点击"创建 Vault"，填写：
- 名称: `My Blog`
- 域名: `blog.localhost` (开发环境)

系统会返回：
- Vault ID
- Sync Token
- DNS 配置说明

### 8. 配置 Obsidian 插件

```bash
cd obsidian-plugin
npm install
npm run build

# 复制到 Obsidian vault
cp -r . /path/to/your/vault/.obsidian/plugins/markdownbrain-sync/
```

在 Obsidian 中：
1. Settings → Community plugins → MarkdownBrain Sync
2. 配置：
   - 服务器地址: `http://localhost:3000`
   - Vault ID: （从管理后台复制）
   - Sync Token: （从管理后台复制）
3. 启用"自动同步"

### 9. 测试同步

在 Obsidian 中创建或修改 Markdown 文件，插件会自动同步到服务器。

### 10. 查看前端

访问：`http://blog.localhost:3000`

（注意：需要配置本地 hosts 或使用真实域名）

---

## 生产部署

详见 [DEPLOYMENT.md](docs/DEPLOYMENT.md)，关键步骤：

1. **配置环境变量**:
   ```bash
   export DB_PATH=/var/www/markdownbrain/data/markdownbrain.db
   export SESSION_SECRET=your-random-secret-key
   export SERVER_IP=123.45.67.89
   ```

2. **配置 Nginx** 反向代理 + 泛域名

3. **配置 SSL** (Let's Encrypt)

4. **创建 Systemd 服务** 开机自启动

5. **配置自动备份** (cron + SQLite backup)

---

## 开发工作流

### 后端开发

```bash
# 启动 REPL
clj

# 加载命名空间
(require '[markdownbrain.core :as core])
(require '[markdownbrain.db :as db])

# 测试数据库操作
(db/list-vaults-by-tenant "tenant-id")
```

### 前端开发

```bash
# 监听文件变化
npm run watch
npm run tailwind:watch

# 浏览器自动刷新：访问 http://localhost:8020
```

### 插件开发

```bash
cd obsidian-plugin
npm run dev

# 在 Obsidian 中启用开发者工具查看日志
```

---

## 项目结构

```
markdownbrain/
├── DESIGN.md                 # 架构设计
├── MVP.md                    # 需求文档
├── README.md                 # 本文件
├── deps.edn                  # Clojure 依赖
├── shadow-cljs.edn           # ClojureScript 配置
├── package.json              # NPM 依赖
├── tailwind.config.js        # TailwindCSS 配置
├── docs/                     # 详细文档
│   ├── BACKEND_CODE.md
│   ├── FRONTEND_CODE.md
│   ├── OBSIDIAN_PLUGIN.md
│   ├── API_REFERENCE.md
│   └── DEPLOYMENT.md
├── resources/
│   ├── migrations/           # 数据库迁移
│   ├── public/               # 静态资源
│   └── templates/            # HTML 模板
├── src/
│   ├── markdownbrain/        # 后端 Clojure
│   │   ├── core.clj
│   │   ├── db.clj
│   │   ├── routes.clj
│   │   ├── middleware.clj
│   │   ├── utils.clj
│   │   └── handlers/
│   │       ├── admin.clj
│   │       ├── sync.clj
│   │       └── frontend.clj
│   └── markdownbrain_frontend/  # 前端 ClojureScript
│       └── core.cljs
└── obsidian-plugin/          # Obsidian 插件
    ├── main.ts               # TypeScript 入口
    ├── manifest.json
    └── src/
        └── sync.cljs         # ClojureScript 逻辑
```

---

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Clojure + Reitit + Ring |
| 数据库 | SQLite |
| 前端 | ClojureScript + HTMX + TailwindCSS |
| 插件 | TypeScript + ClojureScript |
| 模板引擎 | Selmer |
| 认证 | Session (Cookie) + Token (UUID) |
| 构建工具 | shadow-cljs |

---

## 常见问题

### Q: 为什么选择 SQLite？
A: MVP 阶段追求简单，SQLite 无需额外配置。后续可迁移到 PostgreSQL。

### Q: 支持双向同步吗？
A: 当前仅支持单向同步（Obsidian → Server）。双向同步需要 CRDT 或冲突解决机制。

### Q: 可以多人协作吗？
A: MVP 不支持。每个 Vault 只有一个 Obsidian 客户端同步。

### Q: 如何备份数据？
A: 参考 [DEPLOYMENT.md](docs/DEPLOYMENT.md) 配置自动备份脚本。

### Q: 插件支持 iOS/Android 吗？
A: 理论支持（Obsidian Mobile），但未充分测试。

---

## 下一步计划

- [ ] 支持全文搜索
- [ ] 支持 Markdown 渲染（前端）
- [ ] 支持文件附件（图片、PDF）
- [ ] 支持双向同步
- [ ] 支持团队协作
- [ ] 支持自定义主题
- [ ] 支持 GraphQL API

---

## 贡献指南

欢迎提交 Issue 和 PR！

1. Fork 项目
2. 创建分支：`git checkout -b feature/your-feature`
3. 提交更改：`git commit -m "Add your feature"`
4. 推送分支：`git push origin feature/your-feature`
5. 创建 Pull Request

---

## 许可证

MIT License

---

## 联系方式

- GitHub: https://github.com/yourname/markdownbrain
- Email: your@email.com

---

**祝您使用愉快！**
