# Self-hosting MarkdownBrain

[English](README.md) | [简体中文](README.zh-cn.md)

This directory provides production-ready Docker Compose setups.

## Table of contents

- [Overview](#toc-overview)
- [Quick deploy (one command)](#toc-quick-deploy)
- [Choose a deployment](#toc-choose-deployment)
- [Prerequisites](#toc-prerequisites)
- [Environment variables](#toc-environment-variables)
  - [Compose variables](#toc-compose-vars)
  - [MarkdownBrain server variables](#toc-server-vars)
  - [Docker runtime variables](#toc-docker-runtime-vars)
  - [What Compose sets by default](#toc-compose-defaults)
- [Quickstart (recommended: Caddy)](#toc-quickstart-caddy)
- [Minimal mode (no Caddy)](#toc-minimal)
- [S3 mode (Caddy + RustFS)](#toc-s3)
- [Caddy files (when to use which)](#toc-caddy-files)
- [How on-demand TLS works](#toc-on-demand-tls)
  - [Cloudflare notes (when using on-demand TLS)](#toc-cloudflare)
- [Upgrade](#toc-upgrade)
- [Backup](#toc-backup)
- [Troubleshooting](#toc-troubleshooting)

<a id="toc-overview"></a>
## Overview

MarkdownBrain exposes two HTTP ports inside the container:

- Frontend: `8080` (public site)
- Console: `9090` (admin UI + publish API under `/obsidian/*`)

Health checks:

- `GET /console/health` is loopback-only (returns `200` only for requests coming from `127.0.0.1` / `::1`).
  It is intended for local/container health checks, not public monitoring.

Security model (recommended):

- Keep Console private (bound to `127.0.0.1` on the host).
- Expose only the public site (`:80/:443`) through a reverse proxy (Caddy in this repo).
- Access Console through a private network (for example, Tailscale/VPN). Avoid exposing port `9090` to the public internet.
- The Docker image runs in `ENVIRONMENT=production` by default and Console sessions use `Secure` cookies.
  Accessing Console over plain HTTP (including SSH tunnel) can be unreliable; prefer an HTTPS access method for Console.

<a id="toc-quick-deploy"></a>
## Quick deploy (one command)

For a quick trial (no reverse proxy, local storage), run:

```bash
docker run -d --name markdownbrain --restart unless-stopped -p 8080:8080 -p 127.0.0.1:9090:9090 -v markdownbrain:/app/data -e STORAGE_TYPE=local ghcr.io/blackstorm/markdownbrain:latest
```

- Public site: `http://<your-server>:8080/`
- Console (recommended: Tailscale/private network): `http://<tailscale-ip>:9090/console` (optionally via HTTPS reverse proxy)
- Console (quick access via SSH tunnel): `ssh -L 9090:localhost:9090 user@your-server`, then open `http://localhost:9090/console`
  - Note: Console uses `Secure` cookies in `ENVIRONMENT=production`. Over plain HTTP (including SSH tunnel), login can be unreliable.
    Prefer an HTTPS access method for Console (even on an internal network).

<a id="toc-choose-deployment"></a>
## Choose a deployment

- `minimal` (MarkdownBrain only)
  - Compose file: `selfhosted/compose/docker-compose.minimal.yml`
- `caddy` (MarkdownBrain + Caddy) (recommended)
  - Compose file: `selfhosted/compose/docker-compose.caddy.yml`
- `s3` (MarkdownBrain + Caddy + RustFS)
  - Compose file: `selfhosted/compose/docker-compose.s3.yml`

<a id="toc-prerequisites"></a>
## Prerequisites

- A Linux server with Docker + Docker Compose
- A domain pointing to the server (A/AAAA record)
- Ports `80`/`443` open (for Caddy)

<a id="toc-environment-variables"></a>
## Environment variables

Compose reads variables from `selfhosted/.env` (see `selfhosted/.env.example`).

There are two “layers” of variables:

- Compose variables: used by Docker Compose itself (image tags, ports).
- MarkdownBrain variables: passed into the `markdownbrain` container (the server reads them).

For a short overview table, see [../README.md](../README.md#toc-configuration).

<a id="toc-compose-vars"></a>
### Compose variables

| Name | Description | Default / example | Required |
|---|---|---|---|
| `MARKDOWNBRAIN_IMAGE` | Docker image tag to run | `ghcr.io/blackstorm/markdownbrain:latest` | Yes |
| `S3_PUBLIC_PORT` | Host port for bundled RustFS (S3 mode only) | `9000` | No |

<a id="toc-server-vars"></a>
### MarkdownBrain server variables

| Name | Description | Default | Required |
|---|---|---|---|
| `ENVIRONMENT` | `development` or `production` | `production` (Docker image default) | No |
| `HOST` | Bind host for both servers | `0.0.0.0` | No |
| `FRONTEND_PORT` | Frontend server port | `8080` | No |
| `CONSOLE_PORT` | Console server port | `9090` | No |
| `DATA_PATH` | Base data directory (DB, secrets, local storage) | `data` (Docker image: `/app/data`) | No |
| `SESSION_SECRET` | Console session secret (string) | auto-generated | No |
| `STORAGE_TYPE` | Storage backend: `local` or `s3` | `local` | No |
| `LOCAL_STORAGE_PATH` | Local storage path when `STORAGE_TYPE=local` | `${DATA_PATH}/storage` | No |
| `S3_ENDPOINT` | S3 endpoint URL when `STORAGE_TYPE=s3` | - | Yes (S3) |
| `S3_ACCESS_KEY` | S3 access key when `STORAGE_TYPE=s3` | - | Yes (S3) |
| `S3_SECRET_KEY` | S3 secret key when `STORAGE_TYPE=s3` | - | Yes (S3) |
| `S3_REGION` | S3 region | `us-east-1` | No |
| `S3_BUCKET` | S3 bucket name | `markdownbrain` | No |
| `S3_PUBLIC_URL` | Public base URL for browsers to fetch assets | - | Yes (S3) |
| `CADDY_ON_DEMAND_TLS_ENABLED` | Enable `/console/domain-check` for Caddy on-demand TLS | `false` | No |
| `MARKDOWNBRAIN_LOG_LEVEL` | App log level (Logback) | `INFO` (Docker image) | No |

Notes:

- Default DB path is `${DATA_PATH}/markdownbrain.db`.
- If `SESSION_SECRET` is omitted, MarkdownBrain generates one and stores it in `${DATA_PATH}/.secrets.edn`.
- The Docker image runs in `ENVIRONMENT=production` by default (secure cookies enabled for Console sessions).
  If you access Console over plain HTTP, login can be unreliable; prefer HTTPS access for Console.

<a id="toc-docker-runtime-vars"></a>
### Docker runtime variables

| Name | Description | Default | Required |
|---|---|---|---|
| `JAVA_OPTS` | Extra JVM args for the container | empty | No |

<a id="toc-compose-defaults"></a>
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

<a id="toc-quickstart-caddy"></a>
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

4. Access Console (recommended: Tailscale/private network).

Recommended: access over Tailscale/private network, optionally via HTTPS reverse proxy.
Example: `http://<tailscale-ip>:9090/console`.

Quick access (SSH tunnel):

```bash
ssh -L 9090:localhost:9090 user@your-server
open http://localhost:9090/console
```

Note: Console uses `Secure` cookies in `ENVIRONMENT=production`. Over plain HTTP (including SSH tunnel), login can be unreliable.
Prefer an HTTPS access method for Console (for example, a private network + HTTPS reverse proxy).

5. Initialize and publish.

- Create the first admin user at `/console/init`.
- Create a vault and set its domain.
- Copy the vault Publish Key and configure the Obsidian plugin.
- Visit `https://<your-domain>/`.

<a id="toc-minimal"></a>
## Minimal mode (no Caddy)

Use this when you already have a reverse proxy / TLS in front:

```bash
docker compose --env-file selfhosted/.env -f selfhosted/compose/docker-compose.minimal.yml up -d
```

- Public site: `http://<your-server>:8080/`

<a id="toc-s3"></a>
## S3 mode (Caddy + RustFS)

This mode includes a bundled S3-compatible storage service (RustFS). RustFS is exposed on host port `${S3_PUBLIC_PORT:-9000}`.

- `S3_PUBLIC_URL` must be reachable by browsers (recommended to put it behind TLS/CDN).
- If you do not want to expose RustFS publicly, use your own S3 + CDN and set `S3_PUBLIC_URL` to the CDN base URL.

Start:

```bash
docker compose --env-file selfhosted/.env -f selfhosted/compose/docker-compose.s3.yml up -d
```

<a id="toc-caddy-files"></a>
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

<a id="toc-on-demand-tls"></a>
## How on-demand TLS works

When `CADDY_ON_DEMAND_TLS_ENABLED=true`, Caddy will only issue a certificate if MarkdownBrain confirms the domain:

- Caddy calls `http://markdownbrain:9090/console/domain-check?domain=...`
- MarkdownBrain returns `200` only if the domain exists in your vault list

<a id="toc-cloudflare"></a>
### Cloudflare notes (when using on-demand TLS)

On-demand TLS requires the public internet (ACME CA) to reach your server directly on ports `80/443`. If you enable Cloudflare proxy, Cloudflare terminates TLS and ACME challenges may not reach your origin.

Recommended setup:

1. In Cloudflare DNS, create `A`/`AAAA` record(s) for your vault domain pointing to your server IP.
2. Set **Proxy status = DNS only** for those records.
3. Ensure your server firewall/security group allows inbound `80` and `443`.
4. Set `CADDY_ON_DEMAND_TLS_ENABLED=true` and restart.

If you must keep Cloudflare proxy enabled, use `selfhosted/caddy/Caddyfile.simple` and let Cloudflare handle HTTPS at the edge (Caddy stays on `:80`), or switch to a DNS-01 challenge setup (not provided in this repo).

<a id="toc-upgrade"></a>
## Upgrade

```bash
docker compose --env-file selfhosted/.env -f selfhosted/compose/docker-compose.caddy.yml pull
docker compose --env-file selfhosted/.env -f selfhosted/compose/docker-compose.caddy.yml up -d
```

Database migrations run automatically on server startup.

<a id="toc-backup"></a>
## Backup

Persisted data is stored in the Docker volume mounted at `/app/data`.

At minimum, back up:

- SQLite DB: `markdownbrain.db`
- Secrets file: `.secrets.edn`

<a id="toc-troubleshooting"></a>
## Troubleshooting

- TLS fails: verify DNS points to the server and ports `80/443` are reachable.
- Assets fail to load in S3 mode: verify `S3_PUBLIC_URL` is browser-accessible.
- Plugin cannot connect: ensure `/obsidian/*` is reachable (via Caddy) and your Publish Key is correct.
