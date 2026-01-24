# MarkdownBrain

[English](README.md) | [简体中文](README.zh-cn.md)

将 Obsidian 笔记发布为你可以自托管的网站。

MarkdownBrain 包含：
- **Console**（管理后台）：管理站点/域名/Publish Key
- **Frontend**（公开站点）：浏览已发布的笔记
- **Obsidian 插件**：将笔记与资源（图片/PDF/音视频等）发布到你的服务器

## MVP 功能

- 发布 Markdown 笔记与附件
- 每个 Vault 一个自定义域名
- Caddy 按需签发 TLS（可选）
- 内部链接解析 + 反向链接
- 每个 Vault 一个 Publish Key（可随时 Renew）
- Console 展示最近一次发布状态/时间/错误原因（快照）

## 快速开始（开发）

依赖：Java（建议 Temurin 21+）、Clojure CLI、Node.js（CSS 构建）、Make。

```bash
make install
make dev
# Frontend: http://localhost:8080
# Console:  http://localhost:9090/console
```

常用命令：

```bash
make backend-test
make frontend-dev
make plugin-dev
make build
```

## 快速开始（自托管）

详细指南请看 `selfhosted/README.md`（包含部署模式选择、TLS、S3 等）。

本地存储 + Caddy（单机推荐）：

```bash
docker compose -f selfhosted/compose/docker-compose.local.yml up -d
```

S3 兼容对象存储 + Caddy：

```bash
export S3_PUBLIC_URL=https://s3.your-domain.com
docker compose -f selfhosted/compose/docker-compose.s3.yml up -d
```

提供的 Compose 文件会把 Console 端口绑定到 `127.0.0.1:9090`。通过 SSH 隧道访问：

```bash
ssh -L 9090:localhost:9090 user@your-server
open http://localhost:9090/console
```

## 配置

MarkdownBrain 从环境变量与 `server/.env`（开发环境）读取配置。

| 环境变量 | 说明 | 必填 |
|---|---|---|
| `ENVIRONMENT` | `development` 或 `production` | 否 |
| `HOST` | 监听地址（默认 `0.0.0.0`） | 否 |
| `FRONTEND_PORT` | 前台端口（默认 `8080`） | 否 |
| `CONSOLE_PORT` | Console 端口（默认 `9090`） | 否 |
| `DB_PATH` | SQLite 数据库路径（默认 `data/markdownbrain.db`） | 否 |
| `SESSION_SECRET` | Console session 密钥（缺省自动生成） | 否 |
| `STORAGE_TYPE` | `local` 或 `s3` | 否 |
| `LOCAL_STORAGE_PATH` | 本地资源存储目录（默认 `./data/storage`） | 否 |
| `S3_ENDPOINT` | S3 Endpoint（`STORAGE_TYPE=s3` 时需要） | 是（S3） |
| `S3_ACCESS_KEY` | S3 Access Key | 是（S3） |
| `S3_SECRET_KEY` | S3 Secret Key | 是（S3） |
| `S3_REGION` | S3 Region（默认 `us-east-1`） | 否 |
| `S3_BUCKET` | S3 Bucket（默认 `markdownbrain`） | 否 |
| `S3_PUBLIC_URL` | 浏览器可访问的资源 URL 前缀 | 是（S3） |
| `CADDY_ON_DEMAND_TLS_ENABLED` | `true` 启用 Caddy 按需 TLS | 否 |

说明：
- `S3_PUBLIC_URL` 必须对浏览器可访问；资源会从该 URL 直连加载，不经过应用服务器代理。
- `SESSION_SECRET` 如未设置，会自动生成并保存到 `data/.secrets.edn`（与 DB 同目录）。

## Obsidian 插件

- 插件文档：`obsidian-plugin/README.md`
- Release 安装：解压 `markdownbrain-plugin.zip` 到 `.obsidian/plugins/markdownbrain/`
- 配置：
  - Server URL：你的 MarkdownBrain 服务地址
  - Publish Key：在 Console 中复制

## 目录结构

- `server/`：Clojure 后端 + templates + 静态资源
- `obsidian-plugin/`：Obsidian 插件（TypeScript）
- `selfhosted/`：Docker Compose + Caddy 配置

## 第三方声明

本仓库包含第三方字体与前端 JS 库，这些资源遵循其各自的授权协议，**不**包含在本项目的许可证范围内。
详见 `THIRD_PARTY_NOTICES.md` 与 `server/resources/publics/shared/`。

## License

- 服务端（`server/`）：`AGPL-3.0-or-later`（见 `LICENSE`）。
- Obsidian 插件（`obsidian-plugin/`）：MIT（见 `obsidian-plugin/LICENSE`）。
- 部署配置（`selfhosted/`）：MIT（见 `selfhosted/LICENSE`）。
