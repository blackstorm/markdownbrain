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
  syncKey: string;
  autoSync: boolean;
}

export const DEFAULT_SETTINGS: MarkdownBrainSettings = {
  serverUrl: 'https://api.markdownbrain.com',
  syncKey: '',
  autoSync: true
};

// =============================================================================
// Sync Configuration
// =============================================================================

export interface SyncConfig {
  serverUrl: string;
  syncKey: string;
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
// Sync Data Payloads
// =============================================================================

export type SyncAction = 'create' | 'modify' | 'delete';

export interface SyncData {
  path: string;
  clientId: string;
  clientType: string;
  content?: string;
  hash?: string;
  mtime?: string;
  metadata?: NoteMetadata;
  action: SyncAction;
}

export type AssetAction = 'create' | 'modify' | 'delete';

export interface AssetSyncData {
  path: string;
  clientId: string;
  content?: string;
  contentType: string;
  sha256?: string;
  size?: number;
  action: AssetAction;
}

export type ResourceSyncData = AssetSyncData;
export type ResourceAction = AssetAction;

// =============================================================================
// API Responses
// =============================================================================

export interface VaultInfo {
  id: string;
  name: string;
  domain: string;
  'created-at': string;
}

export interface TestConnectionResult {
  success: boolean;
  vaultInfo?: VaultInfo;
  error?: string;
}

export interface SyncResult {
  success: boolean;
  error?: string;
}

export interface FullSyncRequest {
  clientIds: string[];
}

export interface FullSyncResponseData {
  'vault-id': string;
  action: string;
  'client-notes': number;
  'deleted-count': number;
  'remaining-notes': number;
}

export interface FullSyncResponse {
  success: boolean;
  data?: FullSyncResponseData;
  error?: string;
}

// =============================================================================
// Sync Statistics
// =============================================================================

export interface SyncStats {
  success: number;
  failed: number;
  skipped: number;
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

export interface SyncApi {
  testConnection(timeout?: number): Promise<TestConnectionResult>;
  syncNote(data: SyncData): Promise<SyncResult>;
  fullSync(clientIds: string[]): Promise<FullSyncResponse>;
  syncAsset(data: AssetSyncData): Promise<SyncResult>;
}
