# MarkdownBrain Obsidian 插件

[English](README.md) | [简体中文](README.zh-cn.md)

将你的 Obsidian vault 发布到 MarkdownBrain 服务器。

## 安装

### 从 Release ZIP 安装

1. 从 GitHub Releases 下载 `markdownbrain-plugin.zip`
2. 解压到 vault 的 `.obsidian/plugins/markdownbrain/`
3. 在 Obsidian 中启用插件

### 从源码开发（开发模式）

仓库里自带一个测试 vault：`vaults/test/`。

```bash
corepack enable
pnpm install
pnpm dev
```

开发模式会输出到 `../vaults/test/.obsidian/plugins/markdownbrain/`，并启用 source map 方便调试。

## 配置

Obsidian → 设置 → 社区插件 → MarkdownBrain：

- **Server URL**：你对外发布站点的 base URL（例如 `https://notes.example.com`）
- **Publish Key**：从 MarkdownBrain Console 中复制
- **自动发布**：文件变更时自动发布

## 命令

- Sync current file（同步当前文件）
- Sync all files（同步全部文件）

## 常用脚本

```bash
pnpm dev        # 监听构建并输出到 vaults/test
pnpm build      # 生产构建到 dist/
pnpm test       # vitest
pnpm check      # biome check
pnpm package    # build + 生成 markdownbrain-plugin.zip
```

## 说明

- 插件会请求 `${serverUrl}/obsidian/...` 接口。
- 自托管时需要让反向代理把 `/obsidian/*` 转发到 MarkdownBrain 的 console 端口（参考 `selfhosted/README.md`）。

