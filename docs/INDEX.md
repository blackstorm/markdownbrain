# MarkdownBrain 文档索引

本目录包含 MarkdownBrain 项目的所有详细文档。

## 📚 文档列表

### 1. [BACKEND_CODE.md](BACKEND_CODE.md)
**后端代码完整示例**

包含：
- `deps.edn` - Clojure 依赖配置
- 完整的后端代码示例（所有 `.clj` 文件）
- 数据库层 (db.clj)
- 路由定义 (routes.clj)
- 处理器 (handlers/)
- 中间件 (middleware.clj)
- 工具函数 (utils.clj)
- API 测试示例

**适合人群**: 后端开发者

---

### 2. [FRONTEND_CODE.md](FRONTEND_CODE.md)
**前端代码完整示例**

包含：
- `shadow-cljs.edn` - ClojureScript 构建配置
- `package.json` - NPM 依赖
- `tailwind.config.js` - TailwindCSS 配置
- ClojureScript 辅助函数
- HTML 模板（Selmer）
- HTMX 集成示例
- 管理后台页面
- 前端展示页面

**适合人群**: 前端开发者

---

### 3. [OBSIDIAN_PLUGIN.md](OBSIDIAN_PLUGIN.md)
**Obsidian 插件代码完整示例**

包含：
- TypeScript 主文件 (main.ts)
- ClojureScript 同步逻辑 (sync.cljs)
- 插件配置 (manifest.json, package.json, tsconfig.json)
- shadow-cljs 配置
- 文件监听和同步实现
- 设置面板
- 构建和安装指南

**适合人群**: 插件开发者

---

### 4. [API_REFERENCE.md](API_REFERENCE.md)
**完整 API 参考手册**

包含：
- 管理员 API（初始化、登录、Vault 管理）
- 同步 API（文件上传、删除）
- 前端 API（文档列表、文档详情）
- 请求/响应示例
- 错误码说明
- 数据模型定义
- 使用示例（curl, JavaScript）

**适合人群**: API 集成开发者

---

### 5. [DEPLOYMENT.md](DEPLOYMENT.md)
**部署和运维指南**

包含：
- 开发环境部署步骤
- 生产环境部署步骤
- Nginx 反向代理配置（含泛域名）
- SSL 证书配置 (Let's Encrypt)
- Systemd 服务配置
- 数据库备份脚本
- 日志和监控配置
- 安全加固建议
- 性能优化技巧
- 故障排查指南

**适合人群**: DevOps、运维工程师

---

## 📖 阅读顺序建议

### 新手入门
1. **根目录 README.md** - 快速开始指南
2. **根目录 DESIGN.md** - 架构设计概览
3. **BACKEND_CODE.md** - 后端代码示例
4. **FRONTEND_CODE.md** - 前端代码示例
5. **OBSIDIAN_PLUGIN.md** - 插件代码示例

### API 集成
1. **API_REFERENCE.md** - 完整 API 文档
2. **BACKEND_CODE.md** - 理解后端实现

### 部署上线
1. **DEPLOYMENT.md** - 部署指南
2. **API_REFERENCE.md** - 测试 API
3. **根目录 DESIGN.md** - 理解系统架构

---

## 🗂️ 文件用途速查

| 文件 | 用途 | 包含代码 |
|------|------|----------|
| BACKEND_CODE.md | 后端实现 | ✅ 完整代码 |
| FRONTEND_CODE.md | 前端实现 | ✅ 完整代码 |
| OBSIDIAN_PLUGIN.md | 插件实现 | ✅ 完整代码 |
| API_REFERENCE.md | API 文档 | ⚠️ 调用示例 |
| DEPLOYMENT.md | 部署运维 | ⚠️ 配置示例 |

---

## 🔍 快速查找

### 我想...
- **启动项目** → README.md (5 分钟快速开始)
- **理解架构** → DESIGN.md
- **查看后端代码** → BACKEND_CODE.md
- **查看前端代码** → FRONTEND_CODE.md
- **开发插件** → OBSIDIAN_PLUGIN.md
- **调用 API** → API_REFERENCE.md
- **部署上线** → DEPLOYMENT.md

---

## 📝 更新日志

| 日期 | 文档 | 变更 |
|------|------|------|
| 2025-12-21 | 全部文档 | 初始版本创建 |

---

## 🤝 贡献文档

如果您发现文档错误或有改进建议：

1. 提交 Issue 说明问题
2. 或直接提交 PR 修正文档
3. 文档遵循 Markdown 格式
4. 代码示例需要测试通过

---

## 📞 获取帮助

- **GitHub Issues**: https://github.com/yourname/markdownbrain/issues
- **Email**: your@email.com
- **文档更新频率**: 每次功能更新时同步更新文档

---

**感谢阅读！**
