![MarkdownBrain Logo](assets/markdownbrain.png)
# MarkdownBrain

[English](README.md) | [简体中文](README.zh-cn.md)

Publish your Obsidian vault as a website you can self-host.

## What it is

MarkdownBrain is a personal publishing stack for Obsidian:

- Console: manage vaults, domains, and Publish Keys
- Frontend: serve the public site (notes, backlinks, assets)
- Obsidian plugin: publish notes and assets to your server

## MVP scope

- Publish Markdown notes and attachments
- Internal links and backlinks
- Custom domain per vault
- Local storage or S3-compatible object storage
- Per-vault Publish Key (renewable)
- Console shows last publish status, time, and error (snapshot)

## Quickstart (self-host)

1. Prepare a Linux server with Docker + Docker Compose, a domain (A/AAAA), and open ports `80/443`.
2. Create an env file.

```bash
cp selfhosted/.env.example selfhosted/.env
```

3. Start MarkdownBrain (local storage + Caddy, recommended).

```bash
docker compose --env-file selfhosted/.env -f selfhosted/compose/docker-compose.local.yml up -d
```

4. Access Console (private by default).

```bash
ssh -L 9090:localhost:9090 user@your-server
open http://localhost:9090/console
```

5. Create the first admin user at `/console/init`, then create a vault and copy its Publish Key.
6. Install and configure the Obsidian plugin.

Full guide: [selfhosted/README.md](selfhosted/README.md).

## Quickstart (development)

Prereqs: Java 25 (Temurin), Clojure CLI, Node.js 25, pnpm, Make.

```bash
make install
make dev
```

- Frontend: `http://localhost:8080`
- Console: `http://localhost:9090/console`

## Releases

### Docker image (GHCR)

- Image: `ghcr.io/blackstorm/markdownbrain`
- Tags:
  - `edge`, `sha-<7>` on `main`
  - `X.Y.Z`, `X.Y`, `X`, `latest` on git tag `vX.Y.Z`

### Obsidian plugin

- Download `markdownbrain-plugin.zip` from GitHub Releases.
- Unzip into `.obsidian/plugins/markdownbrain/`.

## Documentation

- Self-hosting: [selfhosted/README.md](selfhosted/README.md)
- Obsidian plugin: [obsidian-plugin/README.md](obsidian-plugin/README.md)
- Tests: [server/test/README.md](server/test/README.md)
- UI guidelines: [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md)

## Configuration

MarkdownBrain reads configuration from environment variables.

- Development: when running from `server/`, it also loads `.env` (for example `server/.env`).
- Docker: the server only loads `.env` if a `.env` file exists in the container working directory; most deployments should pass env vars via Docker Compose `environment:` or `--env-file`.

### MarkdownBrain (server) environment variables

| Name | Description | Default | Required |
|---|---|---|---|
| `ENVIRONMENT` | `development` or `production` | `development` | No |
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
| `MARKDOWNBRAIN_LOG_LEVEL` | App log level (Logback) | `DEBUG` | No |

Notes:

- Default DB path is `${DATA_PATH}/markdownbrain.db`.
- If `SESSION_SECRET` is omitted, MarkdownBrain generates one and stores it in `${DATA_PATH}/.secrets.edn`.
- In production, set `ENVIRONMENT=production` to enable secure cookies.

### Docker runtime environment variables

| Name | Description | Default | Required |
|---|---|---|---|
| `JAVA_OPTS` | Extra JVM args for the container | empty | No |

### Self-hosting (Compose) environment variables

These are used by the `selfhosted/` Docker Compose setup:

| Name | Description | Default | Required |
|---|---|---|---|
| `MARKDOWNBRAIN_IMAGE` | Docker image tag to run | `ghcr.io/blackstorm/markdownbrain:latest` | Yes |
| `S3_PUBLIC_PORT` | Host port for bundled RustFS (S3 compose only) | `9000` | No |

## License

- Server (`server/`): `AGPL-3.0-or-later` (see `LICENSE`)
- Obsidian plugin (`obsidian-plugin/`): MIT (see `obsidian-plugin/LICENSE`)
- Deployment configs (`selfhosted/`): MIT (see `selfhosted/LICENSE`)

## Third-party notices

This repository bundles third-party fonts and frontend libraries under their own licenses.
See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
