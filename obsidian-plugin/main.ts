import { Plugin, TFile, Notice, PluginSettingTab, App, Setting, requestUrl, PluginManifest } from 'obsidian';

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

// Full Sync 请求
interface FullSyncRequest {
    clientIds: string[];
}

// Full Sync 响应
interface FullSyncResponse {
    success: boolean;
    data?: {
        'vault-id': string;
        action: string;
        'client-docs': number;
        'deleted-count': number;
        'remaining-docs': number;
    };
    error?: string;
}

// 同步统计
interface SyncStats {
    success: number;
    failed: number;
    skipped: number;
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
                url: `${this.config.serverUrl}/obsidian/vault/info`,
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
                url: `${this.config.serverUrl}/obsidian/sync`,
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

    /**
     * 全量同步 - 清理孤儿文档
     * @param clientIds 客户端所有文档的 client_ids 列表
     * @returns 包含删除数量的响应
     * @complexity O(1) - 单次网络请求
     * @compatibility 降级处理 - 如果服务器返回 404，视为成功（旧服务器）
     */
    async fullSync(clientIds: string[]): Promise<FullSyncResponse> {
        console.log('[MarkdownBrain] Starting full sync...', {
            clientDocCount: clientIds.length
        });

        try {
            const requestBody: FullSyncRequest = { clientIds };
            const response = await requestUrl({
                url: `${this.config.serverUrl}/obsidian/sync/full`,
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${this.config.syncKey}`
                },
                body: JSON.stringify(requestBody),
                throw: false
            });

            // 向后兼容：旧服务器不支持 full-sync (404)
            if (response.status === 404) {
                console.warn('[MarkdownBrain] Full sync not supported by server (404), skipping orphan cleanup');
                return {
                    success: true,
                    data: {
                        'vault-id': '',
                        action: 'full-sync',
                        'client-docs': clientIds.length,
                        'deleted-count': 0,
                        'remaining-docs': clientIds.length
                    }
                };
            }

            if (response.status === 200) {
                const data = response.json;
                console.log('[MarkdownBrain] ✓ Full sync successful:', {
                    deletedCount: data['deleted-count'],
                    remainingDocs: data['remaining-docs']
                });
                return {
                    success: true,
                    data: data
                };
            } else {
                const errorMsg = `HTTP ${response.status}: ${response.text || 'Full sync failed'}`;
                console.error('[MarkdownBrain] ✗ Full sync failed:', {
                    status: response.status,
                    error: errorMsg
                });
                return {
                    success: false,
                    error: errorMsg
                };
            }
        } catch (error) {
            const errorMsg = error instanceof Error ? error.message : 'Unknown error';
            console.error('[MarkdownBrain] ✗ Full sync exception:', {
                error: errorMsg
            });
            return {
                success: false,
                error: errorMsg
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

        // 监听文件重命名
        this.registerEvent(
            this.app.vault.on('rename', (file, oldPath) => {
                if (file instanceof TFile && file.extension === 'md') {
                    this.handleFileRename(file, oldPath);
                }
            })
        );
        console.log('[MarkdownBrain] Registered event: file rename');

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
            const hash = await this.hashString(content);
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

    async handleFileRename(file: TFile, oldPath: string) {
        if (!this.settings.autoSync) {
            console.log('[MarkdownBrain] Auto-sync disabled, skipping rename:', oldPath, '->', file.path);
            return;
        }

        console.log('[MarkdownBrain] File rename detected:', {
            oldPath: oldPath,
            newPath: file.path,
            extension: file.extension
        });

        try {
            // 1. 删除旧路径的文档
            const oldClientId = await this.generateClientId(oldPath);
            console.log('[MarkdownBrain] Deleting old path:', {
                path: oldPath,
                clientId: oldClientId
            });

            const deleteResult = await this.syncManager.sync({
                path: oldPath,
                clientId: oldClientId,
                clientType: 'obsidian',
                action: 'delete'
            });

            if (!deleteResult.success) {
                console.error('[MarkdownBrain] ✗ Failed to delete old path:', oldPath, deleteResult.error);
                new Notice(`重命名同步失败（删除旧文件）: ${oldPath}\n${deleteResult.error}`);
                return;
            }

            console.log('[MarkdownBrain] ✓ Old path deleted:', oldPath);

            // 2. 创建新路径的文档（使用 performSync，它会读取内容和元数据）
            console.log('[MarkdownBrain] Creating new path:', file.path);
            await this.performSync(file, 'create');

            console.log('[MarkdownBrain] ✓ File rename synced:', oldPath, '->', file.path);
        } catch (error) {
            console.error('[MarkdownBrain] ✗ Rename sync exception:', {
                oldPath: oldPath,
                newPath: file.path,
                error: error,
                errorMessage: error instanceof Error ? error.message : 'Unknown error'
            });
            new Notice(`重命名同步错误: ${error instanceof Error ? error.message : 'Unknown error'}`);
        }
    }

    /**
     * 全量同步所有文件
     * @flow 收集 IDs → 清理孤儿 → 增量同步
     * @complexity O(n) where n = 文件数量
     */
    async syncAllFiles() {
        const files = this.app.vault.getMarkdownFiles();
        console.log(`[MarkdownBrain] Starting full sync for ${files.length} files`);
        new Notice(`开始全量同步 ${files.length} 个文件...`);

        // 步骤 1: 收集 client_ids
        const clientIds = await this.collectClientIds(files);
        console.log(`[MarkdownBrain] Collected ${clientIds.length} client IDs`);

        // 步骤 2: 清理孤儿文档 (O(1) 单次请求)
        await this.cleanupOrphans(clientIds);

        // 步骤 3: 增量同步
        await this.incrementalSyncBatch(files);
    }

    /**
     * 收集所有文件的 client_ids
     * @complexity O(n) time, O(n) space
     */
    private async collectClientIds(files: TFile[]): Promise<string[]> {
        const ids: string[] = [];
        for (const file of files) {
            try {
                const content = await this.app.vault.read(file);
                // 只包含非空文件
                if (content?.trim().length > 0) {
                    ids.push(await this.generateClientId(file.path));
                }
            } catch (error) {
                console.error(`[MarkdownBrain] Failed to read ${file.path}:`, error);
            }
        }
        return ids;
    }

    /**
     * 清理孤儿文档
     * @complexity O(1) - 单次网络请求
     */
    private async cleanupOrphans(clientIds: string[]): Promise<void> {
        try {
            const result = await this.syncManager.fullSync(clientIds);
            const deleted = result.data?.['deleted-count'] || 0;

            if (deleted > 0) {
                console.log(`[MarkdownBrain] ✓ Cleaned ${deleted} orphan documents`);
                new Notice(`清理了 ${deleted} 个孤儿文档`);
            } else if (result.success) {
                console.log('[MarkdownBrain] ✓ No orphan documents found');
            }
        } catch (error) {
            // Full-sync 失败不中断流程
            console.error('[MarkdownBrain] Full sync error:', error);
        }
    }

    /**
     * 批量增量同步
     * @complexity O(n) with batching
     */
    private async incrementalSyncBatch(files: TFile[]): Promise<void> {
        const stats: SyncStats = { success: 0, failed: 0, skipped: 0 };
        const batchSize = 10;

        for (let i = 0; i < files.length; i += batchSize) {
            const batch = files.slice(i, i + batchSize);
            await Promise.all(batch.map(file => this.syncOneFile(file, stats)));

            const progress = Math.min(i + batchSize, files.length);
            new Notice(`同步进度: ${progress}/${files.length} (成功: ${stats.success}, 失败: ${stats.failed}, 跳过: ${stats.skipped})`);
        }

        const finalMsg = `同步完成! 总计: ${files.length}, 成功: ${stats.success}, 失败: ${stats.failed}, 跳过: ${stats.skipped}`;
        console.log(`[MarkdownBrain] ${finalMsg}`);
        new Notice(finalMsg, 8000);
    }

    /**
     * 同步单个文件
     */
    private async syncOneFile(file: TFile, stats: SyncStats): Promise<void> {
        try {
            const content = await this.app.vault.read(file);

            await this.performSync(file, 'modify');
            stats.success++;
        } catch (error) {
            stats.failed++;
            console.error(`[MarkdownBrain] ✗ Failed to sync ${file.path}:`, error);
        }
    }

    // MD5 哈希函数（用于检测文件内容变化）
    async hashString(str: string): Promise<string> {
        const encoder = new TextEncoder();
        const data = encoder.encode(str);
        const hashBuffer = await crypto.subtle.digest('MD5', data).catch(() => {
            // 如果 MD5 不可用，降级使用 SHA-256
            return crypto.subtle.digest('SHA-256', data);
        });
        const hashArray = Array.from(new Uint8Array(hashBuffer));
        return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
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

        // 批量同步按钮
        new Setting(containerEl)
            .setName('批量同步')
            .setDesc('手动同步所有 Markdown 文件到服务器')
            .addButton(button => button
                .setButtonText('开始同步')
                .onClick(async () => {
                    button.setDisabled(true);
                    button.setButtonText('同步中...');

                    await this.plugin.syncAllFiles();

                    button.setDisabled(false);
                    button.setButtonText('开始同步');
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
