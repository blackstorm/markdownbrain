# Self-hosting MarkdownBrain

[English](README.md) | [简体中文](README.zh-cn.md)

This directory contains production-ready Docker Compose setups and Caddy configs.

## What you get

- Public site: notes are served from the **frontend** server (port `8080` in the container)
- Publish API: the Obsidian plugin calls `/obsidian/*`, proxied to the **console** server (port `9090` in the container)
- Admin UI: Console is intentionally **not** exposed publicly in the provided Compose files

## Directory layout

- `selfhosted/compose/`: Docker Compose files
- `selfhosted/caddy/`: Caddyfiles + entrypoint

## Choose a deployment

- `compose/docker-compose.minimal.yml`
  - MarkdownBrain only, local storage
  - For local testing / LAN use (no reverse proxy)
- `compose/docker-compose.local.yml`
  - MarkdownBrain + Caddy, local storage
  - Recommended for a single-node VPS
- `compose/docker-compose.s3.yml`
  - MarkdownBrain + Caddy + RustFS (S3-compatible)
  - Recommended if you want object storage (or replace RustFS with your own S3)

## Prerequisites

- A Linux server / VPS with Docker + Docker Compose
- A domain pointing to your server (A/AAAA record)
- Ports `80` and `443` open (for Caddy TLS)

## Quickstart

### Local storage + Caddy (recommended)

```bash
docker compose -f selfhosted/compose/docker-compose.local.yml up -d
docker compose -f selfhosted/compose/docker-compose.local.yml logs -f
```

### S3-compatible storage + Caddy (RustFS included)

RustFS is started inside the Compose network. You still need a public URL for browsers to fetch assets.

```bash
export S3_PUBLIC_URL=https://s3.your-domain.com
docker compose -f selfhosted/compose/docker-compose.s3.yml up -d
docker compose -f selfhosted/compose/docker-compose.s3.yml logs -f
```

## Console access (private)

In these Compose files, port `9090` is bound to `127.0.0.1` on the host. Access via SSH tunnel:

```bash
ssh -L 9090:localhost:9090 user@your-server
open http://localhost:9090/console
```

First run will redirect you to `/console/init` to create the initial admin user.

## On-demand TLS (optional)

Set `CADDY_ON_DEMAND_TLS_ENABLED=true` to let Caddy automatically obtain certificates.

How it works:
- Caddy uses “ask” to call `http://markdownbrain:9090/console/domain-check?domain=...`
- MarkdownBrain returns `200` only if the domain exists in your Console vault list

Important:
- Keep the console port private (localhost or internal network only).

## Obsidian plugin publishing

The plugin calls `${SERVER_URL}/obsidian/...`. In the provided Caddyfiles, `/obsidian/*` is proxied to the console server (`:9090`) while the public site stays on the frontend server (`:8080`).

Recommended plugin `Server URL`:
- `https://notes.example.com` (same as your published site)

## Upgrade

```bash
# pull latest images (if you use image tags) or git pull (if you build locally)
git pull

# restart
docker compose -f selfhosted/compose/docker-compose.local.yml up -d --build
```

Database migrations run automatically on server startup.

## Troubleshooting

- Caddy TLS fails: verify DNS points to the server and ports `80/443` are reachable.
- Assets don’t load on S3 mode: `S3_PUBLIC_URL` must be browser-accessible.
- Plugin can’t connect: ensure `/obsidian/*` is reachable from the internet (via Caddy) and your Publish Key is correct.
