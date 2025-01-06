<p align="center">
	<a href="https://markdownbrain.com">
		<picture>
			<source media="(prefers-color-scheme: dark)" srcset="screenshots/markdownbrain.png">
			<source media="(prefers-color-scheme: light)" srcset="screenshots/markdownbrain.png">
			<img src="screenshots/markdownbrain.png" alt="markdownbrain">
		</picture>
	</a>
	<br>
</p>
<h1 align="center">MarkdownBrain</h1>
<p align="center">Make your digital garden with MarkdownBrain.</p>
<p align="center">
<a href="https://github.com/blackstorm/markdownbrain/actions/workflows/release.yml">
  <img src="https://github.com/blackstorm/markdownbrain/actions/workflows/release.yml/badge.svg" alt="MarkdownBrain Build">
</a>
<a href="https://github.com/blackstorm/markdownbrain/actions/workflows/release-docker.yml">
  <img src="https://github.com/blackstorm/markdownbrain/actions/workflows/release-docker.yml/badge.svg" alt="MarkdownBrain Build">
</a>
<a href="LICENSE.md">
  <img src="https://img.shields.io/badge/license-AGPLv3-blue.svg" alt="License">
</a>
</p>
<p align="center">
  <a href="https://github.com/blackstorm/markdownbrain/releases">Releases</a> ·
  <a href="https://markdownbrain.com">Documentation</a>
</p>

## Quick Start

### Client

#### Create client config.yml

```bash
echo 'source: "~/Library/Mobile Documents/com~apple~CloudDocs/obsidian/example"
server: "https://your-server-url"
api_key: "1234567890"
ignores:
  - "Templates"' > config.yml
```

> Note: The `source` is the path to your Obsidian vault.

#### Run cli

```bash
curl -L https://github.com/blackstorm/markdownbrain/releases/download/v0.1.1/markdownbrain-cli-darwin-amd64 -o markdownbrain-client
chmod +x markdownbrain-client
./markdownbrain-client -c config.yml
```
> Note: Before running the client, ensure the `server` is running.

### Server

#### Create config.yml
```bash
echo 'lang: "en"
root_note_name: "Welcome"
name: "MarkdownBrain"
description: "MarkdownBrain"
api_key: "1234567890"' > config.yml
```

#### Run Server

```bash
docker run -dit --name markdownbrain -v $(pwd)/config.yml:/markdownbrain/config.yml -p 3000:3000 ghcr.io/blackstorm/markdownbrain-server:latest
```

## Documentation

[MarkdownBrain.com](https://markdownbrain.com)

## License

[AGPLv3](LICENSE.md)
