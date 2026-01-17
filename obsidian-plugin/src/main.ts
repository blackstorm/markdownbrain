/**
 * MarkdownBrain Obsidian Plugin - Snapshot Sync
 */

import { App, Notice, Plugin, PluginManifest, TFile } from 'obsidian';
import { CLIENT_ID_KEY, getOrCreateClientId } from './core/client-id';
import { MarkdownBrainSettings, DEFAULT_SETTINGS } from './domain/types';
import { hashString, md5Hash, isAssetFile } from './utils';
import { extractAssetPaths } from './utils/asset-links';
import { ObsidianHttpClient } from './api';
import { SyncApiClient, type SyncSnapshotEntry } from './api/sync-api';
import { DebounceService, ClientIdCache, extractNoteMetadata } from './services';
import { MarkdownBrainSettingTab, registerFileEvents } from './plugin';

export default class MarkdownBrainPlugin extends Plugin {
    settings!: MarkdownBrainSettings;
    syncClient!: SyncApiClient;
    private debounceService: DebounceService;
    private clientIdCache: ClientIdCache;

    constructor(app: App, manifest: PluginManifest) {
        super(app, manifest);
        this.debounceService = new DebounceService();
        this.clientIdCache = new ClientIdCache();
    }

    async onload() {
        console.log('[MarkdownBrain] Plugin loading (snapshot sync)...');
        await this.loadSettings();

        const httpClient = new ObsidianHttpClient();
        this.syncClient = new SyncApiClient({
            serverUrl: this.settings.serverUrl,
            syncKey: this.settings.syncKey
        }, httpClient);

        registerFileEvents(
            this.app,
            {
                onFileChange: (file, action) => this.handleFileChange(file, action),
                onFileDelete: (file) => this.handleFileDelete(file),
                onFileRename: (file, oldPath) => this.handleFileRename(file, oldPath),
                onAssetChange: (file) => this.handleAssetChange(file),
                onAssetDelete: () => this.fullSync(),
                onAssetRename: () => this.fullSync(),
                onMetadataResolved: () => {},
            },
            { add: () => {}, clear: () => {}, forEach: () => {} },
            (event) => this.registerEvent(event)
        );

        this.addSettingTab(new MarkdownBrainSettingTab(this.app, this));

        this.addCommand({
            id: 'sync-current-file',
            name: 'Sync current file',
            callback: () => {
                const file = this.app.workspace.getActiveFile();
                if (file && file.extension === 'md') {
                    this.handleFileChange(file, 'modify');
                }
            }
        });

        this.addCommand({
            id: 'sync-all-files',
            name: 'Sync all files (full sync)',
            callback: () => this.fullSync()
        });

        await this.buildClientIdCache();
        console.log('[MarkdownBrain] ✓ Plugin loaded');
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
        console.log(`[MarkdownBrain] ClientId cache: ${cached}/${files.length}`);
    }

    // =========================================================================
    // File Event Handlers
    // =========================================================================

    async handleFileChange(file: TFile, action: 'create' | 'modify') {
        if (!this.settings.autoSync) return;

        this.debounceService.debounce(file.path, async () => {
            const result = await this.syncNoteFile(file);
            if (!result) {
                new Notice('同步失败: 笔记上传失败');
            }
        }, 500);
    }

    async handleFileDelete(file: TFile) {
        if (!this.settings.autoSync) return;
        await this.fullSync();
    }

    async handleFileRename(file: TFile, oldPath: string) {
        if (!this.settings.autoSync) return;
        this.clientIdCache.rename(oldPath, file.path);
        const result = await this.syncNoteFile(file);
        if (!result) {
            new Notice('同步失败: 笔记重命名同步失败');
        }
    }

    async handleAssetChange(file: TFile) {
        if (!this.settings.autoSync) return;
        const result = await this.syncAssetFile(file);
        if (!result) {
            new Notice('同步失败: 资源上传失败');
        }
    }

    // =========================================================================
    // Sync Operations
    // =========================================================================

    private async syncNoteFile(file: TFile): Promise<boolean> {
        const content = await this.app.vault.read(file);
        const hash = await hashString(content);
        const clientId = await getOrCreateClientId(file, content, this.app);
        const metadata = extractNoteMetadata(this.app.metadataCache.getFileCache(file));
        const assetIds = await this.extractAssetIds(content, file.path);

        this.clientIdCache.set(file.path, clientId);

        const result = await this.syncClient.syncNote(clientId, {
            path: file.path,
            content,
            hash,
            metadata: metadata as Record<string, unknown>,
            assetIds
        });

        return result.success;
    }

    private async syncAssetFile(file: TFile): Promise<boolean> {
        const buffer = await this.app.vault.readBinary(file);
        const assetId = await hashString(file.path);
        const hash = await md5Hash(buffer);
        const base64 = Buffer.from(new Uint8Array(buffer)).toString('base64');

        const result = await this.syncClient.syncAsset(assetId, {
            path: file.path,
            contentType: file.mime || 'application/octet-stream',
            size: buffer.byteLength,
            hash,
            content: base64
        });

        return result.success;
    }

    async fullSync(): Promise<void> {
        const notes = await this.buildNoteSnapshot();
        const assets = await this.buildAssetSnapshot();

        new Notice(`开始全量同步：${notes.length} 笔记 / ${assets.length} 资源`);

        const changes = await this.syncClient.syncChanges({ notes, assets });
        if (!changes.success) {
            new Notice(`全量同步失败: ${changes.error}`);
            return;
        }

        const needNotes = changes.need_upsert?.notes ?? [];
        const needAssets = changes.need_upsert?.assets ?? [];

        if (needAssets.length > 0) {
            await this.uploadAssets(needAssets);
        }

        if (needNotes.length > 0) {
            await this.uploadNotes(needNotes);
        }

        new Notice('全量同步完成');
    }

    private async uploadNotes(entries: SyncSnapshotEntry[]): Promise<void> {
        const fileMap = new Map<string, TFile>();
        for (const file of this.app.vault.getMarkdownFiles()) {
            const content = await this.app.vault.read(file);
            const clientId = await getOrCreateClientId(file, content, this.app);
            fileMap.set(clientId, file);
        }

        for (const entry of entries) {
            const file = fileMap.get(entry.id);
            if (!file) continue;
            await this.syncNoteFile(file);
        }
    }

    private async uploadAssets(entries: SyncSnapshotEntry[]): Promise<void> {
        const assetFiles = this.app.vault.getFiles().filter(isAssetFile);
        const assetMap = new Map<string, TFile>();
        for (const file of assetFiles) {
            const assetId = await hashString(file.path);
            assetMap.set(assetId, file);
        }

        const tasks = entries.map(entry => async () => {
            const file = assetMap.get(entry.id);
            if (!file) return;
            await this.syncAssetFile(file);
        });

        await this.runWithConcurrency(tasks, 3);
    }

    private async runWithConcurrency(tasks: Array<() => Promise<void>>, limit: number): Promise<void> {
        const queue = [...tasks];
        const workers = Array.from({ length: limit }, async () => {
            while (queue.length > 0) {
                const task = queue.shift();
                if (task) {
                    await task();
                }
            }
        });
        await Promise.all(workers);
    }

    private async buildNoteSnapshot(): Promise<SyncSnapshotEntry[]> {
        const files = this.app.vault.getMarkdownFiles();
        const snapshot: SyncSnapshotEntry[] = [];

        for (const file of files) {
            const content = await this.app.vault.read(file);
            if (!content.trim()) continue;

            const clientId = await getOrCreateClientId(file, content, this.app);
            const hash = await hashString(content);

            this.clientIdCache.set(file.path, clientId);
            snapshot.push({ id: clientId, hash });
        }

        return snapshot;
    }

    private async buildAssetSnapshot(): Promise<SyncSnapshotEntry[]> {
        const assets = this.app.vault.getFiles().filter(isAssetFile);
        const snapshot: SyncSnapshotEntry[] = [];

        for (const file of assets) {
            const buffer = await this.app.vault.readBinary(file);
            const assetId = await hashString(file.path);
            const hash = await md5Hash(buffer);
            snapshot.push({ id: assetId, hash });
        }

        return snapshot;
    }

    private async extractAssetIds(content: string, sourcePath: string): Promise<string[]> {
        const paths = extractAssetPaths(content);
        const resolvedPaths = paths.map(path => {
            const resolved = this.app.metadataCache.getFirstLinkpathDest(path, sourcePath);
            return resolved?.path ?? path;
        });
        const ids = await Promise.all(resolvedPaths.map(path => hashString(path)));
        return Array.from(new Set(ids));
    }

    // =========================================================================
    // Settings Persistence
    // =========================================================================

    async loadSettings() {
        const data = await this.loadData();
        this.settings = Object.assign({}, DEFAULT_SETTINGS, data);
    }

    async saveSettings() {
        await this.saveData({
            ...this.settings
        });
        this.syncClient.updateConfig({
            serverUrl: this.settings.serverUrl,
            syncKey: this.settings.syncKey
        });
    }

    onunload() {
        console.log('[MarkdownBrain] Plugin unloading');
        this.debounceService.clearAll();
    }
}
