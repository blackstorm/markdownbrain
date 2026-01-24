![MarkdownBrain Logo](assets/markdownbrain.png)
# MarkdownBrain

[English](README.md) | [简体中文](README.zh-cn.md)

将 Obsidian 笔记发布为你可以自托管的网站。

## 目录

- [它是什么](#toc-它是什么)
- [MVP 范围](#toc-mvp)
- [快速开始（自托管）](#toc-quickstart-self-host)
- [快速开始（开发）](#toc-quickstart-development)
- [发布产物](#toc-releases)
  - [Docker 镜像（GHCR）](#toc-docker-image-ghcr)
  - [Obsidian 插件](#toc-obsidian-plugin)
- [文档](#toc-documentation)
- [配置](#toc-configuration)
  - [环境变量（概览）](#toc-config-server-env)
- [License](#toc-license)
- [第三方声明](#toc-third-party-notices)

<a id="toc-它是什么"></a>
## 它是什么

MarkdownBrain 是一套面向个人自托管的 Obsidian 发布栈：

- Console（管理后台）：管理 Vault、域名和 Publish Key
- Frontend（公开站点）：展示已发布的笔记、反向链接和资源文件
- Obsidian 插件：把笔记与资源发布到你的服务器

<a id="toc-mvp"></a>
## MVP 范围

- 发布 Markdown 笔记与附件
- 解析内部链接与反向链接
- 每个 Vault 支持自定义域名
- 支持本地存储或 S3 兼容对象存储
- 每个 Vault 一个 Publish Key（可随时 Renew）
- Console 展示最近一次发布状态、时间和错误原因（快照）

<a id="toc-quickstart-self-host"></a>
## 快速开始（自托管）

1. 准备一台 Linux 服务器，安装 Docker 与 Docker Compose。
2. 准备一个域名（A/AAAA 记录指向服务器），并放行 `80/443` 端口。
3. 创建环境变量文件。

```bash
cp selfhosted/.env.example selfhosted/.env
```

4. 启动服务（本地存储 + Caddy，推荐）。

```bash
docker compose --env-file selfhosted/.env -f selfhosted/compose/docker-compose.caddy.yml up -d
```

5. 访问 Console（默认不对公网暴露）。

```bash
ssh -L 9090:localhost:9090 user@your-server
open http://localhost:9090/console
```

6. 首次访问会跳转到 `/console/init`，创建第一个管理员账号。
7. 创建 Vault，复制 Publish Key，并配置 Obsidian 插件。

完整部署说明：[selfhosted/README.md](selfhosted/README.md)。

<a id="toc-quickstart-development"></a>
## 快速开始（开发）

依赖：Java 25（Temurin）、Clojure CLI、Node.js 25、pnpm、Make。

```bash
make install
make dev
```

- Frontend：`http://localhost:8080`
- Console：`http://localhost:9090/console`

<a id="toc-releases"></a>
## 发布产物

<a id="toc-docker-image-ghcr"></a>
### Docker 镜像（GHCR）

- 镜像：`ghcr.io/blackstorm/markdownbrain`
- 标签规则：
  - `main` 分支：`edge`、`sha-<7>`
  - git tag `vX.Y.Z`：`X.Y.Z`、`X.Y`、`X`、`latest`

<a id="toc-obsidian-plugin"></a>
### Obsidian 插件

- 从 GitHub Releases 下载 `markdownbrain-plugin.zip`。
- 解压到 `.obsidian/plugins/markdownbrain/`。

<a id="toc-documentation"></a>
## 文档

- 自托管：[selfhosted/README.md](selfhosted/README.md)
- 插件：[obsidian-plugin/README.md](obsidian-plugin/README.md)
- 测试：[server/test/README.md](server/test/README.md)
- UI 规范：[design/DESIGN_SYSTEM.md](design/DESIGN_SYSTEM.md)

<a id="toc-configuration"></a>
## 配置

MarkdownBrain 从环境变量读取配置。

- 开发环境：通常从 `server/` 启动，也会读取 `.env`（例如 `server/.env`）。
- Docker：只有当容器工作目录下存在 `.env` 时服务端才会读取；大多数部署建议通过 Docker Compose 的 `environment:` 或 `--env-file` 传入环境变量。

<a id="toc-config-server-env"></a>
### 环境变量（概览）

| 变量名 | 作用对象 | 说明 | 默认值或示例 | 必填 |
|---|---|---|---|---|
| `MARKDOWNBRAIN_IMAGE` | Compose | 要运行的 Docker 镜像标签 | `ghcr.io/blackstorm/markdownbrain:latest` | 是 |
| `DATA_PATH` | MarkdownBrain | 容器内的数据目录 | `/app/data` | 否 |
| `JAVA_OPTS` | MarkdownBrain | 容器的 JVM 参数 | `-Xms256m -Xmx512m` | 否 |
| `MARKDOWNBRAIN_LOG_LEVEL` | MarkdownBrain | 应用日志级别（Logback） | `INFO` | 否 |
| `CADDY_ON_DEMAND_TLS_ENABLED` | Caddy + MarkdownBrain | 是否启用 Caddy 按需 TLS 集成 | `false` | 否 |
| `S3_PUBLIC_URL` | MarkdownBrain | S3 模式下浏览器加载资源的 base URL | `https://s3.your-domain.com` | 是（S3） |
| `S3_ACCESS_KEY` | MarkdownBrain + RustFS | S3 Access Key（RustFS 或你的 S3） | `rustfsadmin` | 是（S3） |
| `S3_SECRET_KEY` | MarkdownBrain + RustFS | S3 Secret Key（RustFS 或你的 S3） | `rustfsadmin` | 是（S3） |
| `S3_BUCKET` | MarkdownBrain | S3 Bucket 名称 | `markdownbrain` | 是（S3） |
| `S3_PUBLIC_PORT` | Compose | S3 Compose 中 RustFS 暴露到宿主机的端口 | `9000` | 否 |

完整说明（服务端全部环境变量、默认值、使用场景）：[selfhosted/README.zh-cn.md](selfhosted/README.zh-cn.md#toc-environment-variables)。

<a id="toc-license"></a>
## License

- 服务端（`server/`）：`AGPL-3.0-or-later`（见 `LICENSE`）
- Obsidian 插件（`obsidian-plugin/`）：MIT（见 `obsidian-plugin/LICENSE`）
- 部署配置（`selfhosted/`）：MIT（见 `selfhosted/LICENSE`）

<a id="toc-third-party-notices"></a>
## 第三方声明

本仓库包含第三方字体与前端库，这些资源遵循其各自的授权协议，不包含在本项目的许可证范围内。
详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
