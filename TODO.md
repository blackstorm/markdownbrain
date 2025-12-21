# MarkdownBrain MVP - 实现任务清单

> 📅 创建日期: 2025-12-21
> 🎯 目标: 完成 MarkdownBrain MVP 全栈系统实现

---

## 📊 进度概览

- **总任务数**: 50+
- **已完成**: 0
- **进行中**: 0
- **待开始**: 50+

---

## 🗂️ 任务分类

### 阶段 1: 项目基础设置 (10 tasks)

- [ ] 1.1 创建项目目录结构
- [ ] 1.2 初始化 Git 仓库（如需要）
- [ ] 1.3 创建 `.gitignore` 文件
- [ ] 1.4 创建 `deps.edn` (Clojure 依赖)
- [ ] 1.5 创建 `package.json` (NPM 依赖)
- [ ] 1.6 创建 `shadow-cljs.edn` (ClojureScript 配置)
- [ ] 1.7 创建 `tailwind.config.js` (TailwindCSS 配置)
- [ ] 1.8 创建 `tailwind.css` (入口文件)
- [ ] 1.9 验证 Java/Clojure CLI 安装
- [ ] 1.10 验证 Node.js/npm 安装

---

### 阶段 2: 数据库层 (5 tasks)

- [ ] 2.1 创建 `resources/migrations/001-initial-schema.sql`
- [ ] 2.2 创建 `src/markdownbrain/db.clj` (数据库连接和操作)
- [ ] 2.3 测试数据库初始化
- [ ] 2.4 测试基本 CRUD 操作
- [ ] 2.5 验证索引和外键约束

---

### 阶段 3: 后端核心 (10 tasks)

- [ ] 3.1 创建 `src/markdownbrain/config.clj` (配置管理)
- [ ] 3.2 创建 `src/markdownbrain/utils.clj` (工具函数)
  - [ ] UUID 生成
  - [ ] 密码哈希和验证
  - [ ] DNS 记录生成
  - [ ] Auth header 解析
- [ ] 3.3 创建 `src/markdownbrain/middleware.clj` (中间件)
  - [ ] Session 中间件
  - [ ] 认证中间件
  - [ ] CORS 中间件
  - [ ] JSON 中间件
- [ ] 3.4 创建 `src/markdownbrain/handlers/admin.clj` (管理员处理器)
  - [ ] 初始化管理员
  - [ ] 登录/登出
  - [ ] 列出 Vault
  - [ ] 创建 Vault
- [ ] 3.5 创建 `src/markdownbrain/handlers/sync.clj` (同步处理器)
  - [ ] Token 验证
  - [ ] 文件同步 (create/modify)
  - [ ] 文件删除
- [ ] 3.6 创建 `src/markdownbrain/handlers/frontend.clj` (前端处理器)
  - [ ] 获取当前 Vault
  - [ ] 首页渲染
  - [ ] 文档列表 API
  - [ ] 单个文档 API
- [ ] 3.7 创建 `src/markdownbrain/routes.clj` (路由定义)
- [ ] 3.8 创建 `src/markdownbrain/core.clj` (主入口)
- [ ] 3.9 测试后端启动
- [ ] 3.10 测试所有 API 端点

---

### 阶段 4: 前端模板 (8 tasks)

- [ ] 4.1 创建 `resources/templates/base.html` (基础模板)
- [ ] 4.2 创建 `resources/templates/admin/login.html` (登录页)
- [ ] 4.3 创建 `resources/templates/admin/vaults.html` (Vault 管理)
- [ ] 4.4 创建 `resources/templates/frontend/home.html` (Vault 首页)
- [ ] 4.5 创建 `src/markdownbrain_frontend/core.cljs` (ClojureScript 辅助)
- [ ] 4.6 编译 ClojureScript (shadow-cljs)
- [ ] 4.7 编译 TailwindCSS
- [ ] 4.8 复制 HTMX 到 public/js/

---

### 阶段 5: Obsidian 插件 (8 tasks)

- [ ] 5.1 创建 `obsidian-plugin/manifest.json`
- [ ] 5.2 创建 `obsidian-plugin/package.json`
- [ ] 5.3 创建 `obsidian-plugin/tsconfig.json`
- [ ] 5.4 创建 `obsidian-plugin/shadow-cljs.edn`
- [ ] 5.5 创建 `obsidian-plugin/main.ts` (TypeScript 入口)
- [ ] 5.6 创建 `obsidian-plugin/src/sync.cljs` (ClojureScript 同步逻辑)
- [ ] 5.7 编译插件 (TypeScript + ClojureScript)
- [ ] 5.8 测试插件安装到 Obsidian

---

### 阶段 6: 集成测试 (10 tasks)

- [ ] 6.1 下载所有 Clojure 依赖 (`clj -P`)
- [ ] 6.2 下载所有 NPM 依赖 (`npm install`)
- [ ] 6.3 启动后端服务器
- [ ] 6.4 初始化管理员用户
- [ ] 6.5 测试管理员登录
- [ ] 6.6 测试创建 Vault
- [ ] 6.7 配置本地 hosts (或使用 localhost)
- [ ] 6.8 测试前端访问
- [ ] 6.9 测试插件同步文件
- [ ] 6.10 验证数据库中的数据

---

### 阶段 7: 部署准备 (可选，生产环境)

- [ ] 7.1 配置环境变量
- [ ] 7.2 配置 Nginx 反向代理
- [ ] 7.3 申请 SSL 证书 (Let's Encrypt)
- [ ] 7.4 配置 Systemd 服务
- [ ] 7.5 配置自动备份脚本
- [ ] 7.6 配置日志系统
- [ ] 7.7 配置监控告警
- [ ] 7.8 安全加固（防火墙、权限）

---

## 📝 详细任务说明

### 当前任务: 1.1 创建项目目录结构

**目标**: 创建所有必要的目录

**命令**:
```bash
mkdir -p src/markdownbrain/handlers
mkdir -p src/markdownbrain_frontend
mkdir -p resources/migrations
mkdir -p resources/public/css
mkdir -p resources/public/js
mkdir -p resources/templates/admin
mkdir -p resources/templates/frontend
mkdir -p obsidian-plugin/src
mkdir -p test/markdownbrain
```

**验证**: 运行 `tree -L 3` 或 `ls -R` 查看目录结构

---

## 🎯 里程碑

### 里程碑 1: 后端可运行 ✓
- 完成阶段 1, 2, 3
- 能够启动服务器并访问 API

### 里程碑 2: 前端可访问 ✓
- 完成阶段 4
- 能够在浏览器访问管理后台和前端页面

### 里程碑 3: 插件可同步 ✓
- 完成阶段 5
- 能够在 Obsidian 中安装插件并同步文件

### 里程碑 4: 系统集成完成 ✓
- 完成阶段 6
- 整个流程打通：Obsidian → Server → 前端展示

---

## 🐛 已知问题 / 待解决

### 问题列表

- [ ] 暂无

---

## 💡 优化建议 / 未来功能

### 功能增强

- [ ] 添加全文搜索 (SQLite FTS5)
- [ ] 前端 Markdown 渲染
- [ ] 支持图片上传
- [ ] 支持文件附件
- [ ] 添加双向同步
- [ ] 添加团队协作
- [ ] 添加 GraphQL API
- [ ] Docker 容器化
- [ ] Kubernetes 部署

### 性能优化

- [ ] 添加 Redis 缓存
- [ ] 优化数据库查询
- [ ] 添加 CDN
- [ ] 启用 HTTP/2
- [ ] 压缩静态资源

### 安全增强

- [ ] 添加速率限制
- [ ] 添加 IP 黑名单
- [ ] 添加双因素认证 (2FA)
- [ ] 添加审计日志
- [ ] 定期安全扫描

---

## 📊 时间估算

| 阶段 | 预计时间 | 实际时间 | 状态 |
|------|---------|---------|------|
| 阶段 1: 项目基础 | 30 分钟 | - | ⏳ 待开始 |
| 阶段 2: 数据库层 | 30 分钟 | - | ⏳ 待开始 |
| 阶段 3: 后端核心 | 2 小时 | - | ⏳ 待开始 |
| 阶段 4: 前端模板 | 1.5 小时 | - | ⏳ 待开始 |
| 阶段 5: Obsidian 插件 | 1.5 小时 | - | ⏳ 待开始 |
| 阶段 6: 集成测试 | 1 小时 | - | ⏳ 待开始 |
| **总计** | **约 7 小时** | **-** | - |

---

## 📌 注意事项

### 开发环境要求
- Java 11+
- Clojure CLI 1.11+
- Node.js 18+
- SQLite 3.x
- Git

### 推荐开发工具
- IDE: IntelliJ IDEA + Cursive / VS Code + Calva
- 浏览器: Chrome/Firefox (支持开发者工具)
- API 测试: curl / Postman / Insomnia

### 开发顺序建议
1. 先完成后端（阶段 1-3）并测试 API
2. 再完成前端（阶段 4）并测试页面
3. 最后完成插件（阶段 5）并集成测试

---

## ✅ 完成标准

### 后端完成标准
- [x] 所有 API 端点返回正确响应
- [x] 数据库操作正常
- [x] Session 认证正常工作
- [x] Token 验证正常工作

### 前端完成标准
- [x] 管理后台可以登录
- [x] 可以创建 Vault
- [x] 可以查看 Vault 列表
- [x] 前端页面正确显示文档列表

### 插件完成标准
- [x] 插件可以在 Obsidian 中启用
- [x] 可以配置服务器地址和 Token
- [x] 文件创建/修改/删除可以同步到服务器
- [x] 同步成功有通知提示

---

## 🔄 更新日志

| 日期 | 更新内容 | 更新人 |
|------|---------|--------|
| 2025-12-21 | 创建 TODO 清单 | - |

---

## 📞 遇到问题？

- 查看 `docs/BACKEND_CODE.md` - 后端代码参考
- 查看 `docs/FRONTEND_CODE.md` - 前端代码参考
- 查看 `docs/OBSIDIAN_PLUGIN.md` - 插件代码参考
- 查看 `docs/API_REFERENCE.md` - API 文档
- 查看 `docs/DEPLOYMENT.md` - 部署指南
- 查看 `docs/BEST_PRACTICES.md` - 开发规范

---

**准备好开始实现了吗？让我们从阶段 1 开始！** 🚀
