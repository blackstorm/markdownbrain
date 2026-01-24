/**
 * Domain types for MarkdownBrain Obsidian Plugin
 *
 * All interfaces and types extracted from main.ts for better modularity.
 * No runtime dependencies - pure type definitions.
 */

// =============================================================================
// Settings
// =============================================================================

export interface MarkdownBrainSettings {
  serverUrl: string;
  publishKey: string;
  autoSync: boolean;
}

export const DEFAULT_SETTINGS: MarkdownBrainSettings = {
  serverUrl: "https://api.markdownbrain.com",
  publishKey: "",
  autoSync: true,
};

// =============================================================================
// Sync Configuration
// =============================================================================

export interface SyncConfig {
  serverUrl: string;
  publishKey: string;
}

// =============================================================================
// Note Metadata (extracted from Obsidian's metadataCache)
// =============================================================================

export interface Position {
  start: { line: number; col: number; offset: number };
  end: { line: number; col: number; offset: number };
}

export interface TagInfo {
  tag: string;
  position: Position;
}

export interface HeadingInfo {
  heading: string;
  level: number;
  position: Position;
}

export interface NoteMetadata {
  tags?: TagInfo[];
  headings?: HeadingInfo[];
  frontmatter?: Record<string, unknown>;
}

// =============================================================================
// Port Interfaces (for dependency injection)
// =============================================================================

export interface LoggerPort {
  info(message: string, data?: Record<string, unknown>): void;
  warn(message: string, data?: Record<string, unknown>): void;
  error(message: string, data?: Record<string, unknown>): void;
  debug(message: string, data?: Record<string, unknown>): void;
}

export interface NoticePort {
  show(message: string, timeoutMs?: number): void;
}

export interface FileInfo {
  path: string;
  extension: string;
  stat: {
    mtime: number;
    ctime: number;
    size: number;
  };
}

export interface VaultPort {
  getMarkdownFiles(): FileInfo[];
  readText(file: FileInfo): Promise<string>;
  readBinary(file: FileInfo): Promise<ArrayBuffer>;
  stat(file: FileInfo): { mtime: number; size: number };
}

export interface CachedMetadata {
  tags?: Array<{ tag: string; position: Position }>;
  headings?: Array<{ heading: string; level: number; position: Position }>;
  frontmatter?: Record<string, unknown>;
}

export interface MetadataPort {
  getFileCache(path: string): CachedMetadata | null;
}

// =============================================================================
// API Client Interface
// =============================================================================
