import { Plugin, TFile, Notice, PluginSettingTab, App, Setting } from 'obsidian';

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
