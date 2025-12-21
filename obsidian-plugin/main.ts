import { Plugin, TFile, Notice, PluginSettingTab, App, Setting, requestUrl } from 'obsidian';

interface MarkdownBrainSettings {
    serverUrl: string;
    syncKey: string;
    autoSync: boolean;
}

const DEFAULT_SETTINGS: MarkdownBrainSettings = {
    serverUrl: 'https://api.markdownbrain.com',
    syncKey: '',
    autoSync: true
};

// 同步配置
interface SyncConfig {
    serverUrl: string;
    syncKey: string;
}

// 同步数据
interface SyncData {
    path: string;
    content?: string;
    hash?: string;
    mtime?: string;
    action: 'create' | 'modify' | 'delete';
}

// Vault 信息
interface VaultInfo {
    id: string;
    name: string;
    domain: string;
    'created-at': string;
}

// 同步管理器
class SyncManager {
    private config: SyncConfig;

    constructor(config: SyncConfig) {
        this.config = config;
    }

    updateConfig(config: SyncConfig) {
        this.config = config;
    }

    async testConnection(timeout: number = 30000): Promise<{ success: boolean; vaultInfo?: VaultInfo; error?: string }> {
        try {
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), timeout);

            const response = await requestUrl({
                url: `${this.config.serverUrl}/api/vault/info`,
                method: 'GET',
                headers: {
                    'Authorization': `Bearer ${this.config.syncKey}`
                },
                throw: false
            });

            clearTimeout(timeoutId);

            if (response.status === 200) {
                const data = response.json;
                return {
                    success: true,
                    vaultInfo: data.vault
                };
            } else {
                return {
                    success: false,
                    error: `HTTP ${response.status}: ${response.text || 'Unknown error'}`
                };
            }
        } catch (error: any) {
            if (error?.name === 'AbortError') {
                return {
                    success: false,
                    error: '连接超时（30秒）'
                };
            }
            return {
                success: false,
                error: error instanceof Error ? error.message : 'Unknown error'
            };
        }
    }

    async sync(data: SyncData): Promise<{ success: boolean; error?: string }> {
        try {
            const response = await requestUrl({
                url: `${this.config.serverUrl}/api/sync`,
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${this.config.syncKey}`
                },
                body: JSON.stringify(data),
                throw: false
            });

            if (response.status === 200) {
                return { success: true };
            } else {
                return {
                    success: false,
                    error: `HTTP ${response.status}: ${response.text || 'Sync failed'}`
                };
            }
        } catch (error) {
            console.error('Sync error:', error);
            return {
                success: false,
                error: error instanceof Error ? error.message : 'Unknown error'
            };
        }
    }

    destroy() {
        // Cleanup if needed
    }
}

export default class MarkdownBrainPlugin extends Plugin {
    settings!: MarkdownBrainSettings;
    syncManager!: SyncManager;  // 改为 public，供设置面板使用

    async onload() {
        console.log('Loading MarkdownBrain plugin');

        await this.loadSettings();

        // 初始化同步管理器
        this.syncManager = new SyncManager({
            serverUrl: this.settings.serverUrl,
            syncKey: this.settings.syncKey
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

            // 调用同步函数
            const result = await this.syncManager.sync({
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
            new Notice(`同步错误: ${error instanceof Error ? error.message : 'Unknown error'}`);
        }
    }

    async handleFileDelete(file: TFile) {
        if (!this.settings.autoSync) {
            return;
        }

        try {
            const result = await this.syncManager.sync({
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
        this.syncManager.destroy();
    }

    async loadSettings() {
        this.settings = Object.assign({}, DEFAULT_SETTINGS, await this.loadData());
    }

    async saveSettings() {
        await this.saveData(this.settings);
        // 更新同步管理器配置
        this.syncManager.updateConfig({
            serverUrl: this.settings.serverUrl,
            syncKey: this.settings.syncKey
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
            .setName('Sync Key')
            .setDesc('从管理后台获取的同步密钥')
            .addText(text => text
                .setPlaceholder('xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx')
                .setValue(this.plugin.settings.syncKey)
                .onChange(async (value) => {
                    this.plugin.settings.syncKey = value;
                    await this.plugin.saveSettings();
                }));

        // 测试连接按钮
        new Setting(containerEl)
            .setName('测试连接')
            .setDesc('测试与服务器的连接（30秒超时）')
            .addButton(button => button
                .setButtonText('测试')
                .onClick(async () => {
                    button.setDisabled(true);
                    button.setButtonText('测试中...');

                    new Notice('正在测试连接...');

                    const result = await this.plugin.syncManager.testConnection(30000);

                    button.setDisabled(false);
                    button.setButtonText('测试');

                    if (result.success && result.vaultInfo) {
                        new Notice(
                            `✅ 连接成功！\n` +
                            `Vault: ${result.vaultInfo.name}\n` +
                            `Domain: ${result.vaultInfo.domain}`,
                            5000
                        );
                    } else {
                        new Notice(`❌ 连接失败: ${result.error || 'Unknown error'}`, 5000);
                    }
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
