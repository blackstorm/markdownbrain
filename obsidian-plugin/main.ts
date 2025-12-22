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

// 文档元数据
interface DocumentMetadata {
    links?: Array<{
        link: string;              // 链接目标路径
        targetClientId: string;    // 目标文档的 client_id
        original: string;          // 原始文本
        displayText?: string;      // 显示文本
        linkType: 'link' | 'embed'; // 链接类型
        isMedia?: boolean;         // 是否是媒体文件
        mediaType?: 'image' | 'video' | 'audio' | 'pdf' | 'other'; // 媒体类型
    }>;
    tags?: Array<{
        tag: string;
        position: {
            start: { line: number; col: number; offset: number };
            end: { line: number; col: number; offset: number };
        };
    }>;
    headings?: Array<{
        heading: string;
        level: number;
        position: {
            start: { line: number; col: number; offset: number };
            end: { line: number; col: number; offset: number };
        };
    }>;
    frontmatter?: Record<string, any>;
}

// 同步数据
interface SyncData {
    path: string;
    clientId: string;           // 客户端生成的唯一 ID
    clientType: string;         // 客户端类型：'obsidian'
    content?: string;
    hash?: string;
    mtime?: string;
    metadata?: DocumentMetadata;
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
        console.log('[MarkdownBrain] Starting sync:', {
            path: data.path,
            action: data.action,
            hasContent: !!data.content,
            contentLength: data.content?.length,
            serverUrl: this.config.serverUrl,
            hasSyncKey: !!this.config.syncKey
        });

        try {
            const requestBody = JSON.stringify(data);
            console.log('[MarkdownBrain] Request body size:', requestBody.length, 'bytes');

            const response = await requestUrl({
                url: `${this.config.serverUrl}/api/sync`,
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${this.config.syncKey}`
                },
                body: requestBody,
                throw: false
            });

            console.log('[MarkdownBrain] Response:', {
                status: response.status,
                statusText: response.status,
                headers: response.headers,
                bodyPreview: response.text?.substring(0, 200)
            });

            if (response.status === 200) {
                console.log('[MarkdownBrain] ✓ Sync successful:', data.path);
                return { success: true };
            } else {
                const errorMsg = `HTTP ${response.status}: ${response.text || 'Sync failed'}`;
                console.error('[MarkdownBrain] ✗ Sync failed:', {
                    path: data.path,
                    status: response.status,
                    error: errorMsg,
                    responseBody: response.text
                });
                return {
                    success: false,
                    error: errorMsg
                };
            }
        } catch (error) {
            console.error('[MarkdownBrain] ✗ Sync exception:', {
                path: data.path,
                error: error,
                errorMessage: error instanceof Error ? error.message : 'Unknown error',
                errorStack: error instanceof Error ? error.stack : undefined
            });
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
    private syncDebounceTimers: Map<string, NodeJS.Timeout>;
    private pendingSyncs: Set<string>;  // 跟踪待同步的文件

    constructor(app: App, manifest: PluginManifest) {
        super(app, manifest);
        this.syncDebounceTimers = new Map();
        this.pendingSyncs = new Set();
    }

    // 生成客户端 ID（基于路径的 SHA-256 hash）
    async generateClientId(path: string): Promise<string> {
        const encoder = new TextEncoder();
        const data = encoder.encode(path);
        const hashBuffer = await crypto.subtle.digest('SHA-256', data);
        const hashArray = Array.from(new Uint8Array(hashBuffer));
        const hashHex = hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
        // 格式化为 UUID 样式
        return `${hashHex.substring(0, 8)}-${hashHex.substring(8, 12)}-${hashHex.substring(12, 16)}-${hashHex.substring(16, 20)}-${hashHex.substring(20, 32)}`;
    }

    // 标准化 Obsidian 链接路径
    normalizeLinkPath(link: string): string {
        // 移除可能的锚点和块引用
        let path = link.split('#')[0].split('^')[0];

        // 不要为非 markdown 文件添加 .md 扩展名
        // 保留原始扩展名（如 .png, .jpg, .mp4 等）
        if (!path.includes('.')) {
            // 如果没有扩展名，假设是 markdown 文件
            path = path + '.md';
        }

        return path;
    }

    // 判断是否是媒体文件
    isMediaFile(path: string): boolean {
        const mediaExtensions = [
            // 图片
            '.png', '.jpg', '.jpeg', '.gif', '.bmp', '.svg', '.webp',
            // 视频
            '.mp4', '.webm', '.ogv', '.mov', '.mkv',
            // 音频
            '.mp3', '.wav', '.m4a', '.ogg', '.3gp', '.flac',
            // 其他
            '.pdf'
        ];

        const ext = path.substring(path.lastIndexOf('.')).toLowerCase();
        return mediaExtensions.includes(ext);
    }

    // 获取媒体文件类型
    getMediaType(path: string): 'image' | 'video' | 'audio' | 'pdf' | 'other' {
        const ext = path.substring(path.lastIndexOf('.')).toLowerCase();

        const imageExts = ['.png', '.jpg', '.jpeg', '.gif', '.bmp', '.svg', '.webp'];
        const videoExts = ['.mp4', '.webm', '.ogv', '.mov', '.mkv'];
        const audioExts = ['.mp3', '.wav', '.m4a', '.ogg', '.3gp', '.flac'];

        if (imageExts.includes(ext)) return 'image';
        if (videoExts.includes(ext)) return 'video';
        if (audioExts.includes(ext)) return 'audio';
        if (ext === '.pdf') return 'pdf';
        return 'other';
    }

    async onload() {
        console.log('[MarkdownBrain] ========================================');
        console.log('[MarkdownBrain] Plugin loading...');

        await this.loadSettings();

        console.log('[MarkdownBrain] Settings loaded:', {
            serverUrl: this.settings.serverUrl,
            hasSyncKey: !!this.settings.syncKey,
            syncKeyLength: this.settings.syncKey?.length,
            autoSync: this.settings.autoSync
        });

        // 初始化同步管理器
        this.syncManager = new SyncManager({
            serverUrl: this.settings.serverUrl,
            syncKey: this.settings.syncKey
        });
        console.log('[MarkdownBrain] SyncManager initialized');

        // 监听文件创建
        this.registerEvent(
            this.app.vault.on('create', (file) => {
                if (file instanceof TFile && file.extension === 'md') {
                    this.handleFileChange(file, 'create');
                }
            })
        );
        console.log('[MarkdownBrain] Registered event: file create');

        // 监听文件修改
        this.registerEvent(
            this.app.vault.on('modify', (file) => {
                if (file instanceof TFile && file.extension === 'md') {
                    // 标记为待同步
                    this.pendingSyncs.add(file.path);
                }
            })
        );
        console.log('[MarkdownBrain] Registered event: file modify');

        // 监听元数据缓存更新完成
        this.registerEvent(
            this.app.metadataCache.on('resolved', () => {
                // metadataCache 已解析完成，处理所有待同步的文件
                for (const filePath of this.pendingSyncs) {
                    const file = this.app.vault.getAbstractFileByPath(filePath);
                    if (file instanceof TFile) {
                        this.handleFileChange(file, 'modify');
                    }
                }
                this.pendingSyncs.clear();
            })
        );
        console.log('[MarkdownBrain] Registered event: metadata resolved');

        // 监听文件删除
        this.registerEvent(
            this.app.vault.on('delete', (file) => {
                if (file instanceof TFile && file.extension === 'md') {
                    this.handleFileDelete(file);
                }
            })
        );
        console.log('[MarkdownBrain] Registered event: file delete');

        // 添加设置面板
        this.addSettingTab(new MarkdownBrainSettingTab(this.app, this));
        console.log('[MarkdownBrain] Settings tab added');

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
        console.log('[MarkdownBrain] Commands registered');

        console.log('[MarkdownBrain] ✓ Plugin loaded successfully');
        console.log('[MarkdownBrain] ========================================');
    }

    async handleFileChange(file: TFile, action: 'create' | 'modify') {
        if (!this.settings.autoSync) {
            console.log('[MarkdownBrain] Auto-sync disabled, skipping:', file.path);
            return;
        }

        // 清除之前的防抖定时器
        const existingTimer = this.syncDebounceTimers.get(file.path);
        if (existingTimer) {
            clearTimeout(existingTimer);
        }

        // 使用防抖避免快速连续的同步
        const timer = setTimeout(async () => {
            this.syncDebounceTimers.delete(file.path);
            await this.performSync(file, action);
        }, 300); // 300ms 防抖

        this.syncDebounceTimers.set(file.path, timer);
    }

    async performSync(file: TFile, action: 'create' | 'modify') {

        console.log('[MarkdownBrain] File change detected:', {
            path: file.path,
            action: action,
            extension: file.extension,
            size: file.stat.size
        });

        try {
            // 读取文件内容
            const content = await this.app.vault.read(file);
            console.log('[MarkdownBrain] File read successfully:', {
                path: file.path,
                contentLength: content.length
            });

            // 跳过空内容的文件
            if (!content || content.trim().length === 0) {
                console.log('[MarkdownBrain] Skipping empty file:', file.path);
                return;
            }

            // 跳过"未命名.md"文件（如果内容为空）
            if (file.path.includes('未命名') && content.trim().length === 0) {
                console.log('[MarkdownBrain] Skipping untitled empty file:', file.path);
                return;
            }

            // 获取文件元数据 - 使用 Obsidian API
            const cachedMetadata = this.app.metadataCache.getFileCache(file);
            const metadata: DocumentMetadata = {};

            console.log('[MarkdownBrain] Raw cached metadata:', cachedMetadata);

            if (cachedMetadata) {
                // 提取链接和嵌入，合并为统一的 links 数组
                const allLinks: Array<{
                    link: string;
                    targetClientId: string;
                    original: string;
                    displayText?: string;
                    linkType: 'link' | 'embed';
                }> = [];

                // 处理普通链接
                if (cachedMetadata.links) {
                    console.log('[MarkdownBrain] Found', cachedMetadata.links.length, 'links in metadata');
                    for (const link of cachedMetadata.links) {
                        console.log('[MarkdownBrain] Processing link:', link);
                        const normalizedPath = this.normalizeLinkPath(link.link);
                        console.log('[MarkdownBrain] Normalized path:', normalizedPath);
                        const targetClientId = await this.generateClientId(normalizedPath);
                        console.log('[MarkdownBrain] Generated targetClientId:', targetClientId);

                        const isMedia = this.isMediaFile(normalizedPath);
                        const linkData: any = {
                            link: normalizedPath,
                            targetClientId: targetClientId,
                            original: link.original,
                            displayText: link.displayText,
                            linkType: 'link'
                        };

                        if (isMedia) {
                            linkData.isMedia = true;
                            linkData.mediaType = this.getMediaType(normalizedPath);
                            console.log('[MarkdownBrain] Media file detected:', linkData.mediaType);
                        }

                        allLinks.push(linkData);
                    }
                }

                // 处理嵌入
                if (cachedMetadata.embeds) {
                    console.log('[MarkdownBrain] Found', cachedMetadata.embeds.length, 'embeds in metadata');
                    for (const embed of cachedMetadata.embeds) {
                        console.log('[MarkdownBrain] Processing embed:', embed);
                        const normalizedPath = this.normalizeLinkPath(embed.link);
                        console.log('[MarkdownBrain] Normalized path:', normalizedPath);
                        const targetClientId = await this.generateClientId(normalizedPath);
                        console.log('[MarkdownBrain] Generated targetClientId:', targetClientId);

                        const isMedia = this.isMediaFile(normalizedPath);
                        const linkData: any = {
                            link: normalizedPath,
                            targetClientId: targetClientId,
                            original: embed.original,
                            linkType: 'embed'
                        };

                        if (isMedia) {
                            linkData.isMedia = true;
                            linkData.mediaType = this.getMediaType(normalizedPath);
                            console.log('[MarkdownBrain] Media file detected:', linkData.mediaType);
                        }

                        allLinks.push(linkData);
                    }
                }

                if (allLinks.length > 0) {
                    metadata.links = allLinks;
                    console.log('[MarkdownBrain] Total links to sync:', allLinks.length);
                    console.log('[MarkdownBrain] Links data:', JSON.stringify(allLinks, null, 2));
                } else {
                    console.log('[MarkdownBrain] No links found in this document');
                }

                // 提取标签
                if (cachedMetadata.tags) {
                    metadata.tags = cachedMetadata.tags.map(tag => ({
                        tag: tag.tag,
                        position: tag.position
                    }));
                }

                // 提取标题
                if (cachedMetadata.headings) {
                    metadata.headings = cachedMetadata.headings.map(heading => ({
                        heading: heading.heading,
                        level: heading.level,
                        position: heading.position
                    }));
                }

                // 提取 frontmatter
                if (cachedMetadata.frontmatter) {
                    metadata.frontmatter = cachedMetadata.frontmatter;
                }

                console.log('[MarkdownBrain] Metadata extracted:', {
                    path: file.path,
                    linksCount: metadata.links?.length || 0,
                    tagsCount: metadata.tags?.length || 0,
                    headingsCount: metadata.headings?.length || 0,
                    hasFrontmatter: !!metadata.frontmatter
                });
            }

            // 获取文件统计信息
            const stat = file.stat;
            const hash = this.hashString(content);
            const mtime = new Date(stat.mtime).toISOString();

            // 生成客户端 ID
            const clientId = await this.generateClientId(file.path);

            console.log('[MarkdownBrain] File metadata:', {
                path: file.path,
                clientId: clientId,
                hash: hash,
                mtime: mtime,
                hasMetadata: !!metadata,
                linksCount: metadata.links?.length || 0
            });

            // 准备同步数据
            const syncData = {
                path: file.path,
                clientId: clientId,
                clientType: 'obsidian',
                content: content,
                hash: hash,
                mtime: mtime,
                metadata: metadata,
                action: action
            };

            console.log('[MarkdownBrain] Sync data prepared:');
            console.log('[MarkdownBrain] - Path:', syncData.path);
            console.log('[MarkdownBrain] - ClientId:', syncData.clientId);
            console.log('[MarkdownBrain] - Action:', syncData.action);
            console.log('[MarkdownBrain] - Metadata:', JSON.stringify(syncData.metadata, null, 2));

            // 调用同步函数
            const result = await this.syncManager.sync(syncData);

            if (result.success) {
                console.log('[MarkdownBrain] ✓ File synced successfully:', file.path);
            } else {
                console.error('[MarkdownBrain] ✗ File sync failed:', file.path, result.error);
                new Notice(`同步失败: ${file.path}\n${result.error}`);
            }
        } catch (error) {
            console.error('[MarkdownBrain] ✗ File change handler error:', {
                path: file.path,
                error: error,
                errorMessage: error instanceof Error ? error.message : 'Unknown error',
                errorStack: error instanceof Error ? error.stack : undefined
            });
            new Notice(`同步错误: ${error instanceof Error ? error.message : 'Unknown error'}`);
        }
    }

    async handleFileDelete(file: TFile) {
        if (!this.settings.autoSync) {
            console.log('[MarkdownBrain] Auto-sync disabled, skipping delete:', file.path);
            return;
        }

        console.log('[MarkdownBrain] File deletion detected:', {
            path: file.path,
            extension: file.extension
        });

        try {
            // 生成客户端 ID
            const clientId = await this.generateClientId(file.path);

            const result = await this.syncManager.sync({
                path: file.path,
                clientId: clientId,
                clientType: 'obsidian',
                action: 'delete'
            });

            if (result.success) {
                console.log('[MarkdownBrain] ✓ File deletion synced:', file.path);
            } else {
                console.error('[MarkdownBrain] ✗ File deletion sync failed:', file.path, result.error);
            }
        } catch (error) {
            console.error('[MarkdownBrain] ✗ Delete sync exception:', {
                path: file.path,
                error: error,
                errorMessage: error instanceof Error ? error.message : 'Unknown error'
            });
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
        // 清理所有防抖定时器
        for (const timer of this.syncDebounceTimers.values()) {
            clearTimeout(timer);
        }
        this.syncDebounceTimers.clear();
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
