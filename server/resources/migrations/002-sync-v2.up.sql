-- Sync V2 Protocol: Add revision tracking and sync plans

-- Add last_applied_rev to vaults table for tracking server-side revision
ALTER TABLE vaults ADD COLUMN last_applied_rev INTEGER DEFAULT 0;

--;;

-- Add deleted_at to notes table for soft delete (tombstone)
ALTER TABLE notes ADD COLUMN deleted_at INTEGER;

--;;

-- Create sync_plans table for storing sync plans (syncToken verification)
CREATE TABLE sync_plans (
    id TEXT PRIMARY KEY,
    vault_id TEXT NOT NULL,
    from_rev INTEGER NOT NULL,
    to_rev INTEGER NOT NULL,
    need_upload TEXT,
    deletes TEXT,
    expires_at INTEGER NOT NULL,
    created_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now')),
    FOREIGN KEY (vault_id) REFERENCES vaults(id) ON DELETE CASCADE
);

--;;

-- Index for looking up sync plans by vault
CREATE INDEX idx_sync_plans_vault ON sync_plans(vault_id);

--;;

-- Index for cleaning up expired sync plans
CREATE INDEX idx_sync_plans_expires ON sync_plans(expires_at);

--;;

-- Index for querying deleted notes by vault
CREATE INDEX idx_notes_deleted ON notes(vault_id, deleted_at);
