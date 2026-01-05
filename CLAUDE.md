# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MarkdownBrain is a multi-tenant Markdown publishing platform that syncs Obsidian vaults to the web. The system consists of three main components:

1. **Backend Server** (Clojure): Multi-tenant web server with SQLite database
2. **Frontend** (HTMX + TailwindCSS + DaisyUI): Server-rendered HTML with dynamic interactions
3. **Obsidian Plugin** (TypeScript): Real-time file sync from Obsidian to the server

### Architecture Highlights

- **Multi-tenancy**: Each user/organization (tenant) can have multiple vaults, with strict data isolation
- **Domain-based routing**: Each vault can be accessed via its own domain (e.g., `blog.example.com`)
- **Dual authentication**: Session-based for admin, token-based for Obsidian sync
- **Document linking**: Tracks internal links between documents with client-id based resolution
- **File storage**: Markdown content stored directly in SQLite (no external file system)

## Development Commands

### Backend Development

```bash
# Start development server (http://localhost:3000)
make dev
# or
cd server && clojure -M:dev

# Start REPL without server
make backend-repl
# or
cd server && clojure -M:repl

# Run tests
make backend-test
# or
cd server && clojure -M:test

# Run specific test
cd server && clojure -X:test :patterns '["markdownbrain.db-test"]'

# Build uberjar
make backend-build
# or
cd server && clojure -T:build uberjar
```

### Frontend Development

```bash
# Watch mode for CSS changes
make frontend-dev
# or
cd server && npm run watch:css

# Build production CSS
make frontend-build
# or
cd server && npm run build:css

# Install frontend dependencies
cd server && npm install && npm run setup
```

### Obsidian Plugin Development

```bash
# Development mode (watches files, outputs to current directory)
make plugin-dev
# or
cd obsidian-plugin && npm run dev

# Production build (outputs to dist/)
make plugin-build
# or
cd obsidian-plugin && npm run build

# Package as zip for distribution
make plugin-package
# or
cd obsidian-plugin && npm run package
```

### Database Operations

```bash
# Reset database (deletes data/markdownbrain.db and reinitializes)
make db-reset

# Run migrations manually
make db-migrate
```

### Full Build

```bash
# Build everything (backend + frontend + plugin)
make build

# Clean all build artifacts
make clean

# Full release build
make release
```

## Code Architecture

### Backend Structure (server/src/markdownbrain/)

The Clojure backend follows a standard layered architecture:

- **core.clj**: Application entry point, Jetty server setup
- **config.clj**: Configuration management (port, host, database path, session settings)
- **db.clj**: Database layer with CRUD operations for tenants/users/vaults/documents
  - Uses `next.jdbc` with SQLite
  - Converts snake_case DB columns to kebab-case Clojure keywords
  - All tests use in-memory SQLite (`:memory:`) for isolation
- **routes.clj**: Reitit router configuration with three route groups:
  1. Admin routes (`/admin/*`) - require session auth
  2. Sync routes (`/obsidian/*`) - require Bearer token auth
  3. Frontend routes (`/`, `/documents/*`) - domain-based routing
- **middleware.clj**: Ring middleware stack
  - `wrap-auth`: Session-based authentication for admin routes
  - CORS, JSON parsing, error handling
- **utils.clj**: Helper functions (UUID generation, password hashing, DNS record formatting)
- **response.clj**: Standard HTTP response builders
- **link_diff.clj**: Document link diffing logic for tracking link changes

#### Handlers (server/src/markdownbrain/handlers/)

- **admin.clj**: Admin dashboard endpoints
  - System initialization (`POST /admin/init`)
  - Login/logout
  - Vault CRUD
  - Root document selection
- **sync.clj**: Obsidian plugin sync endpoints
  - `POST /obsidian/sync`: Receive file changes from plugin
  - `GET /obsidian/vault/info`: Validate sync token and return vault info
  - Authenticates via `Authorization: Bearer {sync_token}` header
- **frontend.clj**: Public web interface
  - Domain-based vault resolution via `Host` header
  - Document listing and retrieval
  - Renders Selmer templates from `server/resources/templates/`

### Frontend (server/resources/)

- **templates/**: Selmer HTML templates
  - `base.html`: Base layout with HTMX and TailwindCSS
  - `admin/`: Admin dashboard pages
  - `frontend/`: Public vault pages
  - `components/`: Reusable UI components
- **public/**: Static assets
  - `css/app.css`: Compiled TailwindCSS with DaisyUI
  - `js/htmx.min.js`: HTMX library
  - `js/helpers.js`: Utility functions (copyToClipboard, notifications, date formatting)

### Obsidian Plugin (obsidian-plugin/)

- **main.ts**: Single-file TypeScript plugin
  - `MarkdownBrainPlugin`: Main plugin class
  - `SyncManager`: Handles HTTP communication with server
  - `MarkdownBrainSettingTab`: Settings UI
  - File watcher: Monitors vault for create/modify/delete events
  - Metadata extraction: Parses links, embeds, tags, headings, frontmatter
  - Client ID generation: Each file gets a stable UUID for cross-reference

### Database Schema

Key tables (SQLite):
- **tenants**: Top-level isolation boundary
- **users**: Admin users (session auth)
- **vaults**: Tenant's Obsidian vaults (each has sync_token and optional domain)
- **documents**: Markdown files with content, metadata, client_id, and hash
  - `UNIQUE(vault_id, path)` constraint prevents duplicates
  - `ON DELETE CASCADE` for vault deletion

Migration: `server/resources/migrations/001-initial-schema.sql`

## Key Patterns & Conventions

### Multi-tenant Isolation

Every database query must filter by `tenant_id`. The authentication flow:

1. **Admin routes**: Session contains `:tenant-id` and `:user-id`
2. **Sync routes**: Bearer token lookup returns `vault-id` → query vault to get `tenant-id`
3. **Frontend routes**: Domain lookup returns `vault-id` → query vault to get `tenant-id`

Never trust user input for tenant/vault access - always derive from authenticated session or token.

### Testing

- 100% test coverage across all modules
- All tests in `server/test/markdownbrain/`
- Database tests use `:memory:` SQLite with `use-fixtures :each` for isolation
- HTTP tests use `ring.mock.request` to create test requests
- See `server/test/README.md` for comprehensive testing guide

### Error Handling

- Use `markdownbrain.response/error-response` for consistent error JSON
- Log errors with `clojure.tools.logging`
- Return appropriate HTTP status codes (401 for auth, 404 for not found, 500 for server errors)

### Document Sync Flow

1. Obsidian plugin watches vault for file changes
2. On change, extracts metadata (links, tags, headings) and generates client-id
3. POSTs to `/obsidian/sync` with action: `create|modify|delete`
4. Server validates sync_token, determines tenant-id, and upserts/deletes document
5. Link resolution: Internal links use client-ids to find target documents across renames

### Domain Routing

The server uses the `Host` header to route requests:

1. Extract domain from `Host` header (strip port if present)
2. Query `vaults` table: `SELECT * FROM vaults WHERE domain = ?`
3. If found, render vault's documents; otherwise show 404

For development: Use `/etc/hosts` or Nginx/Caddy to route domains to localhost.

## Common Tasks

### Adding a New Admin Endpoint

1. Add handler function in `server/src/markdownbrain/handlers/admin.clj`
2. Add route in `server/src/markdownbrain/routes.clj` under `/admin` with `:middleware [middleware/wrap-auth]`
3. Add test in `server/test/markdownbrain/handlers/admin_test.clj`
4. Create Selmer template if needed in `server/resources/templates/admin/`

### Adding a New Database Query

1. Add function in `server/src/markdownbrain/db.clj`
2. Use `execute!` for multiple rows or `execute-one!` for single row
3. Add test in `server/test/markdownbrain/db_test.clj` with `:memory:` database

### Modifying Plugin Behavior

1. Edit `obsidian-plugin/main.ts`
2. Test in dev mode: `npm run dev` (outputs to current directory)
3. Reload plugin in Obsidian vault at `../vault/test/.obsidian/plugins/markdownbrain/`
4. Build for production: `npm run build` (outputs to `dist/`)

## Important Notes

- **Chinese Comments**: Some code comments and docs are in Chinese (project is for Chinese-speaking users)
- **In-progress Features**: Check `TODO.md` for upcoming work items
- **DESIGN.md**: Contains detailed system design documentation in Chinese
- **Session Secret**: Generated automatically on first run, stored in config
- **Default Port**: Backend runs on port 3000 by default
- **SQLite Location**: Database file is `server/markdownbrain.db` (configurable in config.clj)
- 测试驱动开发
