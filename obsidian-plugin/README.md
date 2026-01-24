# MarkdownBrain Obsidian Plugin

[English](README.md) | [简体中文](README.zh-cn.md)

Publish your Obsidian vault to a MarkdownBrain server.

## Install

### From release ZIP

1. Download `markdownbrain-plugin.zip` from GitHub Releases
2. Unzip into your vault: `.obsidian/plugins/markdownbrain/`
3. Enable the plugin in Obsidian

### From source (development)

This repo includes a test vault at `vaults/test/`.

```bash
corepack enable
pnpm install
pnpm dev
```

Dev build outputs to `../vaults/test/.obsidian/plugins/markdownbrain/` with source maps.

## Configuration

Obsidian → Settings → Community plugins → MarkdownBrain:

- **Server URL**: your published site base URL (e.g. `https://notes.example.com`)
- **Publish Key**: copy from MarkdownBrain Console → your vault card
- **Auto publish**: publish on file changes

## Commands

- Sync current file
- Sync all files

## Scripts

```bash
pnpm dev        # watch build into vaults/test
pnpm build      # production build to dist/
pnpm test       # vitest
pnpm check      # biome check
pnpm package    # build + create markdownbrain-plugin.zip
```

## Notes

- The plugin talks to `${serverUrl}/obsidian/...` endpoints.
- For self-hosting, make sure your reverse proxy routes `/obsidian/*` to the MarkdownBrain console port (see `selfhosted/README.md`).
