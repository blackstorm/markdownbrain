# Obsidian 插件代码示例 (TypeScript + ClojureScript)

## 项目结构

```
obsidian-plugin/
├── package.json          # NPM 依赖和构建脚本
├── tsconfig.json         # TypeScript 配置
├── manifest.json         # Obsidian 插件 manifest
├── main.ts              # TypeScript 入口（Obsidian API 包装）
├── shadow-cljs.edn      # ClojureScript 构建配置
└── src/
    └── sync.cljs        # ClojureScript 同步逻辑
```

## 1. obsidian-plugin/package.json

```json
{
  "name": "markdownbrain-sync",
  "version": "1.0.0",
  "description": "MarkdownBrain Obsidian sync plugin",
  "main": "main.js",
  "scripts": {
    "dev": "concurrently \"npm run dev:ts\" \"npm run dev:cljs\"",
    "dev:ts": "tsc --watch",
    "dev:cljs": "shadow-cljs watch plugin",
    "build": "npm run build:cljs && npm run build:ts",
    "build:ts": "tsc",
    "build:cljs": "shadow-cljs release plugin"
  },
  "keywords": ["obsidian", "plugin", "sync"],
  "author": "",
  "license": "MIT",
  "devDependencies": {
    "@types/node": "^20.10.0",
    "obsidian": "^1.5.3",
    "typescript": "^5.3.3",
    "shadow-cljs": "^2.26.2",
    "concurrently": "^8.2.2"
  }
}
```

## 2. obsidian-plugin/tsconfig.json

```json
{
  "compilerOptions": {
    "target": "ES2018",
    "module": "commonjs",
    "lib": ["ES2018", "DOM"],
    "outDir": ".",
    "strict": true,
    "esModuleInterop": true,
    "skipLibCheck": true,
    "forceConsistentCasingInFileNames": true,
    "moduleResolution": "node",
    "allowSyntheticDefaultImports": true
  },
  "include": ["main.ts"]
}
```

## 3. obsidian-plugin/manifest.json

```json
{
  "id": "markdownbrain-sync",
  "name": "MarkdownBrain Sync",
  "version": "1.0.0",
  "minAppVersion": "1.0.0",
  "description": "Sync your Obsidian vault to MarkdownBrain server",
  "author": "MarkdownBrain",
  "authorUrl": "https://github.com/yourname/markdownbrain",
  "isDesktopOnly": false
}
```

## 4. obsidian-plugin/shadow-cljs.edn

```clojure
{:source-paths ["src"]

 :dependencies
 [[cljs-http "0.1.46"]
  [com.cognitect/transit-cljs "0.8.280"]]

 :builds
 {:plugin
  {:target :node-library
   :output-to "sync.js"
   :exports {:sync markdownbrain-plugin.sync/sync-file
             :init markdownbrain-plugin.sync/init
             :destroy markdownbrain-plugin.sync/destroy}}}}
```

## 5. obsidian-plugin/main.ts - TypeScript 主文件

```typescript
import { Plugin, TFile, Notice } from 'obsidian';

// 导入 ClojureScript 编译的模块
const sync = require('./sync.js');

interface MarkdownBrainSettings {
    serverUrl: string;
    vaultId: string;
    syncToken: string;
    autoSync: boolean;
}

const DEFAULT_SETTINGS: MarkdownBrainSettings = {
    serverUrl: 'https://api.markdownbrain.com',
    vaultId: '',
    syncToken: '',
    autoSync: true
};

export default class MarkdownBrainPlugin extends Plugin {
    settings: MarkdownBrainSettings;

    async onload() {
        console.log('Loading MarkdownBrain plugin');

        await this.loadSettings();

        // 初始化 ClojureScript 同步模块
        sync.init({
            serverUrl: this.settings.serverUrl,
            vaultId: this.settings.vaultId,
            syncToken: this.settings.syncToken
        });

        // 监听文件创建
        this.registerEvent(
            this.app.vault.on('create', (file) => {
                if (file instanceof TFile && file.extension === 'md') {
                    this.handleFileChange(file, 'create');
                }
            })
        );

        // 监听文件修改
        this.registerEvent(
            this.app.vault.on('modify', (file) => {
                if (file instanceof TFile && file.extension === 'md') {
                    this.handleFileChange(file, 'modify');
                }
            })
        );

        // 监听文件删除
        this.registerEvent(
            this.app.vault.on('delete', (file) => {
                if (file instanceof TFile && file.extension === 'md') {
                    this.handleFileDelete(file);
                }
            })
        );

        // 添加设置面板
        this.addSettingTab(new MarkdownBrainSettingTab(this.app, this));

        // 添加命令：手动同步当前文件
        this.addCommand({
            id: 'sync-current-file',
            name: 'Sync current file',
            callback: () => {
                const file = this.app.workspace.getActiveFile();
                if (file) {
                    this.handleFileChange(file, 'modify');
                }
            }
        });

        // 添加命令：同步所有文件
        this.addCommand({
            id: 'sync-all-files',
            name: 'Sync all files',
            callback: () => {
                this.syncAllFiles();
            }
        });
    }

    async handleFileChange(file: TFile, action: 'create' | 'modify') {
        if (!this.settings.autoSync) {
            return;
        }

        try {
            // 读取文件内容
            const content = await this.app.vault.read(file);

            // 获取文件元数据
            const stat = file.stat;

            // 调用 ClojureScript 同步函数
            const result = await sync.sync({
                path: file.path,
                content: content,
                hash: this.hashString(content),
                mtime: new Date(stat.mtime).toISOString(),
                action: action
            });

            if (result.success) {
                console.log(`File ${action}: ${file.path}`);
            } else {
                new Notice(`同步失败: ${file.path}`);
            }
        } catch (error) {
            console.error('Sync error:', error);
            new Notice(`同步错误: ${error.message}`);
        }
    }

    async handleFileDelete(file: TFile) {
        if (!this.settings.autoSync) {
            return;
        }

        try {
            const result = await sync.sync({
                path: file.path,
                action: 'delete'
            });

            if (result.success) {
                console.log(`File deleted: ${file.path}`);
            }
        } catch (error) {
            console.error('Delete sync error:', error);
        }
    }

    async syncAllFiles() {
        const files = this.app.vault.getMarkdownFiles();
        new Notice(`开始同步 ${files.length} 个文件...`);

        let success = 0;
        let failed = 0;

        for (const file of files) {
            try {
                await this.handleFileChange(file, 'modify');
                success++;
            } catch (error) {
                failed++;
                console.error(`Failed to sync ${file.path}:`, error);
            }
        }

        new Notice(`同步完成: ${success} 成功, ${failed} 失败`);
    }

    // 简单的字符串哈希函数
    hashString(str: string): string {
        let hash = 0;
        for (let i = 0; i < str.length; i++) {
            const char = str.charCodeAt(i);
            hash = ((hash << 5) - hash) + char;
            hash = hash & hash;
        }
        return hash.toString(16);
    }

    onunload() {
        console.log('Unloading MarkdownBrain plugin');
        sync.destroy();
    }

    async loadSettings() {
        this.settings = Object.assign({}, DEFAULT_SETTINGS, await this.loadData());
    }

    async saveSettings() {
        await this.saveData(this.settings);
        // 更新 ClojureScript 配置
        sync.init({
            serverUrl: this.settings.serverUrl,
            vaultId: this.settings.vaultId,
            syncToken: this.settings.syncToken
        });
    }
}

// 设置面板
import { App, PluginSettingTab, Setting } from 'obsidian';

class MarkdownBrainSettingTab extends PluginSettingTab {
    plugin: MarkdownBrainPlugin;

    constructor(app: App, plugin: MarkdownBrainPlugin) {
        super(app, plugin);
        this.plugin = plugin;
    }

    display(): void {
        const { containerEl } = this;

        containerEl.empty();
        containerEl.createEl('h2', { text: 'MarkdownBrain 同步设置' });

        new Setting(containerEl)
            .setName('服务器地址')
            .setDesc('MarkdownBrain 服务器 API 地址')
            .addText(text => text
                .setPlaceholder('https://api.markdownbrain.com')
                .setValue(this.plugin.settings.serverUrl)
                .onChange(async (value) => {
                    this.plugin.settings.serverUrl = value;
                    await this.plugin.saveSettings();
                }));

        new Setting(containerEl)
            .setName('Vault ID')
            .setDesc('从管理后台获取的 Vault ID')
            .addText(text => text
                .setPlaceholder('xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx')
                .setValue(this.plugin.settings.vaultId)
                .onChange(async (value) => {
                    this.plugin.settings.vaultId = value;
                    await this.plugin.saveSettings();
                }));

        new Setting(containerEl)
            .setName('Sync Token')
            .setDesc('从管理后台获取的同步 Token')
            .addText(text => text
                .setPlaceholder('xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx')
                .setValue(this.plugin.settings.syncToken)
                .onChange(async (value) => {
                    this.plugin.settings.syncToken = value;
                    await this.plugin.saveSettings();
                }));

        new Setting(containerEl)
            .setName('自动同步')
            .setDesc('文件修改时自动同步到服务器')
            .addToggle(toggle => toggle
                .setValue(this.plugin.settings.autoSync)
                .onChange(async (value) => {
                    this.plugin.settings.autoSync = value;
                    await this.plugin.saveSettings();
                }));
    }
}
```

## 6. obsidian-plugin/src/sync.cljs - ClojureScript 同步逻辑

```clojure
(ns markdownbrain-plugin.sync
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [goog.object :as gobj])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;; 全局配置
(def ^:private config (atom nil))

;; 初始化配置
(defn ^:export init [opts]
  (let [js-config (js->clj opts :keywordize-keys true)]
    (reset! config js-config)
    (println "MarkdownBrain sync initialized:" js-config)))

;; 销毁
(defn ^:export destroy []
  (reset! config nil))

;; 同步文件
(defn ^:export sync [file-data]
  (go
    (let [js-data (js->clj file-data :keywordize-keys true)
          {:keys [serverUrl vaultId syncToken]} @config]

      (if (and serverUrl vaultId syncToken)
        (let [url (str serverUrl "/api/sync")
              payload (merge js-data
                            {:vault_id vaultId
                             :sync_token syncToken})
              response (<! (http/post url
                                      {:json-params payload
                                       :headers {"Authorization" (str "Bearer " vaultId ":" syncToken)}}))]

          (if (:success response)
            (do
              (println "Sync success:" (:path js-data))
              (clj->js {:success true}))
            (do
              (println "Sync failed:" (:error-text response))
              (clj->js {:success false
                       :error (:error-text response)}))))

        (do
          (println "Config not initialized")
          (clj->js {:success false
                   :error "Plugin not configured"}))))))

;; 批量同步
(defn ^:export sync-batch [files-data]
  (go
    (let [files (js->clj files-data :keywordize-keys true)
          results (atom [])]

      (doseq [file files]
        (let [result (<! (sync (clj->js file)))]
          (swap! results conj result)))

      (clj->js @results))))
```

## 7. 构建和安装插件

```bash
cd obsidian-plugin

# 安装依赖
npm install

# 开发模式（监听文件变化自动编译）
npm run dev

# 生产构建
npm run build
```

## 8. 安装到 Obsidian

```bash
# 复制插件文件到 Obsidian vault
cp -r obsidian-plugin /path/to/your/vault/.obsidian/plugins/markdownbrain-sync/

# 或创建符号链接（开发时更方便）
ln -s $(pwd)/obsidian-plugin /path/to/your/vault/.obsidian/plugins/markdownbrain-sync
```

## 9. 使用说明

1. 在 Obsidian 中启用插件：Settings → Community plugins → MarkdownBrain Sync
2. 配置插件：
   - 服务器地址: `https://api.markdownbrain.com`
   - Vault ID: 从管理后台复制
   - Sync Token: 从管理后台复制
3. 启用"自动同步"
4. 编辑任何 Markdown 文件，插件会自动同步

## 10. 手动同步命令

按 `Ctrl+P`（或 `Cmd+P`）打开命令面板，输入：
- `MarkdownBrain: Sync current file` - 同步当前文件
- `MarkdownBrain: Sync all files` - 同步所有文件

## 11. 调试

在 Obsidian 开发者工具（Ctrl+Shift+I）中查看日志：

```javascript
// 查看插件状态
app.plugins.getPlugin('markdownbrain-sync')

// 查看设置
app.plugins.getPlugin('markdownbrain-sync').settings
```

## 12. 发布插件

1. 更新 `manifest.json` 中的版本号
2. 创建 `versions.json`:

```json
{
  "1.0.0": "1.0.0"
}
```

3. 打包发布：

```bash
npm run build
zip markdownbrain-sync.zip main.js manifest.json sync.js
```

## 目录结构最终检查

```
obsidian-plugin/
├── main.js                  ← TypeScript 编译输出
├── main.ts                  ← TypeScript 源码
├── sync.js                  ← ClojureScript 编译输出
├── manifest.json            ← Obsidian 元数据
├── package.json
├── tsconfig.json
├── shadow-cljs.edn
└── src/
    └── sync.cljs           ← ClojureScript 源码
```
