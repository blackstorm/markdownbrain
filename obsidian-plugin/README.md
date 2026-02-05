# Mdbrain Obsidian Plugin

[English](README.md) | [简体中文](README.zh-cn.md)

Publish your Obsidian vault to a Mdbrain server.

## Table of contents

- [Install (from release ZIP)](#toc-install)
- [Configure](#toc-configure)
- [Commands](#toc-commands)
- [Troubleshooting](#toc-troubleshooting)
- [Development](#toc-development)
- [Scripts](#toc-scripts)

<a id="toc-install"></a>
## Install (from release ZIP)

1. Download `mdbrain-plugin.zip` from GitHub Releases.
2. Unzip into your vault: `.obsidian/plugins/mdbrain/`.
3. Enable the plugin in Obsidian.

<a id="toc-configure"></a>
## Configure

Obsidian → Settings → Community plugins → Mdbrain:

- Publish URL: your published site base URL (for example `https://notes.example.com`)
- Publish Key: copy from Mdbrain Console → your vault card
- Auto publish: publish on file changes

The plugin calls `${publishUrl}/obsidian/...` endpoints. Your Publish URL must route `/obsidian/*` to the Mdbrain Console port (`9090`).
For self-hosting, use a reverse proxy that routes `/obsidian/*` → `9090` and everything else → `8080` (see [selfhosted/README.md](../selfhosted/README.md)).

<a id="toc-commands"></a>
## Commands

- Publish current file
- Publish all files (full publish)

<a id="toc-troubleshooting"></a>
## Troubleshooting

- `401 Unauthorized`: check Publish Key.
- `404 Not Found`: check Publish URL and reverse proxy routing for `/obsidian/*` (Publish API runs on port `9090`, not `8080`).
- Upload succeeds but assets do not load: verify your server storage configuration (especially `S3_PUBLIC_URL` for S3 mode).

<a id="toc-development"></a>
## Development

This repo includes a test vault at `vaults/test/`.

```bash
npm install -g pnpm@10.17.1
pnpm install
pnpm dev
```

Dev builds output to `../vaults/test/.obsidian/plugins/mdbrain/` with source maps.

<a id="toc-scripts"></a>
## Scripts

```bash
pnpm dev        # watch build into vaults/test
pnpm build      # production build to dist/
pnpm test       # vitest
pnpm check      # biome check
pnpm package    # build + create mdbrain-plugin.zip
```
