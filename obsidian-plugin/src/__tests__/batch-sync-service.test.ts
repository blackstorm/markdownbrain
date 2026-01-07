import { describe, test, expect, mock, beforeEach } from 'bun:test';
import { BatchSyncService, BatchSyncDeps, BatchSyncCallbacks } from '../services/batch-sync-service';
import { SyncStats } from '../domain/types';

describe('BatchSyncService', () => {
    let service: BatchSyncService;
    let mockDeps: BatchSyncDeps;
    let callbacks: BatchSyncCallbacks;
    let progressReports: Array<{ current: number; total: number; stats: SyncStats }>;
    let completionReports: Array<{ stats: SyncStats }>;

    beforeEach(() => {
        progressReports = [];
        completionReports = [];

        mockDeps = {
            syncFile: mock(async () => {}),
            collectClientId: mock(async () => 'client-id'),
            cleanupOrphans: mock(async () => ({ deleted: 0 })),
        };

        callbacks = {
            onProgress: (current, total, stats) => {
                progressReports.push({ current, total, stats: { ...stats } });
            },
            onComplete: (stats) => {
                completionReports.push({ stats: { ...stats } });
            },
        };

        service = new BatchSyncService(mockDeps, { batchSize: 3 });
    });

    describe('syncAll', () => {
        test('processes files in batches', async () => {
            const files = [
                { path: 'a.md', extension: 'md', stat: { mtime: 0, ctime: 0, size: 100 } },
                { path: 'b.md', extension: 'md', stat: { mtime: 0, ctime: 0, size: 100 } },
                { path: 'c.md', extension: 'md', stat: { mtime: 0, ctime: 0, size: 100 } },
                { path: 'd.md', extension: 'md', stat: { mtime: 0, ctime: 0, size: 100 } },
                { path: 'e.md', extension: 'md', stat: { mtime: 0, ctime: 0, size: 100 } },
            ];

            await service.syncAll(files, callbacks);

            expect(mockDeps.syncFile).toHaveBeenCalledTimes(5);
        });

        test('reports progress after each batch', async () => {
            const files = [
                { path: 'a.md', extension: 'md', stat: { mtime: 0, ctime: 0, size: 100 } },
                { path: 'b.md', extension: 'md', stat: { mtime: 0, ctime: 0, size: 100 } },
                { path: 'c.md', extension: 'md', stat: { mtime: 0, ctime: 0, size: 100 } },
                { path: 'd.md', extension: 'md', stat: { mtime: 0, ctime: 0, size: 100 } },
            ];

            await service.syncAll(files, callbacks);

            expect(progressReports.length).toBe(2); // Two batches: [a,b,c] and [d]
            expect(progressReports[0].current).toBe(3);
            expect(progressReports[0].total).toBe(4);
            expect(progressReports[1].current).toBe(4);
            expect(progressReports[1].total).toBe(4);
        });

        test('tracks success stats', async () => {
            const files = [
                { path: 'a.md', extension: 'md', stat: { mtime: 0, ctime: 0, size: 100 } },
                { path: 'b.md', extension: 'md', stat: { mtime: 0, ctime: 0, size: 100 } },
            ];

            await service.syncAll(files, callbacks);

            expect(completionReports.length).toBe(1);
            expect(completionReports[0].stats.success).toBe(2);
            expect(completionReports[0].stats.failed).toBe(0);
        });

        test('tracks failed stats on sync error', async () => {
            mockDeps.syncFile = mock(async (file) => {
                if (file.path === 'b.md') {
                    throw new Error('Sync failed');
                }
            });

            const files = [
                { path: 'a.md', extension: 'md', stat: { mtime: 0, ctime: 0, size: 100 } },
                { path: 'b.md', extension: 'md', stat: { mtime: 0, ctime: 0, size: 100 } },
            ];

            await service.syncAll(files, callbacks);

            expect(completionReports[0].stats.success).toBe(1);
            expect(completionReports[0].stats.failed).toBe(1);
        });

        test('returns final stats', async () => {
            const files = [
                { path: 'a.md', extension: 'md', stat: { mtime: 0, ctime: 0, size: 100 } },
            ];

            const result = await service.syncAll(files, callbacks);

            expect(result.success).toBe(1);
            expect(result.failed).toBe(0);
            expect(result.skipped).toBe(0);
        });

        test('handles empty file list', async () => {
            const result = await service.syncAll([], callbacks);

            expect(result.success).toBe(0);
            expect(result.failed).toBe(0);
            expect(mockDeps.syncFile).not.toHaveBeenCalled();
        });
    });

    describe('collectAndCleanup', () => {
        test('collects client IDs for all files', async () => {
            const files = [
                { path: 'a.md', extension: 'md', stat: { mtime: 0, ctime: 0, size: 100 } },
                { path: 'b.md', extension: 'md', stat: { mtime: 0, ctime: 0, size: 100 } },
            ];

            await service.collectAndCleanup(files);

            expect(mockDeps.collectClientId).toHaveBeenCalledTimes(2);
        });

        test('calls cleanupOrphans with collected IDs', async () => {
            let callIndex = 0;
            mockDeps.collectClientId = mock(async () => `id-${++callIndex}`);

            const files = [
                { path: 'a.md', extension: 'md', stat: { mtime: 0, ctime: 0, size: 100 } },
                { path: 'b.md', extension: 'md', stat: { mtime: 0, ctime: 0, size: 100 } },
            ];

            await service.collectAndCleanup(files);

            expect(mockDeps.cleanupOrphans).toHaveBeenCalledWith(['id-1', 'id-2']);
        });

        test('returns deleted count', async () => {
            mockDeps.cleanupOrphans = mock(async () => ({ deleted: 5 }));

            const files = [
                { path: 'a.md', extension: 'md', stat: { mtime: 0, ctime: 0, size: 100 } },
            ];

            const result = await service.collectAndCleanup(files);

            expect(result.deletedCount).toBe(5);
        });

        test('skips files that fail to collect client ID', async () => {
            let callCount = 0;
            mockDeps.collectClientId = mock(async (file) => {
                if (file.path === 'b.md') {
                    throw new Error('Failed to read');
                }
                return `id-${++callCount}`;
            });

            const files = [
                { path: 'a.md', extension: 'md', stat: { mtime: 0, ctime: 0, size: 100 } },
                { path: 'b.md', extension: 'md', stat: { mtime: 0, ctime: 0, size: 100 } },
                { path: 'c.md', extension: 'md', stat: { mtime: 0, ctime: 0, size: 100 } },
            ];

            await service.collectAndCleanup(files);

            expect(mockDeps.cleanupOrphans).toHaveBeenCalledWith(['id-1', 'id-2']);
        });
    });
});
