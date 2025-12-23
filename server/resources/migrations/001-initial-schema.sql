-- Tenants table (one per user/organization)
CREATE TABLE IF NOT EXISTS tenants (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

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

-- Vaults (one tenant can have multiple vaults)
CREATE TABLE IF NOT EXISTS vaults (
  id TEXT PRIMARY KEY,
  tenant_id TEXT NOT NULL,
  name TEXT NOT NULL,
  domain TEXT UNIQUE,
  sync_key TEXT UNIQUE NOT NULL,
  client_type TEXT NOT NULL DEFAULT 'obsidian',  -- 客户端类型: obsidian, logseq, notion 等
  root_doc_id TEXT,                              -- 首页展示的根文档 ID (document.client_id)
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (tenant_id) REFERENCES tenants(id)
);

-- Create index on domain and sync_key for fast lookup
CREATE INDEX IF NOT EXISTS idx_vaults_domain ON vaults(domain);
CREATE INDEX IF NOT EXISTS idx_vaults_sync_key ON vaults(sync_key);

-- Documents (markdown files)
CREATE TABLE IF NOT EXISTS documents (
  id TEXT PRIMARY KEY,                  -- 服务器端 UUID
  tenant_id TEXT NOT NULL,
  vault_id TEXT NOT NULL,
  path TEXT NOT NULL,                   -- 文件路径（相对于 vault）
  client_id TEXT NOT NULL,              -- 客户端生成的唯一标识
  content TEXT,
  metadata TEXT,                        -- JSON 格式的元数据
  hash TEXT,
  mtime TIMESTAMP,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(vault_id, client_id),          -- 确保同一 vault 内 client_id 唯一
  FOREIGN KEY (tenant_id) REFERENCES tenants(id),
  FOREIGN KEY (vault_id) REFERENCES vaults(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_documents_vault ON documents(vault_id);
CREATE INDEX IF NOT EXISTS idx_documents_client_id ON documents(vault_id, client_id);
CREATE INDEX IF NOT EXISTS idx_documents_path ON documents(vault_id, path);
CREATE INDEX IF NOT EXISTS idx_documents_mtime ON documents(mtime);

-- Document links (双向链接关系)
CREATE TABLE IF NOT EXISTS document_links (
  id TEXT PRIMARY KEY,
  vault_id TEXT NOT NULL,
  source_client_id TEXT NOT NULL,    -- 源文档的 client_id
  target_client_id TEXT NOT NULL,    -- 目标文档的 client_id
  target_path TEXT,                  -- 目标路径（用于显示，可能不存在对应文档）
  link_type TEXT NOT NULL,           -- 链接类型: 'link' 或 'embed'
  display_text TEXT,                 -- 显示文本
  original TEXT,                     -- 原始链接文本
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (vault_id) REFERENCES vaults(id) ON DELETE CASCADE
);

-- 为链接查询创建索引
CREATE INDEX IF NOT EXISTS idx_links_source ON document_links(vault_id, source_client_id);
CREATE INDEX IF NOT EXISTS idx_links_target ON document_links(vault_id, target_client_id);
CREATE INDEX IF NOT EXISTS idx_links_both ON document_links(vault_id, source_client_id, target_client_id);
