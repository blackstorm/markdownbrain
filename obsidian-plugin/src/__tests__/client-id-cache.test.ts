import { describe, test, expect, beforeEach } from 'bun:test';
import { ClientIdCache } from '../services/client-id-cache';

describe('ClientIdCache', () => {
    let cache: ClientIdCache;

    beforeEach(() => {
        cache = new ClientIdCache();
    });

    describe('set and get', () => {
        test('stores and retrieves client id by path', () => {
            cache.set('notes/test.md', 'client-id-123');

            expect(cache.get('notes/test.md')).toBe('client-id-123');
        });

        test('returns undefined for non-existent path', () => {
            expect(cache.get('non-existent.md')).toBeUndefined();
        });

        test('overwrites existing client id', () => {
            cache.set('test.md', 'old-id');
            cache.set('test.md', 'new-id');

            expect(cache.get('test.md')).toBe('new-id');
        });
    });

    describe('has', () => {
        test('returns true if path exists', () => {
            cache.set('test.md', 'client-id');

            expect(cache.has('test.md')).toBe(true);
        });

        test('returns false if path does not exist', () => {
            expect(cache.has('non-existent.md')).toBe(false);
        });
    });

    describe('delete', () => {
        test('removes entry from cache', () => {
            cache.set('test.md', 'client-id');
            cache.delete('test.md');

            expect(cache.has('test.md')).toBe(false);
            expect(cache.get('test.md')).toBeUndefined();
        });

        test('does nothing for non-existent path', () => {
            // Should not throw
            cache.delete('non-existent.md');
            expect(cache.has('non-existent.md')).toBe(false);
        });
    });

    describe('rename', () => {
        test('moves client id from old path to new path', () => {
            cache.set('old-name.md', 'client-id-123');

            cache.rename('old-name.md', 'new-name.md');

            expect(cache.has('old-name.md')).toBe(false);
            expect(cache.get('new-name.md')).toBe('client-id-123');
        });

        test('does nothing if old path does not exist', () => {
            cache.rename('non-existent.md', 'new-name.md');

            expect(cache.has('new-name.md')).toBe(false);
        });

        test('overwrites existing entry at new path', () => {
            cache.set('old-name.md', 'moved-id');
            cache.set('new-name.md', 'existing-id');

            cache.rename('old-name.md', 'new-name.md');

            expect(cache.get('new-name.md')).toBe('moved-id');
        });
    });

    describe('clear', () => {
        test('removes all entries', () => {
            cache.set('file1.md', 'id-1');
            cache.set('file2.md', 'id-2');
            cache.set('file3.md', 'id-3');

            cache.clear();

            expect(cache.size).toBe(0);
            expect(cache.has('file1.md')).toBe(false);
            expect(cache.has('file2.md')).toBe(false);
            expect(cache.has('file3.md')).toBe(false);
        });
    });

    describe('size', () => {
        test('returns number of entries', () => {
            expect(cache.size).toBe(0);

            cache.set('file1.md', 'id-1');
            expect(cache.size).toBe(1);

            cache.set('file2.md', 'id-2');
            expect(cache.size).toBe(2);

            cache.delete('file1.md');
            expect(cache.size).toBe(1);
        });
    });

    describe('entries', () => {
        test('returns all entries as array', () => {
            cache.set('file1.md', 'id-1');
            cache.set('file2.md', 'id-2');

            const entries = cache.entries();

            expect(entries).toContainEqual(['file1.md', 'id-1']);
            expect(entries).toContainEqual(['file2.md', 'id-2']);
            expect(entries.length).toBe(2);
        });

        test('returns empty array when cache is empty', () => {
            expect(cache.entries()).toEqual([]);
        });
    });

    describe('getAllClientIds', () => {
        test('returns all client ids as array', () => {
            cache.set('file1.md', 'id-1');
            cache.set('file2.md', 'id-2');
            cache.set('file3.md', 'id-3');

            const ids = cache.getAllClientIds();

            expect(ids).toContain('id-1');
            expect(ids).toContain('id-2');
            expect(ids).toContain('id-3');
            expect(ids.length).toBe(3);
        });

        test('returns empty array when cache is empty', () => {
            expect(cache.getAllClientIds()).toEqual([]);
        });
    });

    describe('buildFromEntries', () => {
        test('populates cache from entries array', () => {
            const entries: Array<[string, string]> = [
                ['file1.md', 'id-1'],
                ['file2.md', 'id-2'],
            ];

            cache.buildFromEntries(entries);

            expect(cache.get('file1.md')).toBe('id-1');
            expect(cache.get('file2.md')).toBe('id-2');
            expect(cache.size).toBe(2);
        });

        test('clears existing entries before building', () => {
            cache.set('old-file.md', 'old-id');

            cache.buildFromEntries([['new-file.md', 'new-id']]);

            expect(cache.has('old-file.md')).toBe(false);
            expect(cache.get('new-file.md')).toBe('new-id');
            expect(cache.size).toBe(1);
        });
    });
});
