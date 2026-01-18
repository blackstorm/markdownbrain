# MarkdownBrain

将 Obsidian 笔记同步发布为网站的多租户平台。支持自定义域名和自动 SSL 证书。

## 功能特性

- Obsidian 插件实时同步笔记到服务器
- 多租户架构，支持多个独立站点
- 自定义域名，自动获取 SSL 证书
- 内部链接自动解析
- 反向链接展示

## 快速开始

### 开发环境

```bash
# 安装依赖
make install

# 启动开发服务器
make dev

# Frontend: http://localhost:8080
# Admin: http://localhost:9090
```

### 其他开发命令

```bash
make frontend-dev    # CSS watch 模式
make plugin-dev      # Obsidian 插件开发模式
make test            # 运行测试
make build           # 构建所有项目
```

## 部署指南

### 前置条件

- Docker 和 Docker Compose
- 域名已解析到服务器 IP
- 服务器 80/443 端口可用

### 环境变量

| 变量 | 说明 | 必填 |
|------|------|------|
| `S3_PUBLIC_URL` | S3 存储公开访问地址 (用于直接访问图片等资源) | 是 |
| `CADDY_ON_DEMAND_TLS_ENABLED` | 启用 Caddy 自动 SSL 证书 (默认 `false`) | 否 |
| `SESSION_SECRET` | Session 加密密钥 | 否 (自动生成) |
| `DB_PATH` | SQLite 数据库路径 (默认 `data/markdownbrain.db`) | 否 |
| `S3_BUCKET` | S3 存储桶名称 | 否 (默认 `markdownbrain`) |
| `S3_ACCESS_KEY` | RustFS 访问密钥 | 否 (默认 `rustfsadmin`) |
| `S3_SECRET_KEY` | RustFS 密钥 | 否 (默认 `rustfsadmin`) |

> **注意**: 
> - `S3_PUBLIC_URL` 是浏览器可直接访问的 S3 地址。图片、PDF 等资源会直接从此 URL 加载，不经过应用服务器。
> - `CADDY_ON_DEMAND_TLS_ENABLED=true` 时，Caddy 会自动为配置的域名获取 Let's Encrypt 证书。如果你使用 Cloudflare 或其他方式管理 SSL，保持默认值 `false` 即可。
> - `SESSION_SECRET` 自动生成并保存到 `data/.secrets.edn`（与 `DB_PATH` 同目录）。

### 部署步骤

1. 克隆项目

```bash
git clone https://github.com/example/markdownbrain.git
cd markdownbrain
```

2. 设置环境变量

```bash
export S3_PUBLIC_URL=https://s3.your-domain.com  # 替换为你的 S3 公开访问地址

# 可选：启用自动 SSL 证书 (如果不使用 Cloudflare 等外部 SSL)
export CADDY_ON_DEMAND_TLS_ENABLED=true
```

3. 启动服务

```bash
docker compose up -d
```

4. 查看日志

```bash
docker compose logs -f
```

### 访问 Admin 面板

Admin 端口 (9090) 仅绑定到 localhost，务必不要对公网开放。通过 SSH 隧道访问：

```bash
ssh -L 9090:localhost:9090 user@your-server
```

然后在本地浏览器访问 `http://localhost:9090/admin`

### 添加站点域名

1. 登录 Admin 面板
2. 创建新站点，填写域名（如 `notes.example.com`）
3. 将域名 DNS 解析到服务器 IP
4. 如果启用了 `CADDY_ON_DEMAND_TLS_ENABLED`，Caddy 会自动为该域名获取 SSL 证书

## Obsidian 插件

### 安装

1. 从 Release 下载 `markdownbrain-plugin.zip`
2. 解压到 `.obsidian/plugins/markdownbrain/`
3. 在 Obsidian 设置中启用插件

### 配置

1. 在 Admin 面板创建站点，获取 Sync Key
2. 在插件设置中填写：
   - Server URL: `https://your-admin-domain.com`
   - Sync Key: 从 Admin 面板复制

### 同步

- 自动同步：文件修改后自动同步
- 手动同步：命令面板执行 `MarkdownBrain: Sync All`

## 架构

```
                    ┌─────────────┐
                    │   Caddy     │
                    │  (80/443)   │
                    └──────┬──────┘
                           │
              ┌────────────┴────────────┐
              │                         │
              ▼                         ▼
    ┌─────────────────┐      ┌─────────────────┐
    │  Frontend:8080  │      │  Admin:9090     │
    │  (公开访问)      │      │  (仅 localhost) │
    └─────────────────┘      └─────────────────┘
              │                         │
              └────────────┬────────────┘
                           │
              ┌────────────┴────────────┐
              │                         │
              ▼                         ▼
       ┌─────────────┐         ┌─────────────┐
       │   SQLite    │         │   RustFS    │◄──── 浏览器直接访问
       │   (数据库)   │         │  (对象存储)  │      (S3_PUBLIC_URL)
       └─────────────┘         └─────────────┘

资源访问流程:
- 图片/PDF/音视频等资源直接从 S3 存储加载
- 浏览器通过 S3_PUBLIC_URL 直接请求，不经过应用服务器
- 减少服务器带宽消耗，提高加载速度
```
