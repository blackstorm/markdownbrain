import { App, Notice, Plugin, PluginManifest, TFile } from 'obsidian';
import { CLIENT_ID_KEY, getOrCreateClientId } from './core/client-id';
import {
    MarkdownBrainSettings,
    DEFAULT_SETTINGS,
    SyncStats,
    ResourceSyncData,
} from './domain/types';
import { isResourceFile, getContentType, arrayBufferToBase64, sha256Hash, hashString } from './utils';
import { SyncApiClient, ObsidianHttpClient } from './api';
import { DebounceService, ClientIdCache, extractNoteMetadata } from './services';
import { MarkdownBrainSettingTab, registerFileEvents } from './plugin';

export default class MarkdownBrainPlugin extends Plugin {
    settings!: MarkdownBrainSettings;
    syncManager!: SyncApiClient;
    private debounceService: DebounceService;
    private pendingSyncs: Set<string>;
    private clientIdCache: ClientIdCache;

    constructor(app: App, manifest: PluginManifest) {
        super(app, manifest);
        this.debounceService = new DebounceService();
        this.pendingSyncs = new Set();
        this.clientIdCache = new ClientIdCache();
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
        const httpClient = new ObsidianHttpClient();
        this.syncManager = new SyncApiClient({
            serverUrl: this.settings.serverUrl,
            syncKey: this.settings.syncKey
        }, httpClient);
        console.log('[MarkdownBrain] SyncManager initialized');

        registerFileEvents(
            this.app,
            {
                onFileChange: (file, action) => this.handleFileChange(file, action),
                onFileDelete: (file) => this.handleFileDelete(file),
                onFileRename: (file, oldPath) => this.handleFileRename(file, oldPath),
                onResourceChange: (file, action) => this.handleResourceChange(file, action),
                onResourceDelete: (file) => this.handleResourceDelete(file),
                onResourceRename: (file, oldPath) => this.handleResourceRename(file, oldPath),
                onMetadataResolved: () => {},
            },
            {
                add: (path) => this.pendingSyncs.add(path),
                clear: () => this.pendingSyncs.clear(),
                forEach: (cb) => this.pendingSyncs.forEach(cb),
            },
            (event) => this.registerEvent(event)
        );
        console.log('[MarkdownBrain] File events registered');

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

        await this.buildClientIdCache();

        console.log('[MarkdownBrain] ✓ Plugin loaded successfully');
        console.log('[MarkdownBrain] ========================================');
    }

    private async buildClientIdCache(): Promise<void> {
        const files = this.app.vault.getMarkdownFiles();
        let cached = 0;
        
        for (const file of files) {
            const cache = this.app.metadataCache.getFileCache(file);
            const clientId = cache?.frontmatter?.[CLIENT_ID_KEY];
            if (clientId) {
                this.clientIdCache.set(file.path, String(clientId).trim());
                cached++;
            }
        }
        
        console.log(`[MarkdownBrain] ClientId cache built: ${cached}/${files.length} files`);
    }

    async handleFileChange(file: TFile, action: 'create' | 'modify') {
        if (!this.settings.autoSync) {
            console.log('[MarkdownBrain] Auto-sync disabled, skipping:', file.path);
            return;
        }

        // 使用防抖避免快速连续的同步
        this.debounceService.debounce(file.path, async () => {
            await this.performSync(file, action);
        }, 300);
    }

    async performSync(file: TFile, action: 'create' | 'modify') {

        console.log('[MarkdownBrain] File change detected:', {
            path: file.path,
            action: action,
            extension: file.extension,
            size: file.stat.size
        });

        try {
            const content = await this.app.vault.read(file);
            console.log('[MarkdownBrain] File read successfully:', {
                path: file.path,
                contentLength: content.length
            });

            const cachedMetadata = this.app.metadataCache.getFileCache(file);
            const metadata = extractNoteMetadata(cachedMetadata);

            console.log('[MarkdownBrain] Metadata extracted:', {
                path: file.path,
                tagsCount: metadata.tags?.length || 0,
                headingsCount: metadata.headings?.length || 0,
                hasFrontmatter: !!metadata.frontmatter
            });

            const stat = file.stat;
            const hash = await hashString(content);
            const mtime = new Date(stat.mtime).toISOString();
            const clientId = await getOrCreateClientId(file, content, this.app);

            console.log('[MarkdownBrain] File metadata:', {
                path: file.path,
                clientId: clientId,
                hash: hash,
                mtime: mtime
            });

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

            const result = await this.syncManager.syncNote(syncData);

            if (result.success) {
                this.clientIdCache.set(file.path, clientId);
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

        const clientId = this.clientIdCache.get(file.path);

        if (!clientId) {
            console.warn('[MarkdownBrain] Cannot sync deletion - clientId not in cache:', file.path);
            return;
        }

        const result = await this.syncManager.syncNote({
            path: file.path,
            clientId: clientId,
            clientType: 'obsidian',
            action: 'delete'
        });

        if (result.success) {
            this.clientIdCache.delete(file.path);
            console.log('[MarkdownBrain] ✓ File deletion synced:', file.path);
        } else {
            console.error('[MarkdownBrain] ✗ File deletion sync failed:', file.path, result.error);
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

        this.clientIdCache.rename(oldPath, file.path);

        try {
            console.log('[MarkdownBrain] Syncing rename with frontmatter UUID');
            await this.performSync(file, 'modify');

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

    async handleResourceChange(file: TFile, action: 'upsert') {
        if (!this.settings.autoSync) {
            console.log('[MarkdownBrain] Auto-sync disabled, skipping resource:', file.path);
            return;
        }

        this.debounceService.debounce(file.path, async () => {
            await this.performResourceSync(file, action);
        }, 500);
    }

    async performResourceSync(file: TFile, action: 'upsert') {
        console.log('[MarkdownBrain] Resource change detected:', {
            path: file.path,
            action: action,
            extension: file.extension,
            size: file.stat.size
        });

        try {
            const buffer = await this.app.vault.readBinary(file);
            const content = await arrayBufferToBase64(buffer);
            const sha256 = await sha256Hash(buffer);

            const syncData: ResourceSyncData = {
                path: file.path,
                content: content,
                contentType: getContentType(file.extension),
                sha256: sha256,
                sizeBytes: buffer.byteLength,
                action: action
            };

            const result = await this.syncManager.syncResource(syncData);

            if (result.success) {
                console.log('[MarkdownBrain] ✓ Resource synced successfully:', file.path);
            } else {
                console.error('[MarkdownBrain] ✗ Resource sync failed:', file.path, result.error);
                new Notice(`资源同步失败: ${file.path}\n${result.error}`);
            }
        } catch (error) {
            console.error('[MarkdownBrain] ✗ Resource sync exception:', {
                path: file.path,
                error: error instanceof Error ? error.message : 'Unknown error'
            });
            new Notice(`资源同步错误: ${error instanceof Error ? error.message : 'Unknown error'}`);
        }
    }

    async handleResourceDelete(file: TFile) {
        if (!this.settings.autoSync) {
            console.log('[MarkdownBrain] Auto-sync disabled, skipping resource delete:', file.path);
            return;
        }

        console.log('[MarkdownBrain] Resource deletion detected:', file.path);

        const result = await this.syncManager.syncResource({
            path: file.path,
            contentType: getContentType(file.extension),
            action: 'delete'
        });

        if (result.success) {
            console.log('[MarkdownBrain] ✓ Resource deletion synced:', file.path);
        } else {
            console.error('[MarkdownBrain] ✗ Resource deletion sync failed:', file.path, result.error);
        }
    }

    async handleResourceRename(file: TFile, oldPath: string) {
        if (!this.settings.autoSync) {
            console.log('[MarkdownBrain] Auto-sync disabled, skipping resource rename:', oldPath, '->', file.path);
            return;
        }

        console.log('[MarkdownBrain] Resource rename detected:', oldPath, '->', file.path);

        await this.syncManager.syncResource({
            path: oldPath,
            contentType: getContentType(file.extension),
            action: 'delete'
        });

        await this.performResourceSync(file, 'upsert');
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
                    ids.push(await getOrCreateClientId(file, content, this.app));
                }
            } catch (error) {
                console.error(`[MarkdownBrain] Failed to read ${file.path}:`, error);
            }
        }
        return ids;
    }

    /**
     * 清理孤儿笔记
     * @complexity O(1) - 单次网络请求
     */
    private async cleanupOrphans(clientIds: string[]): Promise<void> {
        try {
            const result = await this.syncManager.fullSync(clientIds);
            const deleted = result.data?.['deleted-count'] || 0;

            if (deleted > 0) {
                console.log(`[MarkdownBrain] ✓ Cleaned ${deleted} orphan notes`);
                new Notice(`清理了 ${deleted} 个孤儿笔记`);
            } else if (result.success) {
                console.log('[MarkdownBrain] ✓ No orphan notes found');
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
            // const content = await this.app.vault.read(file);

            await this.performSync(file, 'modify');
            stats.success++;
        } catch (error) {
            stats.failed++;
            console.error(`[MarkdownBrain] ✗ Failed to sync ${file.path}:`, error);
        }
    }

    onunload() {
        console.log('Unloading MarkdownBrain plugin');
        // 清理所有防抖定时器
        this.debounceService.clearAll();
        this.syncManager.destroy();
    }

    async loadSettings() {
        this.settings = Object.assign({}, DEFAULT_SETTINGS, await this.loadData());
    }

    async saveSettings() {
        await this.saveData(this.settings);
        this.syncManager.updateConfig({
            serverUrl: this.settings.serverUrl,
            syncKey: this.settings.syncKey
        });
    }
}
