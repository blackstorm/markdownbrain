# MarkdownBrain Obsidian Plugin

[English](README.md) | [简体中文](README.zh-cn.md)

Publish your Obsidian vault to a MarkdownBrain server.

## Table of contents

- [Install (from release ZIP)](#toc-install)
- [Configure](#toc-configure)
- [Commands](#toc-commands)
- [Troubleshooting](#toc-troubleshooting)
- [Development](#toc-development)
- [Scripts](#toc-scripts)

<a id="toc-install"></a>
## Install (from release ZIP)

1. Download `markdownbrain-plugin.zip` from GitHub Releases.
2. Unzip into your vault: `.obsidian/plugins/markdownbrain/`.
3. Enable the plugin in Obsidian.

<a id="toc-configure"></a>
## Configure

Obsidian → Settings → Community plugins → MarkdownBrain:

- Server URL: your published site base URL (for example `https://notes.example.com`)
- Publish Key: copy from MarkdownBrain Console → your vault card
- Auto publish: publish on file changes

The plugin calls `${serverUrl}/obsidian/...` endpoints. For self-hosting, your reverse proxy must route `/obsidian/*` to MarkdownBrain Console (see [selfhosted/README.md](../selfhosted/README.md)).

<a id="toc-commands"></a>
## Commands

- Sync current file
- Sync all files (full sync)

<a id="toc-troubleshooting"></a>
## Troubleshooting

- `401 Unauthorized`: check Publish Key.
- `404 Not Found`: check Server URL and reverse proxy routing for `/obsidian/*`.
- Upload succeeds but assets do not load: verify your server storage configuration (especially `S3_PUBLIC_URL` for S3 mode).

<a id="toc-development"></a>
## Development

This repo includes a test vault at `vaults/test/`.

```bash
npm install -g pnpm@10.17.1
pnpm install
pnpm dev
```

Dev builds output to `../vaults/test/.obsidian/plugins/markdownbrain/` with source maps.

<a id="toc-scripts"></a>
## Scripts

```bash
pnpm dev        # watch build into vaults/test
pnpm build      # production build to dist/
pnpm test       # vitest
pnpm check      # biome check
pnpm package    # build + create markdownbrain-plugin.zip
```
