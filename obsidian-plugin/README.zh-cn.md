# MarkdownBrain Obsidian 插件

[English](README.md) | [简体中文](README.zh-cn.md)

将你的 Obsidian vault 发布到 MarkdownBrain 服务器。

## 安装（Release ZIP）

1. 从 GitHub Releases 下载 `markdownbrain-plugin.zip`。
2. 解压到 vault 的 `.obsidian/plugins/markdownbrain/`。
3. 在 Obsidian 中启用插件。

## 配置

Obsidian → 设置 → 社区插件 → MarkdownBrain：

- Server URL：你的站点地址（例如 `https://notes.example.com`）
- Publish Key：从 MarkdownBrain Console 复制
- 自动发布：文件变更时自动发布

插件会请求 `${serverUrl}/obsidian/...` 接口。自托管时，需要让反向代理把 `/obsidian/*` 转发到 MarkdownBrain 的 Console 端口（参考 [selfhosted/README.md](../selfhosted/README.md)）。

## 命令

- Sync current file（同步当前文件）
- Sync all files（全量同步）

## 常见问题

- 返回 `401 Unauthorized`：检查 Publish Key。
- 返回 `404 Not Found`：检查 Server URL 与反向代理的 `/obsidian/*` 转发规则。
- 上传成功但资源无法加载：检查服务端存储配置（S3 模式重点确认 `S3_PUBLIC_URL`）。

## 开发

仓库内置测试 vault：`vaults/test/`。

```bash
npm install -g pnpm@10.17.1
pnpm install
pnpm dev
```

开发模式会输出到 `../vaults/test/.obsidian/plugins/markdownbrain/`，并启用 source map。

## 常用脚本

```bash
pnpm dev        # 监听构建并输出到 vaults/test
pnpm build      # 生产构建到 dist/
pnpm test       # vitest
pnpm check      # biome check
pnpm package    # build + 生成 markdownbrain-plugin.zip
```
