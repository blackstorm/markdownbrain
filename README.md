# MarkdownBrain

Make your digital garden with MarkdownBrain.

## Quick Start

### Create config.yml

```bash
echo 'lang: "en"
root_note_name: "Welcome"
name: "MarkdownBrain"
description: "MarkdownBrain"
api_key: "4j6NByDw3JCiTxYHGiBqqIPUpcZFurwy"' > config.yml
```

### Run Server

```bash
docker run -dit --name markdownbrain -v $(pwd)/config.yml:/markdownbrain/config.yml -p 3000:3000 ghcr.io/blackstorm/markdownbrain-server:latest
```