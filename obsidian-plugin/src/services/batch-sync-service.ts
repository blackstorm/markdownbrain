import { SyncStats, FileInfo } from '../domain/types';

export interface BatchSyncDeps {
    syncFile: (file: FileInfo) => Promise<void>;
    collectClientId: (file: FileInfo) => Promise<string>;
    cleanupOrphans: (clientIds: string[]) => Promise<{ deleted: number }>;
}

export interface BatchSyncCallbacks {
    onProgress?: (current: number, total: number, stats: SyncStats) => void;
    onComplete?: (stats: SyncStats) => void;
}

export interface BatchSyncOptions {
    batchSize?: number;
}

export class BatchSyncService {
    private batchSize: number;
    private deps: BatchSyncDeps;

    constructor(deps: BatchSyncDeps, options: BatchSyncOptions = {}) {
        this.deps = deps;
        this.batchSize = options.batchSize ?? 10;
    }

    async syncAll(files: FileInfo[], callbacks: BatchSyncCallbacks = {}): Promise<SyncStats> {
        const stats: SyncStats = { success: 0, failed: 0, skipped: 0 };

        for (let i = 0; i < files.length; i += this.batchSize) {
            const batch = files.slice(i, i + this.batchSize);
            await Promise.all(batch.map(file => this.syncOneFile(file, stats)));

            const progress = Math.min(i + this.batchSize, files.length);
            callbacks.onProgress?.(progress, files.length, stats);
        }

        callbacks.onComplete?.(stats);
        return stats;
    }

    private async syncOneFile(file: FileInfo, stats: SyncStats): Promise<void> {
        try {
            await this.deps.syncFile(file);
            stats.success++;
        } catch {
            stats.failed++;
        }
    }

    async collectAndCleanup(files: FileInfo[]): Promise<{ deletedCount: number; clientIds: string[] }> {
        const clientIds: string[] = [];

        for (const file of files) {
            try {
                const id = await this.deps.collectClientId(file);
                clientIds.push(id);
            } catch {
                // Skip files that fail to collect
            }
        }

        const result = await this.deps.cleanupOrphans(clientIds);
        return { deletedCount: result.deleted, clientIds };
    }
}
