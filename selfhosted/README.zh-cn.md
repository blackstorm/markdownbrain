# 自托管 MarkdownBrain

[English](README.md) | [简体中文](README.zh-cn.md)

本目录提供生产可用的 Docker Compose 与 Caddy 配置。

## 你会得到什么

- 公开站点：由 **frontend** 服务提供（容器内端口 `8080`）
- 发布 API：Obsidian 插件请求 `/obsidian/*`，由反向代理转发到 **console** 服务（容器内端口 `9090`）
- 管理后台：Console 默认 **不对公网暴露**（下面的 Compose 文件会把 9090 绑定到 localhost）

## 目录结构

- `selfhosted/compose/`：Docker Compose 文件
- `selfhosted/caddy/`：Caddyfile 与启动脚本

## 选择部署方式

- `compose/docker-compose.minimal.yml`
  - 仅 MarkdownBrain（本地存储）
  - 适合本地测试 / 内网使用（不含反向代理）
- `compose/docker-compose.local.yml`
  - MarkdownBrain + Caddy（本地存储）
  - 单机 VPS 推荐
- `compose/docker-compose.s3.yml`
  - MarkdownBrain + Caddy + RustFS（S3 兼容对象存储）
  - 需要对象存储或可扩展存储时推荐（也可替换成你自己的 S3）

## 前置条件

- 一台 Linux 服务器 / VPS，已安装 Docker 与 Docker Compose
- 一个域名，A/AAAA 记录指向服务器
- 放行 `80/443` 端口（Caddy TLS 需要）

## 快速启动

### 本地存储 + Caddy（推荐）

```bash
docker compose -f selfhosted/compose/docker-compose.local.yml up -d
docker compose -f selfhosted/compose/docker-compose.local.yml logs -f
```

### S3 兼容对象存储 + Caddy（包含 RustFS）

RustFS 在 Compose 内网启动。你仍需要一个浏览器可访问的资源域名/URL（用于加载图片/PDF 等）。

```bash
export S3_PUBLIC_URL=https://s3.your-domain.com
docker compose -f selfhosted/compose/docker-compose.s3.yml up -d
docker compose -f selfhosted/compose/docker-compose.s3.yml logs -f
```

## Console 访问（保持私有）

这些 Compose 文件会把 `9090` 端口绑定到宿主机的 `127.0.0.1`。使用 SSH 隧道访问：

```bash
ssh -L 9090:localhost:9090 user@your-server
open http://localhost:9090/console
```

首次访问会跳转到 `/console/init`，用于创建第一个管理员账号。

## 按需 TLS（可选）

设置 `CADDY_ON_DEMAND_TLS_ENABLED=true` 后，Caddy 会按需为你的域名自动签发证书。

工作原理：
- Caddy 会调用 `http://markdownbrain:9090/console/domain-check?domain=...`（ask）
- 只有当域名已在 Console 的 vault 列表中存在时，MarkdownBrain 才返回 `200`

注意：
- Console 端口必须保持私有（只允许 localhost 或内网访问）。

## Obsidian 插件发布

插件请求 `${SERVER_URL}/obsidian/...`。本仓库提供的 Caddyfile 会把 `/obsidian/*` 转发到 console（`:9090`），而公开站点仍由 frontend（`:8080`）提供。

推荐插件 `Server URL`：
- `https://notes.example.com`（与你的公开站点一致）

## 升级

```bash
git pull
docker compose -f selfhosted/compose/docker-compose.local.yml up -d --build
```

数据库迁移会在服务启动时自动执行。

## 常见问题

- Caddy TLS 失败：检查 DNS 是否已指向服务器，且 `80/443` 可从公网访问。
- S3 模式资源加载失败：`S3_PUBLIC_URL` 必须能被浏览器直接访问。
- 插件无法连接：确认公网可访问 `/obsidian/*`（由 Caddy 转发），以及 Publish Key 是否正确。

