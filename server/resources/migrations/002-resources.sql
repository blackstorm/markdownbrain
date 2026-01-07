-- Resources table (binary files: images, videos, PDFs, etc.)
CREATE TABLE IF NOT EXISTS resources (
  id TEXT PRIMARY KEY,
  tenant_id TEXT NOT NULL,
  vault_id TEXT NOT NULL,
  path TEXT NOT NULL,
  object_key TEXT NOT NULL,
  size_bytes INTEGER NOT NULL,
  content_type TEXT NOT NULL,
  sha256 TEXT NOT NULL,
  deleted_at INTEGER,
  created_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now')),
  updated_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now')),
  UNIQUE(vault_id, path),
  FOREIGN KEY (tenant_id) REFERENCES tenants(id),
  FOREIGN KEY (vault_id) REFERENCES vaults(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_resources_vault ON resources(vault_id);
CREATE INDEX IF NOT EXISTS idx_resources_path ON resources(vault_id, path);
CREATE INDEX IF NOT EXISTS idx_resources_deleted ON resources(vault_id, deleted_at);
