# Repository Guidelines

## Project Structure & Module Organization
- `server/`: Clojure backend (routes, handlers, middleware, DB, migrations).
  - `server/src/markdownbrain/`: application code.
  - `server/test/markdownbrain/`: backend tests.
  - `server/resources/templates/`: Selmer templates for console/frontend.
  - `server/resources/publics/`: compiled CSS/JS and static assets.
  - `server/resources/migrations/`: SQL migrations.
- `obsidian-plugin/`: TypeScript Obsidian plugin (builds to `dist/`).
- `selfhosted/`: Docker Compose/Caddy configs for deployment.
- `vault/test/`: local test vault used by plugin dev mode.

## Build, Test, and Development Commands
- `make install`: install backend/frontend/plugin dependencies.
- `make dev`: start backend dev server (Frontend/Console ports via `FRONTEND_PORT`/`CONSOLE_PORT`).
- `make frontend-dev`: watch and rebuild Tailwind CSS.
- `make plugin-dev`: watch plugin, outputs to `vault/test/.obsidian/plugins/markdownbrain`.
- `make build`: build backend uberjar, frontend CSS, and plugin.
- `make backend-test`: run backend tests (`clojure -M:test`).
- `cd obsidian-plugin && npm run test`: run plugin tests with Bun.

## Coding Style & Naming Conventions
- Clojure: follow existing idioms and indentation; namespaces use kebab-case (e.g., `markdownbrain.link-parser`) and filenames use snake_case (e.g., `link_parser.clj`).
- TypeScript: keep formatting consistent with existing files; tests live in `src/__tests__/*.test.ts`.
- CSS: generated via Tailwind (`server/console.css`, `server/frontend.css`). Avoid editing compiled files in `server/resources/publics/` directly.

## Testing Guidelines
- Backend tests live under `server/test/markdownbrain/`; naming is `*_test.clj`.
- Frontend JS tests are in `server/test/frontend/test.html` (manual browser run, see `server/test/README.md`).
- Plugin tests use `bun test` via `npm run test`.

## Commit & Pull Request Guidelines
- Recent commits use conventional prefixes (`feat:`, `fix:`, `refactor:`) and short imperative summaries. Match that style when possible.
- PRs should include a clear description, link relevant issues, and note any DB migration changes.
- Include screenshots or screen recordings for console/frontend UI changes.

## Agent Notes
- `CLAUDE.md` documents architecture and deeper development workflows; read it when touching core backend behavior.
