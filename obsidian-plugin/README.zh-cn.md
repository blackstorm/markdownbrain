# Mdbrain Obsidian 插件

[English](README.md) | [简体中文](README.zh-cn.md)

将你的 Obsidian vault 发布到 Mdbrain 服务器。

## 目录

- [安装（Release ZIP）](#toc-install)
- [配置](#toc-configure)
- [命令](#toc-commands)
- [常见问题](#toc-troubleshooting)
- [开发](#toc-development)
- [常用脚本](#toc-scripts)

<a id="toc-install"></a>
## 安装（Release ZIP）

1. 从 GitHub Releases 下载 `mdbrain-plugin.zip`。
2. 解压到 vault 的 `.obsidian/plugins/mdbrain/`。
3. 在 Obsidian 中启用插件。

<a id="toc-configure"></a>
## 配置

Obsidian → 设置 → 社区插件 → Mdbrain：

- Publish URL：你的站点地址（例如 `https://notes.example.com`）
- Publish Key：从 Mdbrain Console 复制
- 自动发布：文件变更时自动发布

插件会请求 `${publishUrl}/obsidian/...` 接口。Publish URL 必须能把 `/obsidian/*` 转发到 Mdbrain 的 Console 端口（`9090`）。
自托管时，建议通过反向代理实现：`/obsidian/*` → `9090`，其它路径 → `8080`（参考 [selfhosted/README.md](../selfhosted/README.md)）。

<a id="toc-commands"></a>
## 命令

- Publish current file（发布当前文件）
- Publish all files（全量发布）

<a id="toc-troubleshooting"></a>
## 常见问题

- 返回 `401 Unauthorized`：检查 Publish Key。
- 返回 `404 Not Found`：检查 Publish URL 与反向代理的 `/obsidian/*` 转发规则（Publish API 在 `9090` 端口，不在 `8080`）。
- 上传成功但资源无法加载：检查服务端存储配置（S3 模式重点确认 `S3_PUBLIC_URL`）。

<a id="toc-development"></a>
## 开发

仓库内置测试 vault：`vaults/test/`。

```bash
npm install -g pnpm@10.17.1
pnpm install
pnpm dev
```

开发模式会输出到 `../vaults/test/.obsidian/plugins/mdbrain/`，并启用 source map。

<a id="toc-scripts"></a>
## 常用脚本

```bash
pnpm dev        # 监听构建并输出到 vaults/test
pnpm build      # 生产构建到 dist/
pnpm test       # vitest
pnpm check      # biome check
pnpm package    # build + 生成 mdbrain-plugin.zip
```
