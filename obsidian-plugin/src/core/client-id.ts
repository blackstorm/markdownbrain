import { TFile, App, parseYaml } from 'obsidian';

/**
 * Get or create a client ID from frontmatter using Obsidian's official API.
 *
 * This function uses `app.fileManager.processFrontMatter` for writing, which is:
 * - Atomic (prevents race conditions)
 * - Safe (handles complex YAML correctly)
 * - Compatible with Obsidian's "Properties" UI
 *
 * For reading, we use the metadataCache for fast lookups, falling back to
 * parseYaml for cases where the cache hasn't updated yet.
 *
 * @param file - The file to get or create the client ID for
 * @param content - The current content of the file (used as fallback)
 * @param app - The Obsidian app instance
 * @returns The client ID (existing or newly created)
 */
export async function getOrCreateClientId(
  file: TFile,
  content: string,
  app: App
): Promise<string> {
  // Step 1: Try to read existing markdownbrain-id from metadataCache (fastest)
  const cache = app.metadataCache.getFileCache(file);
  if (cache?.frontmatter?.['markdownbrain-id']) {
    return String(cache.frontmatter['markdownbrain-id']).trim();
  }

  // Step 2: Cache might not be updated yet, try parsing directly
  // This handles the case where we just wrote to the file
  const fileContent = await app.vault.read(file);
  const latestContent = fileContent || content;

  const frontmatterMatch = latestContent.match(/^---\n([\s\S]*?)\n---/);
  if (frontmatterMatch) {
    try {
      const frontmatter = parseYaml(frontmatterMatch[1]);
      if (frontmatter?.['markdownbrain-id']) {
        return String(frontmatter['markdownbrain-id']).trim();
      }
    } catch {
      // YAML parse error, will generate new ID
    }
  }

  // Step 3: No existing ID, generate a new one and write it using processFrontMatter
  const newId = crypto.randomUUID();

  await app.fileManager.processFrontMatter(file, (frontmatter) => {
    frontmatter['markdownbrain-id'] = newId;
  });

  return newId;
}
