# MarkdownBrain Obsidian Sync Plugin

Obsidian 同步插件，用于将 Markdown 文件同步到 MarkdownBrain 服务器。

## 开发

```bash
# 安装依赖
npm install

# 开发模式（监听文件变化）
npm run dev

# 构建生产版本（输出到 dist/ 目录）
npm run build

# 打包成 zip 文件
npm run package
```

## 构建说明

- **开发模式**: `npm run dev` 会输出到 `../vaults/test/.obsidian/plugins/markdownbrain/` 并启用 source map，方便调试
- **生产构建**: `npm run build` 会：
  1. 运行 TypeScript 类型检查
  2. 使用 esbuild 打包到 `dist/main.js`（不含 source map）
  3. 复制 `manifest.json` 和 `styles.css`（如果存在）到 `dist/` 目录
- **打包**: `npm run package` 会构建并创建 `markdownbrain-plugin.zip`，可以直接用于发布

## 安装插件

### 方法 1: 手动安装（开发模式）

1. 构建插件: `npm run build`
2. 将 `dist/` 目录中的文件复制到 Obsidian vault 的 `.obsidian/plugins/markdownbrain-sync/` 目录
3. 在 Obsidian 设置中启用插件

### 方法 2: 使用 zip 文件安装

1. 打包插件: `npm run package`
2. 解压 `markdownbrain-plugin.zip` 到 `.obsidian/plugins/markdownbrain-sync/`
3. 在 Obsidian 设置中启用插件

## 配置

在 Obsidian 设置 → 社区插件 → MarkdownBrain Sync 中配置：

- **服务器地址**: MarkdownBrain API 地址（例如：`https://api.markdownbrain.com`）
- **Publish Key**: 从 Console 获取的发布密钥（Publish Key）
- **自动同步**: 启用后文件修改时自动同步

### 测试连接

点击"测试"按钮验证配置是否正确，成功后会显示 Vault 信息。

## 命令

- **Sync current file**: 手动同步当前文件
- **Sync all files**: 手动同步所有 Markdown 文件

## 目录结构

```
obsidian-plugin/
├── dist/              # 构建输出目录
│   ├── main.js        # 打包后的插件代码
│   └── manifest.json  # 插件清单文件
├── main.ts            # 插件源码
├── manifest.json      # 插件清单（源文件）
├── package.json       # npm 配置
├── esbuild.config.mjs # 构建配置
├── tsconfig.json      # TypeScript 配置
└── README.md          # 本文件
```

## 技术栈

- TypeScript 5.9+
- esbuild (打包)
- Obsidian API 1.11+
