# MarkdownBrain

[![License](https://img.shields.io/github/license/blackstorm/markdownbrain)](https://github.com/blackstorm/markdownbrain/blob/main/LICENSE) [![Release](https://img.shields.io/github/v/release/blackstorm/markdownbrain)](https://github.com/blackstorm/markdownbrain/releases) [![GitHub Stars](https://img.shields.io/github/stars/blackstorm/markdownbrain)](https://github.com/blackstorm/markdownbrain)

[English](README.md) | [简体中文](README.zh-cn.md)

**MarkdownBrain is a complete self-hosted solution for publishing [Obsidian](https://obsidian.md/) notes as websites.**

It supports multiple vaults, automatic incremental sync, link parsing, and backlink display—designed to deliver a seamless publishing experience for digital gardens, blogs, documentation, and tutorial sites.

Built with `Clojure` and `HTMX` for a simple, fast, and maintainable architecture.

## Why MarkdownBrain

- **Truly self-hosted** — No SaaS or third-party platform dependencies; you own your data
- **Obsidian-native** — Full support for internal links, backlinks, and wiki-style references
- **Developer-friendly** — Flexible integration with local storage or S3-compatible backends
- **One-click publishing** — Sync your existing vault to a live site in seconds

## Features

- Fully self-hosted with complete control over deployment and data
- Support for multiple independent vaults
- Incremental and full sync modes for efficient publishing
- Native support for Obsidian notes and related assets
- Automatic parsing of internal links and backlinks
- Built-in custom domain support with automatic HTTPS
- Compatibility with local storage and S3-compatible object storage
- Customizable site logo and HTML templates

## Quickstart

**One command to try it out:**

```bash
docker run -d \
  --name markdownbrain \
  --restart unless-stopped \
  -p 8080:8080 \
  -p 127.0.0.1:9090:9090 \
  -v markdownbrain:/app/data \
  -e STORAGE_TYPE=local \
  ghcr.io/blackstorm/markdownbrain:latest
```

- Public site: `http://<your-server>:8080`
- Console: `http://localhost:9090/console` (bound to localhost by default for security; access via SSH tunnel, VPN, or other secure methods)

**Production deployment (with Caddy + auto TLS):**

```bash
# 1. Clone and configure
git clone https://github.com/blackstorm/markdownbrain.git
cd markdownbrain
cp selfhosted/.env.example selfhosted/.env

# 2. Start services
docker compose --env-file selfhosted/.env \
  -f selfhosted/compose/docker-compose.caddy.yml up -d

# 3. Access Console via SSH tunnel
ssh -L 9090:localhost:9090 user@your-server
open http://localhost:9090/console
```

Then create your first admin user at `/console/init`, set up a vault, and install the Obsidian plugin.

Full deployment guide: [selfhosted/README.md](selfhosted/README.md)

## Configuration

MarkdownBrain reads configuration from environment variables.

| Name | Description | Default | Required |
|---|---|---|---|
| `STORAGE_TYPE` | Storage backend: `local` or `s3` | `local` | No |
| `DATA_PATH` | Base data directory | `/app/data` | No |
| `CADDY_ON_DEMAND_TLS_ENABLED` | Enable automatic HTTPS certificates | `false` | No |
| `S3_ENDPOINT` | S3 endpoint URL | — | Yes (S3) |
| `S3_ACCESS_KEY` | S3 access key | — | Yes (S3) |
| `S3_SECRET_KEY` | S3 secret key | — | Yes (S3) |
| `S3_BUCKET` | S3 bucket name | `markdownbrain` | No |
| `S3_PUBLIC_URL` | Public URL for browser asset loading | — | Yes (S3) |

Full reference: [selfhosted/README.md](selfhosted/README.md#toc-environment-variables)

## FAQ

**What storage backends are supported?**

Local filesystem storage and any S3-compatible object storage (AWS S3, MinIO, RustFS, Cloudflare R2, etc.).

**Does it support backlinks?**

Yes. MarkdownBrain automatically parses Obsidian internal links (`[[note]]`) and displays backlinks on each published page.

**How are images and attachments handled?**

All assets referenced in your notes are uploaded alongside your content and served from the same domain or S3 storage.

**Can I use my own domain for each vault?**

Yes. Each vault can have its own custom domain with automatic HTTPS via Caddy's on-demand TLS.

## Development

Prerequisites: Java 25 (Temurin), Clojure CLI, Node.js 25, pnpm, Make.

```bash
make install
make dev
```

- Frontend: `http://localhost:8080`
- Console: `http://localhost:9090/console`

## Releases

### Docker image

- Image: `ghcr.io/blackstorm/markdownbrain`
- Tags: `latest`, `X.Y.Z`, `edge` (main branch)

### Obsidian plugin

Download `markdownbrain-plugin.zip` from [GitHub Releases](https://github.com/blackstorm/markdownbrain/releases) and extract to `.obsidian/plugins/markdownbrain/`.

## Contributing

Contributions are welcome! Please feel free to submit issues and pull requests.

## License

- Server (`server/`): AGPL-3.0-or-later
- Obsidian plugin (`obsidian-plugin/`): MIT
- Deployment configs (`selfhosted/`): MIT

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for third-party licenses.
