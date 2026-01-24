![MarkdownBrain Logo](assets/markdownbrain.png)
# MarkdownBrain

[English](README.md) | [简体中文](README.zh-cn.md)

将 Obsidian 笔记发布为你可以自托管的网站。

## 它是什么

MarkdownBrain 是一套面向个人自托管的 Obsidian 发布栈：

- Console（管理后台）：管理 Vault、域名和 Publish Key
- Frontend（公开站点）：展示已发布的笔记、反向链接和资源文件
- Obsidian 插件：把笔记与资源发布到你的服务器

## MVP 范围

- 发布 Markdown 笔记与附件
- 解析内部链接与反向链接
- 每个 Vault 支持自定义域名
- 支持本地存储或 S3 兼容对象存储
- 每个 Vault 一个 Publish Key（可随时 Renew）
- Console 展示最近一次发布状态、时间和错误原因（快照）

## 快速开始（自托管）

1. 准备一台 Linux 服务器，安装 Docker 与 Docker Compose。
2. 准备一个域名（A/AAAA 记录指向服务器），并放行 `80/443` 端口。
3. 创建环境变量文件。

```bash
cp selfhosted/.env.example selfhosted/.env
```

4. 启动服务（本地存储 + Caddy，推荐）。

```bash
docker compose --env-file selfhosted/.env -f selfhosted/compose/docker-compose.local.yml up -d
```

5. 访问 Console（默认不对公网暴露）。

```bash
ssh -L 9090:localhost:9090 user@your-server
open http://localhost:9090/console
```

6. 首次访问会跳转到 `/console/init`，创建第一个管理员账号。
7. 创建 Vault，复制 Publish Key，并配置 Obsidian 插件。

完整部署说明：`selfhosted/README.md`。

## 快速开始（开发）

依赖：Java 25（Temurin）、Clojure CLI、Node.js 25、pnpm、Make。

```bash
make install
make dev
```

- Frontend：`http://localhost:8080`
- Console：`http://localhost:9090/console`

## 发布产物

### Docker 镜像（GHCR）

- 镜像：`ghcr.io/<owner>/markdownbrain`
- 标签规则：
  - `main` 分支：`edge`、`sha-<7>`
  - git tag `vX.Y.Z`：`X.Y.Z`、`X.Y`、`X`、`latest`

### Obsidian 插件

- 从 GitHub Releases 下载 `markdownbrain-plugin.zip`。
- 解压到 `.obsidian/plugins/markdownbrain/`。

## 文档

- 自托管：`selfhosted/README.md`
- 插件：`obsidian-plugin/README.md`
- 测试：`server/test/README.md`
- UI 规范：`DESIGN_SYSTEM.md`

## 配置

MarkdownBrain 从环境变量读取配置。在开发环境中，也会读取 `server/.env`。

| 变量名 | 说明 | 默认值 | 必填 |
|---|---|---|---|
| `ENVIRONMENT` | `development` 或 `production` | `development` | 否 |
| `HOST` | 监听地址（前台与 Console 共用） | `0.0.0.0` | 否 |
| `FRONTEND_PORT` | Frontend 端口 | `8080` | 否 |
| `CONSOLE_PORT` | Console 端口 | `9090` | 否 |
| `DB_PATH` | SQLite 数据库路径 | `data/markdownbrain.db` | 否 |
| `SESSION_SECRET` | Console session 密钥（hex 字符串） | 自动生成 | 否 |
| `STORAGE_TYPE` | 存储类型：`local` 或 `s3` | `local` | 否 |
| `LOCAL_STORAGE_PATH` | `STORAGE_TYPE=local` 时的本地存储目录 | `./data/storage` | 否 |
| `S3_ENDPOINT` | `STORAGE_TYPE=s3` 时的 S3 Endpoint | - | 是（S3） |
| `S3_ACCESS_KEY` | `STORAGE_TYPE=s3` 时的 S3 Access Key | - | 是（S3） |
| `S3_SECRET_KEY` | `STORAGE_TYPE=s3` 时的 S3 Secret Key | - | 是（S3） |
| `S3_REGION` | S3 Region | `us-east-1` | 否 |
| `S3_BUCKET` | S3 Bucket 名称 | `markdownbrain` | 否 |
| `S3_PUBLIC_URL` | 浏览器加载资源的 base URL | - | 是（S3） |
| `CADDY_ON_DEMAND_TLS_ENABLED` | 是否启用 Caddy 按需 TLS 集成 | `false` | 否 |
| `JAVA_OPTS` | JVM 参数（Docker 运行时） | 空 | 否 |

说明：

- 如果未设置 `SESSION_SECRET`，MarkdownBrain 会自动生成，并保存在与数据库同目录的 `data/.secrets.edn` 中。
- 生产环境建议设置 `ENVIRONMENT=production`，以启用安全 Cookie。

## License

- 服务端（`server/`）：`AGPL-3.0-or-later`（见 `LICENSE`）
- Obsidian 插件（`obsidian-plugin/`）：MIT（见 `obsidian-plugin/LICENSE`）
- 部署配置（`selfhosted/`）：MIT（见 `selfhosted/LICENSE`）

## 第三方声明

本仓库包含第三方字体与前端库，这些资源遵循其各自的授权协议，不包含在本项目的许可证范围内。
详见 `THIRD_PARTY_NOTICES.md`。
