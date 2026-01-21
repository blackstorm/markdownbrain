## 文件结构

- `selfhosted/compose/`：Docker Compose 配置
- `selfhosted/caddy/`：Caddy 配置与启动脚本

## Compose profiles

- `compose/docker-compose.minimal.yml`: only the MarkdownBrain server (local storage).
- `compose/docker-compose.local.yml`: MarkdownBrain + Caddy + local storage.
- `compose/docker-compose.s3.yml`: MarkdownBrain + Caddy + S3 (with RustFS).

## On-demand TLS

When `CADDY_ON_DEMAND_TLS_ENABLED=true`, Caddy calls `/console/domain-check` to
validate requested domains. Keep the console port private (bound to localhost or
internal network only).

## 何时使用哪种部署

- 最小部署（仅 server）：本地开发/测试、没有公网域名或不需要反向代理。
- 本地存储 + Caddy：生产使用，本地磁盘存储资源，适合单机部署。
- S3 + Caddy（含 RustFS）：生产使用，资源存储在对象存储，适合需要可扩展存储的场景。

## 如何启动

最小部署：

```bash
docker compose -f selfhosted/compose/docker-compose.minimal.yml up -d
```

本地存储 + Caddy：

```bash
docker compose -f selfhosted/compose/docker-compose.local.yml up -d
```

S3 + Caddy（含 RustFS）：

```bash
export S3_PUBLIC_URL=https://s3.your-domain.com
docker compose -f selfhosted/compose/docker-compose.s3.yml up -d
```

## Console 访问

Console 端口 (9090) 仅绑定到 localhost。需要通过 SSH 隧道访问：

```bash
ssh -L 9090:localhost:9090 user@your-server
```

然后访问 `http://localhost:9090/console`。
