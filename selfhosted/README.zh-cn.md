# 自托管 MarkdownBrain

[English](README.md) | [简体中文](README.zh-cn.md)

本目录提供生产可用的 Docker Compose 部署方案。

## 目录

- [概览](#toc-overview)
- [快速部署（一行命令）](#toc-quick-deploy)
- [选择部署方式](#toc-choose-deployment)
- [前置条件](#toc-prerequisites)
- [环境变量](#toc-environment-variables)
  - [Compose 默认会设置的变量](#toc-compose-defaults)
- [快速开始（推荐：Caddy）](#toc-quickstart-caddy)
- [Minimal 模式（不含 Caddy）](#toc-minimal)
- [S3 模式（Caddy + RustFS）](#toc-s3)
- [Caddy 目录（文件作用与使用场景）](#toc-caddy-files)
- [按需 TLS 的工作方式](#toc-on-demand-tls)
  - [Cloudflare 说明（按需 TLS 场景）](#toc-cloudflare)
- [升级](#toc-upgrade)
- [备份](#toc-backup)
- [常见问题](#toc-troubleshooting)

<a id="toc-overview"></a>
## 概览

MarkdownBrain 在容器内提供两个端口：

- Frontend：`8080`（公开站点）
- Console：`9090`（管理后台与发布 API，`/obsidian/*`）

推荐的安全模型：

- Console 保持私有（宿主机只绑定 `127.0.0.1`）。
- 只对外暴露公开站点（通过本仓库提供的 Caddy 转发到 `8080`/`9090`）。

<a id="toc-quick-deploy"></a>
## 快速部署（一行命令）

用于快速试用（不含反向代理、本地存储），执行：

```bash
docker run -d --name markdownbrain --restart unless-stopped -p 8080:8080 -p 127.0.0.1:9090:9090 -v markdownbrain:/app/data -e STORAGE_TYPE=local ghcr.io/blackstorm/markdownbrain:latest
```

- 公开站点：`http://<你的服务器>:8080/`
- Console（SSH 隧道）：`ssh -L 9090:localhost:9090 user@your-server`，然后打开 `http://localhost:9090/console`

<a id="toc-choose-deployment"></a>
## 选择部署方式

- `minimal`（仅 MarkdownBrain）
  - Compose 文件：`selfhosted/compose/docker-compose.minimal.yml`
- `caddy`（MarkdownBrain + Caddy）（推荐）
  - Compose 文件：`selfhosted/compose/docker-compose.caddy.yml`
- `s3`（MarkdownBrain + Caddy + RustFS）
  - Compose 文件：`selfhosted/compose/docker-compose.s3.yml`

<a id="toc-prerequisites"></a>
## 前置条件

- 一台 Linux 服务器，已安装 Docker 与 Docker Compose
- 一个域名，A/AAAA 记录指向服务器
- 放行 `80/443` 端口（Caddy 使用）

<a id="toc-environment-variables"></a>
## 环境变量

Compose 会从 `selfhosted/.env` 读取环境变量（参考 `selfhosted/.env.example`）。

这里存在两层变量：

- Compose 变量：由 Docker Compose 使用（镜像标签、端口等）。
- MarkdownBrain 变量：注入到 `markdownbrain` 容器中（服务端读取并生效）。

服务端完整配置说明见 [../README.md](../README.md#configuration)。

| 变量名 | 作用对象 | 说明 | 默认值或示例 | 必填 |
|---|---|---|---|---|
| `MARKDOWNBRAIN_IMAGE` | Compose | 要运行的 Docker 镜像标签 | `ghcr.io/blackstorm/markdownbrain:latest` | 是 |
| `DATA_PATH` | MarkdownBrain | 容器内的数据目录 | `/app/data` | 否 |
| `JAVA_OPTS` | MarkdownBrain | MarkdownBrain 容器的 JVM 参数 | `-Xms256m -Xmx512m` | 否 |
| `MARKDOWNBRAIN_LOG_LEVEL` | MarkdownBrain | 应用日志级别（Logback） | `INFO` | 否 |
| `CADDY_ON_DEMAND_TLS_ENABLED` | Caddy + MarkdownBrain | 是否启用 Caddy 按需 TLS 集成 | `false` | 否 |
| `S3_PUBLIC_URL` | MarkdownBrain | S3 模式下浏览器加载资源的 base URL | `https://s3.your-domain.com` | 是（S3） |
| `S3_ACCESS_KEY` | MarkdownBrain + RustFS | S3 Access Key（RustFS 或你的 S3） | `rustfsadmin` | 是（S3） |
| `S3_SECRET_KEY` | MarkdownBrain + RustFS | S3 Secret Key（RustFS 或你的 S3） | `rustfsadmin` | 是（S3） |
| `S3_BUCKET` | MarkdownBrain | S3 Bucket 名称 | `markdownbrain` | 是（S3） |
| `S3_PUBLIC_PORT` | Compose | S3 Compose 中 RustFS 暴露到宿主机的端口 | `9000` | 否 |

<a id="toc-compose-defaults"></a>
### Compose 默认会设置的变量

本仓库提供的 compose 文件已经设置了关键的 MarkdownBrain 变量：

- `compose/docker-compose.caddy.yml` 与 `compose/docker-compose.minimal.yml`
  - `STORAGE_TYPE=local`
- `compose/docker-compose.s3.yml`
  - `STORAGE_TYPE=s3`
  - `S3_ENDPOINT=http://rustfs:9000`（也可以改成你自己的 S3 Endpoint）

对应的 compose 文件为：

- `selfhosted/compose/docker-compose.minimal.yml`
- `selfhosted/compose/docker-compose.caddy.yml`
- `selfhosted/compose/docker-compose.s3.yml`

通常不需要额外设置 `DATA_PATH` 或 `LOCAL_STORAGE_PATH`。因为容器会持久化 `/app/data`，而默认值本来就在这个目录中：

- 默认数据库：`/app/data/markdownbrain.db`
- 默认本地存储：`/app/data/storage`

<a id="toc-quickstart-caddy"></a>
## 快速开始（推荐：Caddy）

1. 创建 `selfhosted/.env`。

```bash
cp selfhosted/.env.example selfhosted/.env
```

2. 修改 `selfhosted/.env`。

- 生产环境建议固定版本：`MARKDOWNBRAIN_IMAGE=...:X.Y.Z`
- 需要自动证书时设置：`CADDY_ON_DEMAND_TLS_ENABLED=true`

3. 启动。

```bash
docker compose --env-file selfhosted/.env -f selfhosted/compose/docker-compose.caddy.yml up -d
```

4. 通过 SSH 隧道访问 Console。

```bash
ssh -L 9090:localhost:9090 user@your-server
open http://localhost:9090/console
```

5. 初始化并发布。

- 在 `/console/init` 创建第一个管理员账号。
- 创建 Vault，并设置域名。
- 复制 Vault 的 Publish Key，配置 Obsidian 插件。
- 打开 `https://<你的域名>/`。

<a id="toc-minimal"></a>
## Minimal 模式（不含 Caddy）

当你已经有自己的反向代理 / TLS 时可以使用：

```bash
docker compose --env-file selfhosted/.env -f selfhosted/compose/docker-compose.minimal.yml up -d
```

- 公开站点：`http://<你的服务器>:8080/`

<a id="toc-s3"></a>
## S3 模式（Caddy + RustFS）

该模式包含内置的 S3 兼容对象存储（RustFS）。RustFS 会暴露到宿主机端口 `${S3_PUBLIC_PORT:-9000}`。

- `S3_PUBLIC_URL` 必须能被浏览器直接访问（建议使用 TLS 或 CDN）。
- 如果你不希望暴露 RustFS，请使用你自己的 S3 + CDN，并把 `S3_PUBLIC_URL` 设置为 CDN 的 base URL。

启动：

```bash
docker compose --env-file selfhosted/.env -f selfhosted/compose/docker-compose.s3.yml up -d
```

<a id="toc-caddy-files"></a>
## Caddy 目录（文件作用与使用场景）

Caddy 相关文件在 `selfhosted/caddy/`：

- `selfhosted/compose/docker-compose.caddy.yml`
  - 启动 MarkdownBrain + Caddy，并配置端口与路由。
- `selfhosted/caddy/Caddyfile.simple`
  - 适合你通过 Cloudflare / nginx / 负载均衡器等方式在外部管理 TLS 的场景。
  - 监听 `:80`，并反向代理：
    - `/obsidian/*` → `markdownbrain:9090`
    - 其它路径 → `markdownbrain:8080`
- `selfhosted/caddy/Caddyfile.on-demand-tls`
  - 适合你希望 Caddy 自动为域名签发证书的场景。
  - 需要设置 `CADDY_ON_DEMAND_TLS_ENABLED=true`，并放行 `80/443`。
  - 使用 `ask http://markdownbrain:9090/console/domain-check`：只有在 Console 中登记过的域名才会签发证书。
- `selfhosted/caddy/caddy-entrypoint.sh`
  - 根据 `CADDY_ON_DEMAND_TLS_ENABLED` 自动选择 `Caddyfile.simple` 或 `Caddyfile.on-demand-tls`。

<a id="toc-on-demand-tls"></a>
## 按需 TLS 的工作方式

当 `CADDY_ON_DEMAND_TLS_ENABLED=true` 时，Caddy 只会为已在 MarkdownBrain 中登记的域名签发证书：

- Caddy 调用 `http://markdownbrain:9090/console/domain-check?domain=...`
- 只有当域名存在于 Vault 列表中时，MarkdownBrain 才返回 `200`

<a id="toc-cloudflare"></a>
### Cloudflare 说明（按需 TLS 场景）

按需 TLS 需要公网（证书签发机构）能直接访问你的服务器 `80/443` 端口。如果你开启了 Cloudflare 代理，Cloudflare 会在边缘终止 TLS，证书校验流程可能无法直达你的源站。

推荐做法：

1. 在 Cloudflare DNS 中为 Vault 域名创建 `A`/`AAAA` 记录，指向你的服务器 IP。
2. 将 **Proxy status 设为 DNS only**。
3. 确保服务器防火墙/安全组已放行 `80/443` 入站。
4. 设置 `CADDY_ON_DEMAND_TLS_ENABLED=true` 并重启。

如果你必须开启 Cloudflare 代理，请使用 `selfhosted/caddy/Caddyfile.simple`，让 Cloudflare 负责边缘 HTTPS（Caddy 仅监听 `:80`）；或者改用 DNS-01 签发方案（本仓库未提供）。

<a id="toc-upgrade"></a>
## 升级

```bash
docker compose --env-file selfhosted/.env -f selfhosted/compose/docker-compose.caddy.yml pull
docker compose --env-file selfhosted/.env -f selfhosted/compose/docker-compose.caddy.yml up -d
```

数据库迁移会在服务启动时自动执行。

<a id="toc-backup"></a>
## 备份

持久化数据存放在挂载到 `/app/data` 的 Docker volume 中。

至少备份：

- SQLite：`markdownbrain.db`
- Secrets：`.secrets.edn`

<a id="toc-troubleshooting"></a>
## 常见问题

- TLS 失败：检查 DNS 是否指向服务器，且 `80/443` 可从公网访问。
- S3 模式资源加载失败：确认 `S3_PUBLIC_URL` 可被浏览器直接访问。
- 插件无法连接：确认公网可访问 `/obsidian/*`（由 Caddy 转发），以及 Publish Key 是否正确。
