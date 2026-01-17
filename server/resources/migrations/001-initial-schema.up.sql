-- MarkdownBrain Database Schema
-- Single migration file for MVP (no backward compatibility needed)

-- Tenants table (one per user/organization)
CREATE TABLE IF NOT EXISTS tenants (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
--;;

-- Admin users (manage vaults)
CREATE TABLE IF NOT EXISTS users (
  id TEXT PRIMARY KEY,
  tenant_id TEXT NOT NULL,
  username TEXT UNIQUE NOT NULL,
  password_hash TEXT NOT NULL,
  role TEXT DEFAULT 'admin',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (tenant_id) REFERENCES tenants(id)
);
--;;

-- Vaults (one tenant can have multiple vaults)
CREATE TABLE IF NOT EXISTS vaults (
  id TEXT PRIMARY KEY,
  tenant_id TEXT NOT NULL,
  name TEXT NOT NULL,
  domain TEXT UNIQUE,
  sync_key TEXT UNIQUE NOT NULL,
  client_type TEXT NOT NULL DEFAULT 'obsidian',
  root_note_id TEXT,
  logo_object_key TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (tenant_id) REFERENCES tenants(id)
);
--;;

CREATE INDEX IF NOT EXISTS idx_vaults_domain ON vaults(domain);
--;;

CREATE INDEX IF NOT EXISTS idx_vaults_sync_key ON vaults(sync_key);
--;;

-- Notes (markdown files)
CREATE TABLE IF NOT EXISTS notes (
  id TEXT PRIMARY KEY,
  tenant_id TEXT NOT NULL,
  vault_id TEXT NOT NULL,
  path TEXT NOT NULL,
  client_id TEXT NOT NULL,
  content TEXT,
  metadata TEXT,
  hash TEXT,
  mtime TIMESTAMP,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(vault_id, client_id),
  FOREIGN KEY (tenant_id) REFERENCES tenants(id),
  FOREIGN KEY (vault_id) REFERENCES vaults(id) ON DELETE CASCADE
);
--;;

CREATE INDEX IF NOT EXISTS idx_notes_vault ON notes(vault_id);
--;;

CREATE INDEX IF NOT EXISTS idx_notes_client_id ON notes(vault_id, client_id);
--;;

CREATE INDEX IF NOT EXISTS idx_notes_path ON notes(vault_id, path);
--;;

CREATE INDEX IF NOT EXISTS idx_notes_mtime ON notes(mtime);
--;;

-- Note links (bidirectional links between notes)
CREATE TABLE IF NOT EXISTS note_links (
  id TEXT PRIMARY KEY,
  vault_id TEXT NOT NULL,
  source_client_id TEXT NOT NULL,
  target_client_id TEXT NOT NULL,
  target_path TEXT,
  link_type TEXT NOT NULL,
  display_text TEXT,
  original TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (vault_id) REFERENCES vaults(id) ON DELETE CASCADE
);
--;;

CREATE INDEX IF NOT EXISTS idx_links_source ON note_links(vault_id, source_client_id);
--;;

CREATE INDEX IF NOT EXISTS idx_links_target ON note_links(vault_id, target_client_id);
--;;

CREATE INDEX IF NOT EXISTS idx_links_both ON note_links(vault_id, source_client_id, target_client_id);
--;;

-- Assets (binary files: images, videos, PDFs, etc.)
-- object_key is based on client_id (stable across renames)
-- Format: "assets/{client_id}" or "assets/{client_id}.{ext}"
CREATE TABLE IF NOT EXISTS assets (
  id TEXT PRIMARY KEY,
  tenant_id TEXT NOT NULL,
  vault_id TEXT NOT NULL,
  client_id TEXT NOT NULL,
  path TEXT NOT NULL,
  object_key TEXT NOT NULL,
  size_bytes INTEGER NOT NULL,
  content_type TEXT NOT NULL,
  sha256 TEXT NOT NULL,
  deleted_at INTEGER,
  created_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now')),
  updated_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now')),
  UNIQUE(vault_id, client_id),
  FOREIGN KEY (tenant_id) REFERENCES tenants(id),
  FOREIGN KEY (vault_id) REFERENCES vaults(id) ON DELETE CASCADE
);
--;;

CREATE INDEX IF NOT EXISTS idx_assets_vault ON assets(vault_id);
--;;

CREATE INDEX IF NOT EXISTS idx_assets_path ON assets(vault_id, path);
--;;

CREATE INDEX IF NOT EXISTS idx_assets_deleted ON assets(vault_id, deleted_at);
--;;

CREATE INDEX IF NOT EXISTS idx_assets_client_id ON assets(vault_id, client_id);
--;;

CREATE INDEX IF NOT EXISTS idx_assets_hash ON assets(vault_id, sha256);
--;;

-- Note-Asset references (tracks which notes reference which assets)
CREATE TABLE IF NOT EXISTS note_asset_refs (
  id TEXT PRIMARY KEY,
  vault_id TEXT NOT NULL,
  note_client_id TEXT NOT NULL,
  asset_client_id TEXT NOT NULL,
  created_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now')),
  UNIQUE(vault_id, note_client_id, asset_client_id),
  FOREIGN KEY (vault_id) REFERENCES vaults(id) ON DELETE CASCADE
);
--;;

CREATE INDEX IF NOT EXISTS idx_asset_refs_note ON note_asset_refs(vault_id, note_client_id);
--;;

CREATE INDEX IF NOT EXISTS idx_asset_refs_asset ON note_asset_refs(vault_id, asset_client_id);
