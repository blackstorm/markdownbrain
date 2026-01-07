-- Migration 003: Rename resources table to assets + add client_id + create note_asset_refs
-- This migration supports the new asset lifecycle management with reference tracking
-- Note: deleted_at column serves as orphaned_at (SQLite ALTER TABLE limitations)

-- Step 1: Create new assets table with proper schema (including UNIQUE constraint on client_id)
CREATE TABLE IF NOT EXISTS assets (
  id TEXT PRIMARY KEY,
  tenant_id TEXT NOT NULL,
  vault_id TEXT NOT NULL,
  client_id TEXT,
  path TEXT NOT NULL,
  object_key TEXT NOT NULL,
  size_bytes INTEGER NOT NULL,
  content_type TEXT NOT NULL,
  sha256 TEXT NOT NULL,
  original_name TEXT,
  deleted_at INTEGER,
  created_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now')),
  updated_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now')),
  UNIQUE(vault_id, client_id),
  FOREIGN KEY (tenant_id) REFERENCES tenants(id),
  FOREIGN KEY (vault_id) REFERENCES vaults(id) ON DELETE CASCADE
);

-- Step 2: Copy data from resources to assets (if resources table exists)
INSERT OR IGNORE INTO assets (id, tenant_id, vault_id, path, object_key, size_bytes, content_type, sha256, deleted_at, created_at, updated_at)
SELECT id, tenant_id, vault_id, path, object_key, size_bytes, content_type, sha256, deleted_at, created_at, updated_at
FROM resources WHERE EXISTS (SELECT 1 FROM sqlite_master WHERE type='table' AND name='resources');

-- Step 3: Drop old resources table and its indexes
DROP INDEX IF EXISTS idx_resources_vault;
DROP INDEX IF EXISTS idx_resources_path;
DROP INDEX IF EXISTS idx_resources_deleted;
DROP TABLE IF EXISTS resources;

-- Step 4: Create new indexes for assets table
CREATE INDEX IF NOT EXISTS idx_assets_vault ON assets(vault_id);
CREATE INDEX IF NOT EXISTS idx_assets_path ON assets(vault_id, path);
CREATE INDEX IF NOT EXISTS idx_assets_deleted ON assets(vault_id, deleted_at);
CREATE INDEX IF NOT EXISTS idx_assets_client_id ON assets(vault_id, client_id);
CREATE INDEX IF NOT EXISTS idx_assets_hash ON assets(vault_id, sha256);

-- Step 5: Create note_asset_refs table for reference tracking
CREATE TABLE IF NOT EXISTS note_asset_refs (
  id TEXT PRIMARY KEY,
  vault_id TEXT NOT NULL,
  note_client_id TEXT NOT NULL,
  asset_client_id TEXT NOT NULL,
  created_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now')),
  
  UNIQUE(vault_id, note_client_id, asset_client_id),
  FOREIGN KEY (vault_id) REFERENCES vaults(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_asset_refs_note ON note_asset_refs(vault_id, note_client_id);
CREATE INDEX IF NOT EXISTS idx_asset_refs_asset ON note_asset_refs(vault_id, asset_client_id);
