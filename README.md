# MarkdownBrain

[English](README.md) | [简体中文](README.zh-cn.md)

Publish your Obsidian vault as a website you can self-host.

MarkdownBrain includes:
- a **Console** (admin UI) to manage sites/domains/publish keys
- a **Frontend** (public site) to browse published notes
- an **Obsidian plugin** that publishes notes/assets to your server

## MVP Features

- Publish Markdown notes + attachments (images/PDF/audio/video)
- Custom domain per vault
- On-demand TLS via Caddy (optional)
- Internal links + backlinks
- Per-vault publish key (renewable)
- Console shows last publish status/time/error (snapshot)

## Quickstart (Development)

Prereqs: Java (Temurin 21+ recommended), Clojure CLI, Node.js (for CSS build), Make.

```bash
make install
make dev
# Frontend: http://localhost:8080
# Console:  http://localhost:9090/console
```

Other useful commands:

```bash
make backend-test
make frontend-dev
make plugin-dev
make build
```

## Quickstart (Self-host)

See `selfhosted/README.md` for a detailed guide.

Local storage + Caddy (recommended for single-node):

```bash
docker compose -f selfhosted/compose/docker-compose.local.yml up -d
```

S3-compatible storage + Caddy:

```bash
export S3_PUBLIC_URL=https://s3.your-domain.com
docker compose -f selfhosted/compose/docker-compose.s3.yml up -d
```

Console is bound to `127.0.0.1:9090` in the provided Compose files. Access it via SSH tunnel:

```bash
ssh -L 9090:localhost:9090 user@your-server
open http://localhost:9090/console
```

## Configuration

MarkdownBrain reads env vars from the environment and `server/.env` (development).

| Env var | Description | Required |
|---|---|---|
| `ENVIRONMENT` | `development` or `production` | No |
| `HOST` | Bind host (defaults to `0.0.0.0`) | No |
| `FRONTEND_PORT` | Public frontend port (default `8080`) | No |
| `CONSOLE_PORT` | Console port (default `9090`) | No |
| `DB_PATH` | SQLite DB path (default `data/markdownbrain.db`) | No |
| `SESSION_SECRET` | Console session secret (auto-generated if omitted) | No |
| `STORAGE_TYPE` | `local` or `s3` | No |
| `LOCAL_STORAGE_PATH` | Local object storage path (default `./data/storage`) | No |
| `S3_ENDPOINT` | S3 endpoint (required when `STORAGE_TYPE=s3`) | Yes (S3) |
| `S3_ACCESS_KEY` | S3 access key | Yes (S3) |
| `S3_SECRET_KEY` | S3 secret key | Yes (S3) |
| `S3_REGION` | S3 region (default `us-east-1`) | No |
| `S3_BUCKET` | S3 bucket (default `markdownbrain`) | No |
| `S3_PUBLIC_URL` | Public base URL for browser asset loading | Yes (S3) |
| `CADDY_ON_DEMAND_TLS_ENABLED` | `true` to enable Caddy on-demand TLS | No |

Notes:
- `S3_PUBLIC_URL` must be reachable by browsers. Assets are loaded directly from it (not proxied via the app).
- If `SESSION_SECRET` is omitted, it is generated and stored in `data/.secrets.edn` next to the DB file.

## Obsidian Plugin

- Plugin docs: `obsidian-plugin/README.md`
- Install (from release): unzip `markdownbrain-plugin.zip` into `.obsidian/plugins/markdownbrain/`
- Configure:
  - Server URL: your MarkdownBrain base URL
  - Publish Key: copy from Console → your vault card

## Repository Layout

- `server/`: Clojure backend + templates + static assets
- `obsidian-plugin/`: Obsidian plugin (TypeScript)
- `selfhosted/`: Docker Compose + Caddy configs

## Third-party assets

This repository ships third-party fonts and frontend libraries under their own licenses.
See `THIRD_PARTY_NOTICES.md` and files under `server/resources/publics/shared/`.

## License

MIT. See `LICENSE.md`.
