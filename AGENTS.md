# Repository Guidelines

## Project Structure & Module Organization
- `server/`: Clojure backend (routes, handlers, middleware, DB, migrations).
  - `server/src/mdbrain/`: application code.
  - `server/test/mdbrain/`: backend tests.
- `server/resources/templates/`: Selmer templates for console/app.
  - `server/resources/publics/`: compiled CSS/JS and static assets.
  - `server/resources/migrations/`: SQL migrations.
- `obsidian-plugin/`: TypeScript Obsidian plugin (builds to `dist/`).
- `selfhosted/`: Docker Compose/Caddy configs for deployment.
- `vault/test/`: local test vault used by plugin dev mode.

## Build, Test, and Development Commands
- `make install`: install backend/app/plugin dependencies.
- `make dev`: start backend dev server (App/Console ports via `APP_PORT`/`CONSOLE_PORT`).
- `make assets-dev`: watch and rebuild Tailwind CSS.
- `make plugin-dev`: watch plugin, outputs to `vault/test/.obsidian/plugins/mdbrain`.
- `make build`: build backend uberjar, app CSS, and plugin.
- `make backend-test`: run backend tests (`clojure -M:test`).
- `cd obsidian-plugin && npm run test`: run plugin tests with Bun.

## Coding Style & Naming Conventions
- Clojure: follow existing idioms and indentation; namespaces use kebab-case (e.g., `mdbrain.link-parser`) and filenames use snake_case (e.g., `link_parser.clj`).
- TypeScript: keep formatting consistent with existing files; tests live in `src/__tests__/*.test.ts`.
- CSS: generated via Tailwind (`server/console.css`, `server/app.css`). Avoid editing compiled files in `server/resources/publics/` directly.

## Testing Guidelines
- Backend tests live under `server/test/mdbrain/`; naming is `*_test.clj`.
- App JS tests are in `server/test/app/test.html` (manual browser run, see `server/test/README.md`).
- Plugin tests use `bun test` via `npm run test`.

## Commit & Pull Request Guidelines
- Recent commits use conventional prefixes (`feat:`, `fix:`, `refactor:`) and short imperative summaries. Match that style when possible.
- PRs should include a clear description, link relevant issues, and note any DB migration changes.
- Include screenshots or screen recordings for console/app UI changes.

## Agent Notes
- `CLAUDE.md` documents architecture and deeper development workflows; read it when touching core backend behavior.
