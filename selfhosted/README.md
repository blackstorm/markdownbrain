# Self-hosting MarkdownBrain

[English](README.md) | [简体中文](README.zh-cn.md)

This directory provides production-ready Docker Compose setups.

## Overview

MarkdownBrain exposes two HTTP ports inside the container:

- Frontend: `8080` (public site)
- Console: `9090` (admin UI + publish API under `/obsidian/*`)

Security model (recommended):

- Keep Console private (bound to `127.0.0.1` on the host).
- Expose only the public site (`:80/:443`) through a reverse proxy (Caddy in this repo).

## Quick deploy (one command)

For a quick trial (no reverse proxy, local storage), run:

```bash
docker run -d --name markdownbrain --restart unless-stopped -p 8080:8080 -p 127.0.0.1:9090:9090 -v markdownbrain:/app/data -e STORAGE_TYPE=local ghcr.io/blackstorm/markdownbrain:latest
```

- Public site: `http://<your-server>:8080/`
- Console (SSH tunnel): `ssh -L 9090:localhost:9090 user@your-server`, then open `http://localhost:9090/console`

## Choose a deployment

- `minimal` (MarkdownBrain only)
  - Compose file: `selfhosted/compose/docker-compose.minimal.yml`
- `caddy` (MarkdownBrain + Caddy) (recommended)
  - Compose file: `selfhosted/compose/docker-compose.caddy.yml`
- `s3` (MarkdownBrain + Caddy + RustFS)
  - Compose file: `selfhosted/compose/docker-compose.s3.yml`

## Prerequisites

- A Linux server with Docker + Docker Compose
- A domain pointing to the server (A/AAAA record)
- Ports `80`/`443` open (for Caddy)

## Environment variables

Compose reads variables from `selfhosted/.env` (see `selfhosted/.env.example`).

There are two “layers” of variables:

- Compose variables: used by Docker Compose itself (image tags, ports).
- MarkdownBrain variables: passed into the `markdownbrain` container (the server reads them).

For the full MarkdownBrain server configuration reference, see [../README.md](../README.md#configuration).

| Name | Used by | Description | Default / example | Required |
|---|---|---|---|---|
| `MARKDOWNBRAIN_IMAGE` | Compose | Docker image tag to run | `ghcr.io/blackstorm/markdownbrain:latest` | Yes |
| `DATA_PATH` | MarkdownBrain | Base data directory inside the container | `/app/data` | No |
| `JAVA_OPTS` | MarkdownBrain | Extra JVM args for the MarkdownBrain container | `-Xms256m -Xmx512m` | No |
| `MARKDOWNBRAIN_LOG_LEVEL` | MarkdownBrain | App log level (Logback) | `INFO` | No |
| `CADDY_ON_DEMAND_TLS_ENABLED` | Caddy + MarkdownBrain | Enable Caddy on-demand TLS integration | `false` | No |
| `S3_PUBLIC_URL` | MarkdownBrain | Public base URL for browsers to fetch assets in S3 mode | `https://s3.your-domain.com` | Yes (S3) |
| `S3_ACCESS_KEY` | MarkdownBrain + RustFS | S3 access key (RustFS or your S3) | `rustfsadmin` | Yes (S3) |
| `S3_SECRET_KEY` | MarkdownBrain + RustFS | S3 secret key (RustFS or your S3) | `rustfsadmin` | Yes (S3) |
| `S3_BUCKET` | MarkdownBrain | S3 bucket name | `markdownbrain` | Yes (S3) |
| `S3_PUBLIC_PORT` | Compose | Host port for RustFS in the bundled S3 compose | `9000` | No |

### What Compose sets by default

The provided compose files already set key MarkdownBrain variables:

- `compose/docker-compose.caddy.yml` and `compose/docker-compose.minimal.yml`
  - `STORAGE_TYPE=local`
- `compose/docker-compose.s3.yml`
  - `STORAGE_TYPE=s3`
  - `S3_ENDPOINT=http://rustfs:9000` (or change it to your own S3 endpoint)
 
These correspond to:

- `selfhosted/compose/docker-compose.minimal.yml`
- `selfhosted/compose/docker-compose.caddy.yml`
- `selfhosted/compose/docker-compose.s3.yml`

You usually do not need to set `DATA_PATH` or `LOCAL_STORAGE_PATH` because the container persists `/app/data` and the defaults already live there:

- Default DB: `/app/data/markdownbrain.db`
- Default local storage: `/app/data/storage`

## Quickstart (recommended: Caddy)

1. Create `selfhosted/.env`.

```bash
cp selfhosted/.env.example selfhosted/.env
```

2. Edit `selfhosted/.env`.

- Pin a version for production (recommended): `MARKDOWNBRAIN_IMAGE=...:X.Y.Z`
- Enable Caddy on-demand TLS if you want automatic certs: `CADDY_ON_DEMAND_TLS_ENABLED=true`

3. Start.

```bash
docker compose --env-file selfhosted/.env -f selfhosted/compose/docker-compose.caddy.yml up -d
```

4. Access Console via SSH tunnel.

```bash
ssh -L 9090:localhost:9090 user@your-server
open http://localhost:9090/console
```

5. Initialize and publish.

- Create the first admin user at `/console/init`.
- Create a vault and set its domain.
- Copy the vault Publish Key and configure the Obsidian plugin.
- Visit `https://<your-domain>/`.

## Minimal mode (no Caddy)

Use this when you already have a reverse proxy / TLS in front:

```bash
docker compose --env-file selfhosted/.env -f selfhosted/compose/docker-compose.minimal.yml up -d
```

- Public site: `http://<your-server>:8080/`

## S3 mode (Caddy + RustFS)

This mode includes a bundled S3-compatible storage service (RustFS). RustFS is exposed on host port `${S3_PUBLIC_PORT:-9000}`.

- `S3_PUBLIC_URL` must be reachable by browsers (recommended to put it behind TLS/CDN).
- If you do not want to expose RustFS publicly, use your own S3 + CDN and set `S3_PUBLIC_URL` to the CDN base URL.

Start:

```bash
docker compose --env-file selfhosted/.env -f selfhosted/compose/docker-compose.s3.yml up -d
```

## Caddy files (when to use which)

The Caddy setup lives in `selfhosted/caddy/`:

- `selfhosted/compose/docker-compose.caddy.yml`
  - Runs MarkdownBrain + Caddy and wires ports/routes.
- `selfhosted/caddy/Caddyfile.simple`
  - Use when you manage TLS externally (Cloudflare / nginx / a load balancer).
  - Listens on `:80` and reverse-proxies:
    - `/obsidian/*` → `markdownbrain:9090`
    - everything else → `markdownbrain:8080`
- `selfhosted/caddy/Caddyfile.on-demand-tls`
  - Use when you want Caddy to obtain certificates automatically per domain.
  - Requires `CADDY_ON_DEMAND_TLS_ENABLED=true` and ports `80/443` open.
  - Uses `ask http://markdownbrain:9090/console/domain-check` so only domains registered in Console can get certs.
- `selfhosted/caddy/caddy-entrypoint.sh`
  - Small wrapper that selects `Caddyfile.simple` vs `Caddyfile.on-demand-tls` based on `CADDY_ON_DEMAND_TLS_ENABLED`.

## How on-demand TLS works

When `CADDY_ON_DEMAND_TLS_ENABLED=true`, Caddy will only issue a certificate if MarkdownBrain confirms the domain:

- Caddy calls `http://markdownbrain:9090/console/domain-check?domain=...`
- MarkdownBrain returns `200` only if the domain exists in your vault list

### Cloudflare notes (when using on-demand TLS)

On-demand TLS requires the public internet (ACME CA) to reach your server directly on ports `80/443`. If you enable Cloudflare proxy, Cloudflare terminates TLS and ACME challenges may not reach your origin.

Recommended setup:

1. In Cloudflare DNS, create `A`/`AAAA` record(s) for your vault domain pointing to your server IP.
2. Set **Proxy status = DNS only** for those records.
3. Ensure your server firewall/security group allows inbound `80` and `443`.
4. Set `CADDY_ON_DEMAND_TLS_ENABLED=true` and restart.

If you must keep Cloudflare proxy enabled, use `selfhosted/caddy/Caddyfile.simple` and let Cloudflare handle HTTPS at the edge (Caddy stays on `:80`), or switch to a DNS-01 challenge setup (not provided in this repo).

## Upgrade

```bash
docker compose --env-file selfhosted/.env -f selfhosted/compose/docker-compose.caddy.yml pull
docker compose --env-file selfhosted/.env -f selfhosted/compose/docker-compose.caddy.yml up -d
```

Database migrations run automatically on server startup.

## Backup

Persisted data is stored in the Docker volume mounted at `/app/data`.

At minimum, back up:

- SQLite DB: `markdownbrain.db`
- Secrets file: `.secrets.edn`

## Troubleshooting

- TLS fails: verify DNS points to the server and ports `80/443` are reachable.
- Assets fail to load in S3 mode: verify `S3_PUBLIC_URL` is browser-accessible.
- Plugin cannot connect: ensure `/obsidian/*` is reachable (via Caddy) and your Publish Key is correct.
