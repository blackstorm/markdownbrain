![MarkdownBrain Logo](assets/markdownbrain.png)
# MarkdownBrain

[![License](https://img.shields.io/github/license/blackstorm/markdownbrain)](https://github.com/blackstorm/markdownbrain/blob/main/LICENSE) [![Release](https://img.shields.io/github/v/release/blackstorm/markdownbrain)](https://github.com/blackstorm/markdownbrain/releases) [![GitHub Stars](https://img.shields.io/github/stars/blackstorm/markdownbrain)](https://github.com/blackstorm/markdownbrain) [![Top Language](https://img.shields.io/github/languages/top/blackstorm/markdownbrain)](https://github.com/blackstorm/markdownbrain) [![Last Commit](https://img.shields.io/github/last-commit/blackstorm/markdownbrain)](https://github.com/blackstorm/markdownbrain/commits)

[English](README.md) | [简体中文](README.zh-cn.md)

Publish your Obsidian vault as a website you can self-host.

<a id="toc-what-it-is"></a>
## What it is

MarkdownBrain is a personal publishing stack for Obsidian:

- Console: manage vaults, domains, and Publish Keys
- Frontend: serve the public site (notes, backlinks, assets)
- Obsidian plugin: publish notes and assets to your server

<a id="toc-features"></a>
## Features

- Publish Markdown notes and attachments
- Internal links and backlinks
- Custom domain per vault
- Local storage or S3-compatible object storage
- Per-vault Publish Key (renewable)
- Console shows last publish status, time, and error (snapshot)

<a id="toc-quickstart"></a>
## Quickstart

1. Prepare a Linux server with Docker + Docker Compose, a domain (A/AAAA), and open ports `80/443`.
2. Create an env file.

```bash
cp selfhosted/.env.example selfhosted/.env
```

3. Start MarkdownBrain (local storage + Caddy, recommended).

```bash
docker compose --env-file selfhosted/.env -f selfhosted/compose/docker-compose.caddy.yml up -d
```

4. Access Console (private by default).

```bash
ssh -L 9090:localhost:9090 user@your-server
open http://localhost:9090/console
```

5. Create the first admin user at `/console/init`, then create a vault and copy its Publish Key.
6. Install and configure the Obsidian plugin.

Full guide: [selfhosted/README.md](selfhosted/README.md).

<a id="toc-configuration"></a>
### Configuration

MarkdownBrain reads configuration from environment variables.

- Development: when running from `server/`, it also loads `.env` (for example `server/.env`).
- Docker: the server only loads `.env` if a `.env` file exists in the container working directory; most deployments should pass env vars via Docker Compose `environment:` or `--env-file`.

<a id="toc-config-server-env"></a>
#### Environment variables (overview)

| Name | Used by | Description | Default / example | Required |
|---|---|---|---|---|
| `MARKDOWNBRAIN_IMAGE` | Compose | Docker image tag to run | `ghcr.io/blackstorm/markdownbrain:latest` | Yes |
| `DATA_PATH` | MarkdownBrain | Base data directory inside the container | `/app/data` | No |
| `JAVA_OPTS` | MarkdownBrain | Extra JVM args for the container | `-Xms256m -Xmx512m` | No |
| `MARKDOWNBRAIN_LOG_LEVEL` | MarkdownBrain | App log level (Logback) | `INFO` | No |
| `CADDY_ON_DEMAND_TLS_ENABLED` | Caddy + MarkdownBrain | Enable Caddy on-demand TLS integration | `false` | No |
| `S3_PUBLIC_URL` | MarkdownBrain | Public base URL for browsers to fetch assets in S3 mode | `https://s3.your-domain.com` | Yes (S3) |
| `S3_ACCESS_KEY` | MarkdownBrain + RustFS | S3 access key (RustFS or your S3) | `rustfsadmin` | Yes (S3) |
| `S3_SECRET_KEY` | MarkdownBrain + RustFS | S3 secret key (RustFS or your S3) | `rustfsadmin` | Yes (S3) |
| `S3_BUCKET` | MarkdownBrain | S3 bucket name | `markdownbrain` | Yes (S3) |
| `S3_PUBLIC_PORT` | Compose | Host port for RustFS in the bundled S3 compose | `9000` | No |

Full reference (all server env vars, defaults, and when to use them): [selfhosted/README.md](selfhosted/README.md#toc-environment-variables).

<a id="toc-quickstart-development"></a>
## Development

Prereqs: Java 25 (Temurin), Clojure CLI, Node.js 25, pnpm, Make.

```bash
make install
make dev
```

- Frontend: `http://localhost:8080`
- Console: `http://localhost:9090/console`

<a id="toc-releases"></a>
## Releases

<a id="toc-docker-image-ghcr"></a>
### Docker image (GHCR)

- Image: `ghcr.io/blackstorm/markdownbrain`
- Tags:
  - `edge`, `sha-<7>` on `main`
  - `X.Y.Z`, `X.Y`, `X`, `latest` on git tag `vX.Y.Z`

<a id="toc-obsidian-plugin"></a>
### Obsidian plugin

- Download `markdownbrain-plugin.zip` from GitHub Releases.
- Unzip into `.obsidian/plugins/markdownbrain/`.

<a id="toc-license"></a>
## License

- Server (`server/`): `AGPL-3.0-or-later` (see `LICENSE`)
- Obsidian plugin (`obsidian-plugin/`): MIT (see `obsidian-plugin/LICENSE`)
- Deployment configs (`selfhosted/`): MIT (see `selfhosted/LICENSE`)

<a id="toc-third-party-notices"></a>
## Third-party notices

This repository bundles third-party fonts and frontend libraries under their own licenses.
See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
