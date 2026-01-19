import type { NoteMetadata } from "../domain/types";

/**
 * Minimal representation of Obsidian's CachedMetadata
 * Used for type-safe extraction without full Obsidian dependency
 */
export interface ObsidianCachedMetadata {
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
  frontmatter?: Record<string, unknown>;
}

/**
 * Extract note metadata from Obsidian's cached metadata
 *
 * Pure function - no side effects, easily testable
 *
 * @param cached - Obsidian's CachedMetadata (or null/undefined)
 * @returns NoteMetadata with tags, headings, and frontmatter
 *
 * @example
 * ```ts
 * const cached = app.metadataCache.getFileCache(file);
 * const metadata = extractNoteMetadata(cached);
 * ```
 */
export function extractNoteMetadata(
  cached: ObsidianCachedMetadata | null | undefined,
): NoteMetadata {
  if (!cached) {
    return {};
  }

  const metadata: NoteMetadata = {};

  if (cached.tags) {
    metadata.tags = cached.tags.map((tag) => ({
      tag: tag.tag,
      position: tag.position,
    }));
  }

  if (cached.headings) {
    metadata.headings = cached.headings.map((heading) => ({
      heading: heading.heading,
      level: heading.level,
      position: heading.position,
    }));
  }

  if (cached.frontmatter) {
    metadata.frontmatter = cached.frontmatter;
  }

  return metadata;
}
