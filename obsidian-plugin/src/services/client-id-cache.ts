/**
 * Cache for mapping file paths to client IDs
 * 
 * Provides fast lookup and handles path renames gracefully.
 * Used to track client IDs without re-reading file content/frontmatter.
 * 
 * @example
 * ```ts
 * const cache = new ClientIdCache();
 * 
 * // On sync success
 * cache.set(file.path, clientId);
 * 
 * // On file delete
 * const clientId = cache.get(file.path);
 * cache.delete(file.path);
 * 
 * // On file rename
 * cache.rename(oldPath, newPath);
 * ```
 */
export class ClientIdCache {
    private cache: Map<string, string>;

    constructor() {
        this.cache = new Map();
    }

    /**
     * Get client ID for a file path
     */
    get(path: string): string | undefined {
        return this.cache.get(path);
    }

    /**
     * Set client ID for a file path
     */
    set(path: string, clientId: string): void {
        this.cache.set(path, clientId);
    }

    /**
     * Check if a path has a cached client ID
     */
    has(path: string): boolean {
        return this.cache.has(path);
    }

    /**
     * Remove client ID for a file path
     */
    delete(path: string): void {
        this.cache.delete(path);
    }

    /**
     * Handle file rename - moves client ID from old path to new path
     */
    rename(oldPath: string, newPath: string): void {
        const clientId = this.cache.get(oldPath);
        if (clientId !== undefined) {
            this.cache.delete(oldPath);
            this.cache.set(newPath, clientId);
        }
    }

    /**
     * Clear all cached entries
     */
    clear(): void {
        this.cache.clear();
    }

    /**
     * Get number of cached entries
     */
    get size(): number {
        return this.cache.size;
    }

    /**
     * Get all entries as array of [path, clientId] tuples
     */
    entries(): Array<[string, string]> {
        return Array.from(this.cache.entries());
    }

    /**
     * Get all client IDs
     */
    getAllClientIds(): string[] {
        return Array.from(this.cache.values());
    }

    /**
     * Build cache from entries (replaces existing cache)
     */
    buildFromEntries(entries: Array<[string, string]>): void {
        this.cache.clear();
        for (const [path, clientId] of entries) {
            this.cache.set(path, clientId);
        }
    }
}
