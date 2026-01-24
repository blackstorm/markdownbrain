# 自托管 MarkdownBrain

[English](README.md) | [简体中文](README.zh-cn.md)

本目录提供生产可用的 Docker Compose 与 Caddy 配置。

## 概览

MarkdownBrain 在容器内提供两个端口：

- Frontend：`8080`（公开站点）
- Console：`9090`（管理后台与发布 API，`/obsidian/*`）

推荐的安全模型：

- Console 保持私有（宿主机只绑定 `127.0.0.1`）。
- 只对外暴露公开站点（通过本仓库提供的 Caddy 转发到 `8080`/`9090`）。

## 快速部署（一行命令）

用于快速试用（不含反向代理、本地存储），执行：

```bash
docker run -d --name markdownbrain --restart unless-stopped -p 8080:8080 -p 127.0.0.1:9090:9090 -v markdownbrain:/app/data -e STORAGE_TYPE=local ghcr.io/leehaoya/markdownbrain:latest
```

- 公开站点：`http://<你的服务器>:8080/`
- Console（SSH 隧道）：`ssh -L 9090:localhost:9090 user@your-server`，然后打开 `http://localhost:9090/console`

## 选择部署方式

- `compose/docker-compose.local.yml`（推荐）
  - MarkdownBrain + Caddy
  - 本地存储（不使用 S3）
- `compose/docker-compose.s3.yml`
  - MarkdownBrain + Caddy + RustFS（S3 兼容）
  - 也可以替换为你自己的 S3
- `compose/docker-compose.minimal.yml`
  - 仅 MarkdownBrain（不含反向代理）
  - 适合本地测试或内网环境

## 前置条件

- 一台 Linux 服务器，已安装 Docker 与 Docker Compose
- 一个域名，A/AAAA 记录指向服务器
- 放行 `80/443` 端口（Caddy 使用）

## 环境变量

Compose 会从 `selfhosted/.env` 读取环境变量（参考 `selfhosted/.env.example`）。这些变量要么用于 Compose 的变量替换（镜像标签、端口等），要么会注入到容器的环境变量中。

| 变量名 | 说明 | 默认值或示例 | 必填 |
|---|---|---|---|
| `MARKDOWNBRAIN_IMAGE` | 要运行的 Docker 镜像标签 | `ghcr.io/<owner>/markdownbrain:latest` | 是 |
| `JAVA_OPTS` | MarkdownBrain 容器的 JVM 参数 | `-Xms256m -Xmx512m` | 否 |
| `CADDY_ON_DEMAND_TLS_ENABLED` | 是否启用 Caddy 按需 TLS | `false` | 否 |
| `S3_PUBLIC_URL` | S3 模式下浏览器加载资源的 base URL | `https://s3.your-domain.com` | 是（S3） |
| `S3_ACCESS_KEY` | S3 Access Key（RustFS 或你的 S3） | `rustfsadmin` | 是（S3） |
| `S3_SECRET_KEY` | S3 Secret Key（RustFS 或你的 S3） | `rustfsadmin` | 是（S3） |
| `S3_BUCKET` | S3 Bucket 名称 | `markdownbrain` | 是（S3） |
| `S3_PUBLIC_PORT` | S3 Compose 中 RustFS 暴露到宿主机的端口 | `9000` | 否 |

## 快速开始（本地存储 + Caddy）

1. 创建 `selfhosted/.env`。

```bash
cp selfhosted/.env.example selfhosted/.env
```

2. 修改 `selfhosted/.env`。

- 生产环境建议固定版本：`MARKDOWNBRAIN_IMAGE=...:X.Y.Z`
- 需要自动证书时设置：`CADDY_ON_DEMAND_TLS_ENABLED=true`

3. 启动。

```bash
docker compose --env-file selfhosted/.env -f selfhosted/compose/docker-compose.local.yml up -d
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

## S3 模式说明

在 `docker-compose.s3.yml` 中，RustFS 会暴露到宿主机端口 `${S3_PUBLIC_PORT:-9000}`。

- `S3_PUBLIC_URL` 必须能被浏览器直接访问（建议使用 TLS 或 CDN）。
- 如果你不希望暴露 RustFS，请使用你自己的 S3 + CDN，并把 `S3_PUBLIC_URL` 设置为 CDN 的 base URL。

启动：

```bash
docker compose --env-file selfhosted/.env -f selfhosted/compose/docker-compose.s3.yml up -d
```

## 按需 TLS 的工作方式

当 `CADDY_ON_DEMAND_TLS_ENABLED=true` 时，Caddy 只会为已在 MarkdownBrain 中登记的域名签发证书：

- Caddy 调用 `http://markdownbrain:9090/console/domain-check?domain=...`
- 只有当域名存在于 Vault 列表中时，MarkdownBrain 才返回 `200`

## 升级

```bash
docker compose --env-file selfhosted/.env -f selfhosted/compose/docker-compose.local.yml pull
docker compose --env-file selfhosted/.env -f selfhosted/compose/docker-compose.local.yml up -d
```

数据库迁移会在服务启动时自动执行。

## 备份

持久化数据存放在挂载到 `/app/data` 的 Docker volume 中。

至少备份：

- SQLite：`markdownbrain.db`
- Secrets：`.secrets.edn`

## 常见问题

- TLS 失败：检查 DNS 是否指向服务器，且 `80/443` 可从公网访问。
- S3 模式资源加载失败：确认 `S3_PUBLIC_URL` 可被浏览器直接访问。
- 插件无法连接：确认公网可访问 `/obsidian/*`（由 Caddy 转发），以及 Publish Key 是否正确。
