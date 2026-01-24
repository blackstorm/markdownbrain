# MarkdownBrain

[![License](https://img.shields.io/github/license/blackstorm/markdownbrain)](https://github.com/blackstorm/markdownbrain/blob/main/LICENSE) [![Top Language](https://img.shields.io/github/languages/top/blackstorm/markdownbrain)](https://github.com/blackstorm/markdownbrain) [![Last Commit](https://img.shields.io/github/last-commit/blackstorm/markdownbrain)](https://github.com/blackstorm/markdownbrain/commits) [![Release](https://img.shields.io/github/v/release/blackstorm/markdownbrain)](https://github.com/blackstorm/markdownbrain/releases) [![GitHub Stars](https://img.shields.io/github/stars/blackstorm/markdownbrain)](https://github.com/blackstorm/markdownbrain)

[English](README.md) | [简体中文](README.zh-cn.md)

MarkdownBrain is a self-hosted solution for publishing [Obsidian](https://obsidian.md/) notes, built with `Clojure` and `HTMX`. It transforms your notes into beautiful websites—perfect for digital gardens, blogs, documentation, or tutorials. It consists of an Obsidian plugin and a backend Console: the plugin publishes local content to the server, while the Console manages vaults, domains, and more. If you already have a vault, just click sync once to create your site.

<a id="toc-features"></a>
## Features

- Fully self-hosted with complete control over deployment and data
- Unlimited independent vault publishing
- Incremental and full publish modes
- Native support for Obsidian notes, attachments, and assets
- Automatic parsing of Obsidian internal links and backlink structure
- Built-in custom domain support with automatic HTTPS certificate configuration
- Compatible with local storage and S3-protocol object storage
- Site logo and HTML customization support

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

| Name | Description | Default / example | Required |
|---|---|---|---|
| `MARKDOWNBRAIN_IMAGE` | Docker image tag to run | `ghcr.io/blackstorm/markdownbrain:latest` | Yes |
| `DATA_PATH` | Base data directory inside the container | `/app/data` | No |
| `JAVA_OPTS` | Extra JVM args for the container | `-Xms256m -Xmx512m` | No |
| `MARKDOWNBRAIN_LOG_LEVEL` | App log level (Logback) | `INFO` | No |
| `CADDY_ON_DEMAND_TLS_ENABLED` | Enable Caddy on-demand TLS integration | `false` | No |
| `S3_PUBLIC_URL` | Public base URL for browsers to fetch assets in S3 mode | `https://s3.your-domain.com` | Yes (S3) |
| `S3_ACCESS_KEY` | S3 access key (RustFS or your S3) | `rustfsadmin` | Yes (S3) |
| `S3_SECRET_KEY` | S3 secret key (RustFS or your S3) | `rustfsadmin` | Yes (S3) |
| `S3_BUCKET` | S3 bucket name | `markdownbrain` | Yes (S3) |
| `S3_PUBLIC_PORT` | Host port for RustFS in the bundled S3 compose | `9000` | No |

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
