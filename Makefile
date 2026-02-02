.PHONY: \
	help \
	install backend-install assets-install plugin-install \
	dev backend-dev backend-repl assets-dev plugin-dev \
	build backend-build assets-build plugin-build plugin-package \
	test backend-test plugin-test \
	db-migrate db-pending db-create-migration db-reset \
	clean

help:
	@echo "MarkdownBrain - Developer commands"
	@echo ""
	@echo "Development:"
	@echo "  make dev                           Start backend + frontend/console watch (FRONTEND_PORT/CONSOLE_PORT)"
	@echo "  make backend-repl                  Start backend REPL (no server)"
	@echo "  make assets-dev                    Watch and rebuild Tailwind CSS (console + frontend)"
	@echo "  make plugin-dev                    Watch Obsidian plugin (vaults/test)"
	@echo ""
	@echo "Build:"
	@echo "  make build                         Build backend + CSS + plugin"
	@echo "  make backend-build                 Build backend uberjar"
	@echo "  make assets-build                  Build Tailwind CSS (console + frontend)"
	@echo "  make plugin-build                  Build Obsidian plugin to dist/"
	@echo "  make plugin-package                Package plugin zip (markdownbrain-plugin.zip)"
	@echo ""
	@echo "Test:"
	@echo "  make test                          Run backend + plugin tests"
	@echo "  make backend-test                  Run backend tests (clojure -M:test)"
	@echo "  make plugin-test                   Run plugin tests (pnpm test)"
	@echo ""
	@echo "Database:"
	@echo "  make db-migrate                    Run migrations (migratus)"
	@echo "  make db-pending                    List pending migrations"
	@echo "  make db-create-migration NAME=xxx  Create a new migration file"
	@echo "  make db-reset                      Delete local DB and rerun migrations"
	@echo ""
	@echo "Maintenance:"
	@echo "  make install                       Install backend + assets + plugin dependencies"
	@echo "  make clean                         Remove build outputs"
	@echo ""
	@echo "Notes:"
	@echo "  - Plugin tasks require pnpm (Node.js 25 does not ship Corepack)."
	@echo "    Install: npm install -g pnpm@10.17.1"

install: backend-install assets-install plugin-install

backend-install:
	@echo "Installing backend dependencies..."
	@cd server && clojure -P -M:dev:test

assets-install:
	@echo "Installing Tailwind CSS dependencies..."
	@cd server && npm install

plugin-install:
	@echo "Installing plugin dependencies..."
	@cd obsidian-plugin && pnpm install --frozen-lockfile

FRONTEND_PORT ?= 8080
CONSOLE_PORT ?= 9090

dev:
	@echo "Starting backend development server + asset watches..."
	@echo "Frontend Port: $(FRONTEND_PORT), Console Port: $(CONSOLE_PORT)"
	@echo "Use Ctrl+C to stop all processes"
	@set -e; \
	cd server; \
	FRONTEND_PORT=$(FRONTEND_PORT) CONSOLE_PORT=$(CONSOLE_PORT) MARKDOWNBRAIN_LOG_LEVEL=DEBUG clojure -M:dev & \
	BACKEND_PID=$$!; \
	npm run watch:console & \
	CONSOLE_WATCH_PID=$$!; \
	npm run watch:frontend & \
	FRONTEND_WATCH_PID=$$!; \
	trap 'kill $$BACKEND_PID $$CONSOLE_WATCH_PID $$FRONTEND_WATCH_PID || true' INT TERM; \
	wait $$BACKEND_PID $$CONSOLE_WATCH_PID $$FRONTEND_WATCH_PID

backend-dev:
	@echo "Starting backend development server..."
	@echo "Frontend Port: $(FRONTEND_PORT), Console Port: $(CONSOLE_PORT)"
	@cd server && FRONTEND_PORT=$(FRONTEND_PORT) CONSOLE_PORT=$(CONSOLE_PORT) MARKDOWNBRAIN_LOG_LEVEL=DEBUG clojure -M:dev

backend-repl:
	@echo "Starting backend REPL..."
	@cd server && clojure -M:repl

assets-dev:
	@echo "Starting Tailwind CSS watch mode..."
	@cd server && npm run watch

plugin-dev:
	@echo "Starting Obsidian plugin development mode..."
	@cd obsidian-plugin && pnpm dev

build: backend-build assets-build plugin-build

backend-build:
	@echo "Building backend uberjar..."
	@cd server && clojure -T:build uberjar
	@echo "Backend built: server/target/server-standalone.jar"

assets-build:
	@echo "Building Tailwind CSS..."
	@cd server && npm run build
	@echo "CSS built:"
	@echo "  - server/resources/publics/console/css/app.css"
	@echo "  - server/resources/publics/frontend/css/frontend.css"

plugin-build:
	@echo "Building Obsidian plugin..."
	@cd obsidian-plugin && pnpm build
	@echo "Plugin built: obsidian-plugin/dist/"

plugin-package:
	@echo "Packaging Obsidian plugin..."
	@cd obsidian-plugin && pnpm package
	@echo "Plugin packaged: obsidian-plugin/markdownbrain-plugin.zip"

test: backend-test plugin-test

backend-test:
	@echo "Running backend tests..."
	@cd server && clojure -M:test

plugin-test:
	@echo "Running plugin tests..."
	@cd obsidian-plugin && pnpm test

# Database
db-migrate:
	@echo "Running database migrations..."
	@cd server && clojure -M -m markdownbrain.migrations migrate

db-reset:
	@echo "Resetting database..."
	@rm -f data/markdownbrain.db data/.secrets.edn
	@cd server && clojure -M -m markdownbrain.migrations migrate
	@echo "Database reset complete"

db-pending:
	@echo "Checking pending migrations..."
	@cd server && clojure -M -m markdownbrain.migrations pending

db-create-migration:
	@echo "Creating new migration..."
	@cd server && clojure -M -m markdownbrain.migrations create $(NAME)

clean:
	@echo "Cleaning build artifacts..."
	@rm -rf server/target/
	@rm -rf server/.cpcache/
	@rm -rf obsidian-plugin/dist/
	@rm -f obsidian-plugin/main.js
	@rm -f obsidian-plugin/main.js.map
	@rm -f obsidian-plugin/*.zip
	@echo "Clean complete"
