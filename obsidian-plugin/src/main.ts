/**
 * MarkdownBrain Obsidian Plugin - Snapshot Sync
 */

import { type App, Notice, Plugin, type PluginManifest, TFile } from "obsidian";
import { ObsidianHttpClient } from "./api";
import { SyncApiClient, type SyncSnapshotEntry } from "./api/sync-api";
import { CLIENT_ID_KEY, getOrCreateClientId } from "./core/client-id";
import { DEFAULT_SETTINGS, type MarkdownBrainSettings } from "./domain/types";
import { MarkdownBrainSettingTab, registerFileEvents } from "./plugin";
import { ClientIdCache, DebounceService, extractNoteMetadata } from "./services";
import { extractNotePaths, getContentType, hashString, isAssetFile, md5Hash } from "./utils";
import { extractAssetPaths } from "./utils/asset-links";

export default class MarkdownBrainPlugin extends Plugin {
  settings!: MarkdownBrainSettings;
  syncClient!: SyncApiClient;
  private debounceService: DebounceService;
  private clientIdCache: ClientIdCache;
  private pendingSyncs: Set<string>;

  constructor(app: App, manifest: PluginManifest) {
    super(app, manifest);
    this.debounceService = new DebounceService();
    this.clientIdCache = new ClientIdCache();
    this.pendingSyncs = new Set();
  }

  async onload() {
    console.log("[MarkdownBrain] Plugin loading (snapshot sync)...");
    await this.loadSettings();

    const httpClient = new ObsidianHttpClient();
    this.syncClient = new SyncApiClient(
      {
        serverUrl: this.settings.serverUrl,
        syncKey: this.settings.syncKey,
      },
      httpClient,
    );

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
      {
        add: (path) => this.pendingSyncs.add(path),
        clear: () => this.pendingSyncs.clear(),
        forEach: (callback) => this.pendingSyncs.forEach(callback),
      },
      (event) => this.registerEvent(event),
    );

    this.addSettingTab(new MarkdownBrainSettingTab(this.app, this));

    this.addCommand({
      id: "sync-current-file",
      name: "Sync current file",
      callback: () => {
        const file = this.app.workspace.getActiveFile();
        if (file && file.extension === "md") {
          this.handleFileChange(file, "modify");
        }
      },
    });

    this.addCommand({
      id: "sync-all-files",
      name: "Sync all files (full sync)",
      callback: () => this.fullSync(),
    });

    await this.buildClientIdCache();
    console.log("[MarkdownBrain] ✓ Plugin loaded");
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

  async handleFileChange(file: TFile, _action: "create" | "modify") {
    if (!this.settings.autoSync) return;

    this.debounceService.debounce(
      file.path,
      async () => {
        const result = await this.syncNoteFile(file);
        if (!result.success) {
          new Notice("同步失败: 笔记上传失败");
          return;
        }
        await this.syncAssetsForNote(file, result.needUploadAssets, result.assetsById);
        await this.syncLinkedNotesForNote(file, result.needUploadNotes, result.linkedNotesById);
      },
      500,
    );
  }

  async handleFileDelete(_file: TFile) {
    if (!this.settings.autoSync) return;
    await this.fullSync();
  }

  async handleFileRename(file: TFile, oldPath: string) {
    if (!this.settings.autoSync) return;
    this.clientIdCache.rename(oldPath, file.path);
    const result = await this.syncNoteFile(file);
    if (!result.success) {
      new Notice("同步失败: 笔记重命名同步失败");
      return;
    }
    await this.syncAssetsForNote(file, result.needUploadAssets, result.assetsById);
    await this.syncLinkedNotesForNote(file, result.needUploadNotes, result.linkedNotesById);
  }

  async handleAssetChange(file: TFile) {
    if (!this.settings.autoSync) return;
    const referencedAssets = await this.collectReferencedAssetFiles();
    if (!referencedAssets.some((asset) => asset.path === file.path)) {
      return;
    }
    const result = await this.syncAssetFile(file);
    if (!result) {
      new Notice("同步失败: 资源上传失败");
    }
  }

  // =========================================================================
  // Sync Operations
  // =========================================================================

  private async syncNoteFile(file: TFile): Promise<{
    success: boolean;
    needUploadAssets: Array<{ id: string; hash: string }>;
    assetsById: Map<string, TFile>;
    needUploadNotes: Array<{ id: string; hash: string }>;
    linkedNotesById: Map<string, TFile>;
  }> {
    const content = await this.app.vault.read(file);
    const hash = await hashString(content);
    const clientId = await getOrCreateClientId(file, content, this.app);
    const metadata = extractNoteMetadata(this.app.metadataCache.getFileCache(file));
    const assets = await this.collectReferencedAssetEntriesForNote(file);
    const linkedNotes = await this.collectLinkedNoteEntriesForNote(file);

    this.clientIdCache.set(file.path, clientId);

    const result = await this.syncClient.syncNote(clientId, {
      path: file.path,
      content,
      hash,
      metadata: metadata as Record<string, unknown>,
      assets: assets.entries,
      linked_notes: linkedNotes.entries,
    });

    return {
      success: result.success,
      needUploadAssets: result.need_upload_assets ?? [],
      assetsById: assets.byId,
      needUploadNotes: result.need_upload_notes ?? [],
      linkedNotesById: linkedNotes.byId,
    };
  }

  private async syncAssetFile(file: TFile): Promise<boolean> {
    const buffer = await this.app.vault.readBinary(file);
    const assetId = await hashString(file.path);
    const hash = await md5Hash(buffer);
    const base64 = Buffer.from(new Uint8Array(buffer)).toString("base64");

    const result = await this.syncClient.syncAsset(assetId, {
      path: file.path,
      contentType: getContentType(file.extension),
      size: buffer.byteLength,
      hash,
      content: base64,
    });

    return result.success;
  }

  async fullSync(): Promise<void> {
    const notes = await this.buildNoteSnapshot();
    const referencedAssets = await this.collectReferencedAssetFiles();
    const assets = await this.buildAssetSnapshot(referencedAssets);

    new Notice(`开始全量同步：${notes.length} 笔记 / ${assets.length} 资源`);

    const changes = await this.syncClient.syncChanges({ notes, assets });
    if (!changes.success) {
      new Notice(`全量同步失败: ${changes.error}`);
      return;
    }

    const needNotes = changes.need_upsert?.notes ?? [];
    const needAssets = changes.need_upsert?.assets ?? [];

    if (needAssets.length > 0) {
      await this.uploadAssets(needAssets, referencedAssets);
    }

    if (needNotes.length > 0) {
      await this.uploadNotes(needNotes);
    }

    new Notice("全量同步完成");
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
      const result = await this.syncNoteFile(file);
      if (!result.success) {
        new Notice("同步失败: 笔记上传失败");
      }
    }
  }

  private async uploadAssets(entries: SyncSnapshotEntry[], assetFiles?: TFile[]): Promise<void> {
    const targetAssets = assetFiles ?? this.app.vault.getFiles().filter(isAssetFile);
    const assetMap = new Map<string, TFile>();
    for (const file of targetAssets) {
      const assetId = await hashString(file.path);
      assetMap.set(assetId, file);
    }

    const tasks = entries.map((entry) => async () => {
      const file = assetMap.get(entry.id);
      if (!file) return;
      await this.syncAssetFile(file);
    });

    await this.runWithConcurrency(tasks, 3);
  }

  private async runWithConcurrency(
    tasks: Array<() => Promise<void>>,
    limit: number,
  ): Promise<void> {
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

  private async buildAssetSnapshot(assetFiles?: TFile[]): Promise<SyncSnapshotEntry[]> {
    const assets = assetFiles ?? this.app.vault.getFiles().filter(isAssetFile);
    const snapshot: SyncSnapshotEntry[] = [];

    for (const file of assets) {
      const buffer = await this.app.vault.readBinary(file);
      const assetId = await hashString(file.path);
      const hash = await md5Hash(buffer);
      snapshot.push({ id: assetId, hash });
    }

    return snapshot;
  }

  private async collectReferencedAssetFiles(): Promise<TFile[]> {
    const referenced = new Map<string, TFile>();
    const notes = this.app.vault.getMarkdownFiles();

    for (const note of notes) {
      const content = await this.app.vault.read(note);
      const paths = extractAssetPaths(content);
      for (const path of paths) {
        const resolved = this.app.metadataCache.getFirstLinkpathDest(path, note.path);
        if (resolved instanceof TFile && isAssetFile(resolved)) {
          referenced.set(resolved.path, resolved);
        }
      }
    }

    return Array.from(referenced.values());
  }

  private async collectReferencedAssetFilesForNote(note: TFile): Promise<TFile[]> {
    const referenced = new Map<string, TFile>();
    const content = await this.app.vault.read(note);
    const paths = extractAssetPaths(content);

    for (const path of paths) {
      const resolved = this.app.metadataCache.getFirstLinkpathDest(path, note.path);
      if (resolved instanceof TFile && isAssetFile(resolved)) {
        referenced.set(resolved.path, resolved);
      }
    }

    return Array.from(referenced.values());
  }

  private async collectReferencedAssetEntriesForNote(note: TFile): Promise<{
    entries: Array<{ id: string; hash: string }>;
    byId: Map<string, TFile>;
  }> {
    const files = await this.collectReferencedAssetFilesForNote(note);
    const byId = new Map<string, TFile>();
    const entries = await Promise.all(
      files.map(async (file) => {
        const buffer = await this.app.vault.readBinary(file);
        const id = await hashString(file.path);
        const hash = await md5Hash(buffer);
        byId.set(id, file);
        return { id, hash };
      }),
    );
    return { entries, byId };
  }

  private async collectLinkedNoteEntriesForNote(note: TFile): Promise<{
    entries: Array<{ id: string; hash: string }>;
    byId: Map<string, TFile>;
  }> {
    const content = await this.app.vault.read(note);
    const paths = extractNotePaths(content);
    const linkedFiles = new Map<string, TFile>();

    for (const path of paths) {
      const resolved = this.app.metadataCache.getFirstLinkpathDest(path, note.path);
      if (resolved instanceof TFile && resolved.extension === "md") {
        linkedFiles.set(resolved.path, resolved);
      }
    }

    const byId = new Map<string, TFile>();
    const entries = await Promise.all(
      Array.from(linkedFiles.values()).map(async (file) => {
        const linkedContent = await this.app.vault.read(file);
        const id = await getOrCreateClientId(file, linkedContent, this.app);
        const hash = await hashString(linkedContent);
        this.clientIdCache.set(file.path, id);
        byId.set(id, file);
        return { id, hash };
      }),
    );

    return { entries, byId };
  }

  private async syncAssetsForNote(
    note: TFile,
    needUploadAssets?: Array<{ id: string }>,
    assetsById?: Map<string, TFile>,
  ): Promise<void> {
    const lookup = assetsById ?? (await this.collectReferencedAssetEntriesForNote(note)).byId;
    const assetsToUpload = needUploadAssets?.length
      ? needUploadAssets
      : Array.from(lookup.keys()).map((id) => ({ id }));

    if (assetsToUpload.length === 0) {
      return;
    }

    let failed = false;
    for (const asset of assetsToUpload) {
      const file = lookup.get(asset.id);
      if (!file) continue;
      const result = await this.syncAssetFile(file);
      if (!result) {
        failed = true;
      }
    }

    if (failed) {
      new Notice("同步失败: 资源上传失败");
    }
  }

  private async syncLinkedNotesForNote(
    note: TFile,
    needUploadNotes: Array<{ id: string; hash: string }>,
    linkedNotesById: Map<string, TFile>,
  ): Promise<void> {
    if (needUploadNotes.length === 0) {
      return;
    }

    let failed = false;
    for (const entry of needUploadNotes) {
      const file = linkedNotesById.get(entry.id);
      if (!file || file.path === note.path) continue;
      const result = await this.syncNoteFile(file);
      if (!result.success) {
        failed = true;
        continue;
      }
      await this.syncAssetsForNote(file, result.needUploadAssets, result.assetsById);
    }

    if (failed) {
      new Notice("同步失败: 关联笔记上传失败");
    }
  }

  // extractAssetIds removed; asset uploads now use per-note asset entries with hashes.

  // =========================================================================
  // Settings Persistence
  // =========================================================================

  async loadSettings() {
    const data = await this.loadData();
    this.settings = Object.assign({}, DEFAULT_SETTINGS, data);
  }

  async saveSettings() {
    await this.saveData({
      ...this.settings,
    });
    this.syncClient.updateConfig({
      serverUrl: this.settings.serverUrl,
      syncKey: this.settings.syncKey,
    });
  }

  onunload() {
    console.log("[MarkdownBrain] Plugin unloading");
    this.debounceService.clearAll();
    this.pendingSyncs.clear();
  }
}
