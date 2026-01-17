/**
 * MarkdownBrain Obsidian Plugin - V2
 * 
 * Simplified sync using the new plan/commit protocol.
 * Key principles:
 * - Client is the source of truth
 * - Explicit deletes only (no delete-by-absence)
 * - Atomic plan→commit with syncToken
 * - Idempotent retry
 */

import { App, Notice, Plugin, PluginManifest, TFile } from 'obsidian';
import { CLIENT_ID_KEY, getOrCreateClientId } from './core/client-id';
import { MarkdownBrainSettings, DEFAULT_SETTINGS, SyncStats } from './domain/types';
import { hashString } from './utils';
import { ObsidianHttpClient } from './api';
import { SyncV2Client } from './api/sync-v2-client';
import type { SyncOp, ManifestEntry, FileUpload } from './api/sync-v2-types';
import { DebounceService, ClientIdCache, extractNoteMetadata } from './services';
import { MarkdownBrainSettingTab, registerFileEvents } from './plugin';

interface LocalSyncState {
    lastSyncedRev: number;
    pendingOps: SyncOp[];
    nextRev: number;
}

export default class MarkdownBrainPlugin extends Plugin {
    settings!: MarkdownBrainSettings;
    syncClient!: SyncV2Client;
    private debounceService: DebounceService;
    private clientIdCache: ClientIdCache;
    private syncState: LocalSyncState;

    constructor(app: App, manifest: PluginManifest) {
        super(app, manifest);
        this.debounceService = new DebounceService();
        this.clientIdCache = new ClientIdCache();
        this.syncState = { lastSyncedRev: 0, pendingOps: [], nextRev: 1 };
    }

    async onload() {
        console.log('[MarkdownBrain] Plugin loading (V2 protocol)...');
        await this.loadSettings();
        await this.loadSyncState();

        const httpClient = new ObsidianHttpClient();
        this.syncClient = new SyncV2Client({
            serverUrl: this.settings.serverUrl,
            syncKey: this.settings.syncKey
        }, httpClient);

        registerFileEvents(
            this.app,
            {
                onFileChange: (file, action) => this.handleFileChange(file, action),
                onFileDelete: (file) => this.handleFileDelete(file),
                onFileRename: (file, oldPath) => this.handleFileRename(file, oldPath),
                onAssetChange: () => {},
                onAssetDelete: () => {},
                onAssetRename: () => {},
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

        this.addCommand({
            id: 'flush-pending',
            name: 'Flush pending changes',
            callback: () => this.flushPendingOps()
        });

        await this.buildClientIdCache();
        console.log('[MarkdownBrain] ✓ Plugin loaded (V2)');
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
            await this.addUpsertOp(file);
            await this.flushPendingOps();
        }, 500);
    }

    async handleFileDelete(file: TFile) {
        if (!this.settings.autoSync) return;
        
        const clientId = this.clientIdCache.get(file.path);
        if (!clientId) {
            console.warn('[MarkdownBrain] Cannot delete - no clientId:', file.path);
            return;
        }

        const content = await this.app.vault.read(file).catch(() => null);
        const hash = content ? await hashString(content) : undefined;

        this.addOp({
            rev: this.syncState.nextRev++,
            op: 'delete',
            fileId: clientId,
            ifMatchHash: hash
        });

        this.clientIdCache.delete(file.path);
        await this.flushPendingOps();
    }

    async handleFileRename(file: TFile, oldPath: string) {
        if (!this.settings.autoSync) return;
        this.clientIdCache.rename(oldPath, file.path);
        await this.addUpsertOp(file);
        await this.flushPendingOps();
    }

    private async addUpsertOp(file: TFile) {
        const content = await this.app.vault.read(file);
        const hash = await hashString(content);
        const clientId = await getOrCreateClientId(file, content, this.app);

        this.clientIdCache.set(file.path, clientId);

        this.addOp({
            rev: this.syncState.nextRev++,
            op: 'upsert',
            fileId: clientId,
            path: file.path,
            hash: hash,
            size: content.length
        });
    }

    private addOp(op: SyncOp) {
        const existingIdx = this.syncState.pendingOps.findIndex(
            o => o.fileId === op.fileId
        );
        if (existingIdx >= 0) {
            this.syncState.pendingOps[existingIdx] = op;
        } else {
            this.syncState.pendingOps.push(op);
        }
        this.saveSyncState();
    }

    // =========================================================================
    // Sync Operations
    // =========================================================================

    async flushPendingOps(): Promise<void> {
        if (this.syncState.pendingOps.length === 0) return;

        const ops = [...this.syncState.pendingOps];
        console.log(`[MarkdownBrain] Flushing ${ops.length} pending ops`);

        const planResponse = await this.syncClient.plan({
            mode: 'incremental',
            baseRev: this.syncState.lastSyncedRev,
            ops: ops
        });

        if (!planResponse.success) {
            if (planResponse.serverLastAppliedRev !== undefined) {
                console.warn('[MarkdownBrain] Cursor mismatch, triggering full sync');
                this.syncState.lastSyncedRev = planResponse.serverLastAppliedRev;
                await this.fullSync();
                return;
            }
            console.error('[MarkdownBrain] Plan failed:', planResponse.error);
            new Notice(`同步计划失败: ${planResponse.error}`);
            return;
        }

        const needUpload = planResponse.needUpload || [];
        const files: FileUpload[] = [];

        for (const item of needUpload) {
            const op = ops.find(o => o.fileId === item.fileId && o.op === 'upsert');
            if (!op?.path) continue;

            const tfile = this.app.vault.getAbstractFileByPath(op.path);
            if (!(tfile instanceof TFile)) continue;

            const content = await this.app.vault.read(tfile);
            const metadata = extractNoteMetadata(this.app.metadataCache.getFileCache(tfile));

            files.push({
                fileId: item.fileId,
                path: op.path,
                hash: item.hash,
                content: content,
                metadata: metadata as Record<string, unknown>
            });
        }

        const commitResponse = await this.syncClient.commit({
            syncToken: planResponse.syncToken!,
            files: files,
            finalize: true
        });

        if (commitResponse.success && commitResponse.status === 'ok') {
            this.syncState.lastSyncedRev = commitResponse.lastAppliedRev || 0;
            this.syncState.pendingOps = [];
            this.saveSyncState();
            console.log('[MarkdownBrain] ✓ Sync complete, rev:', this.syncState.lastSyncedRev);
        } else {
            console.error('[MarkdownBrain] Commit failed:', commitResponse.error);
            new Notice(`同步提交失败: ${commitResponse.error}`);
        }
    }

    async fullSync(): Promise<void> {
        const files = this.app.vault.getMarkdownFiles();
        new Notice(`开始全量同步 ${files.length} 个文件...`);
        console.log(`[MarkdownBrain] Full sync: ${files.length} files`);

        const manifest: ManifestEntry[] = [];
        const fileMap = new Map<string, TFile>();

        for (const file of files) {
            const content = await this.app.vault.read(file);
            if (!content.trim()) continue;

            const clientId = await getOrCreateClientId(file, content, this.app);
            const hash = await hashString(content);

            this.clientIdCache.set(file.path, clientId);
            manifest.push({ fileId: clientId, hash });
            fileMap.set(clientId, file);
        }

        const planResponse = await this.syncClient.plan({
            mode: 'full',
            manifest: manifest
        });

        if (!planResponse.success) {
            console.error('[MarkdownBrain] Full sync plan failed:', planResponse.error);
            new Notice(`全量同步计划失败: ${planResponse.error}`);
            return;
        }

        const needUpload = planResponse.needUpload || [];
        const stats: SyncStats = { success: 0, failed: 0, skipped: manifest.length - needUpload.length };

        if (needUpload.length > 0) {
            const batchSize = 20;
            for (let i = 0; i < needUpload.length; i += batchSize) {
                const batch = needUpload.slice(i, i + batchSize);
                const files: FileUpload[] = [];

                for (const item of batch) {
                    const tfile = fileMap.get(item.fileId);
                    if (!tfile) continue;

                    const content = await this.app.vault.read(tfile);
                    const metadata = extractNoteMetadata(this.app.metadataCache.getFileCache(tfile));

                    files.push({
                        fileId: item.fileId,
                        path: tfile.path,
                        hash: item.hash,
                        content: content,
                        metadata: metadata as Record<string, unknown>
                    });
                }

                const isLastBatch = i + batchSize >= needUpload.length;
                const commitResponse = await this.syncClient.commit({
                    syncToken: planResponse.syncToken!,
                    files: files,
                    finalize: isLastBatch
                });

                if (commitResponse.success) {
                    stats.success += files.length;
                    if (isLastBatch && commitResponse.lastAppliedRev) {
                        this.syncState.lastSyncedRev = commitResponse.lastAppliedRev;
                    }
                } else {
                    stats.failed += files.length;
                }

                new Notice(`同步进度: ${Math.min(i + batchSize, needUpload.length)}/${needUpload.length}`);
            }
        } else {
            this.syncState.lastSyncedRev = planResponse.serverState?.lastAppliedRev || 0;
        }

        this.syncState.pendingOps = [];
        this.saveSyncState();

        const orphans = planResponse.orphanCandidates?.length || 0;
        const msg = `同步完成! 成功: ${stats.success}, 跳过: ${stats.skipped}, 失败: ${stats.failed}` +
            (orphans > 0 ? `, 服务器孤儿: ${orphans}` : '');
        console.log(`[MarkdownBrain] ${msg}`);
        new Notice(msg, 5000);
    }

    // =========================================================================
    // State Persistence
    // =========================================================================

    private async loadSyncState(): Promise<void> {
        const data = await this.loadData();
        if (data?.syncState) {
            this.syncState = data.syncState;
            console.log('[MarkdownBrain] Loaded sync state:', {
                lastSyncedRev: this.syncState.lastSyncedRev,
                pendingOps: this.syncState.pendingOps.length
            });
        }
    }

    private async saveSyncState(): Promise<void> {
        await this.saveData({
            ...this.settings,
            syncState: this.syncState
        });
    }

    async loadSettings() {
        const data = await this.loadData();
        this.settings = Object.assign({}, DEFAULT_SETTINGS, data);
    }

    async saveSettings() {
        await this.saveData({
            ...this.settings,
            syncState: this.syncState
        });
        this.syncClient.updateConfig({
            serverUrl: this.settings.serverUrl,
            syncKey: this.settings.syncKey
        });
    }

    onunload() {
        console.log('[MarkdownBrain] Plugin unloading');
        this.debounceService.clearAll();
        this.syncClient.destroy();
    }
}
