.PHONY: help dev build test clean install backend-dev backend-build frontend-dev frontend-build plugin-dev plugin-build plugin-package

# 默认目标
help:
	@echo "MarkdownBrain Development Commands"
	@echo ""
	@echo "Development:"
	@echo "  make dev                              - 启动后端开发服务器（默认端口：8080/9090）"
	@echo "  make dev FRONTEND_PORT=3000           - 使用自定义端口启动"
	@echo "  make backend-dev                      - 启动后端开发服务器"
	@echo "  make backend-repl     - 启动后端 REPL（不启动服务器）"
	@echo "  make frontend-dev     - 启动前端 CSS watch 模式"
	@echo "  make plugin-dev       - 启动 Obsidian 插件开发模式"
	@echo ""
	@echo "Build:"
	@echo "  make build            - 构建所有项目（后端 + 前端 + 插件）"
	@echo "  make backend-build    - 构建后端 uberjar"
	@echo "  make frontend-build   - 构建前端 CSS"
	@echo "  make plugin-build     - 构建 Obsidian 插件到 dist/"
	@echo "  make plugin-package   - 打包插件为 zip 文件"
	@echo ""
	@echo "Test:"
	@echo "  make test             - 运行所有测试"
	@echo "  make backend-test     - 运行后端测试"
	@echo ""
	@echo "Maintenance:"
	@echo "  make install          - 安装所有依赖"
	@echo "  make clean                        - 清理构建产物"
	@echo "  make db-migrate                   - 运行数据库迁移 (migratus)"
	@echo "  make db-reset                     - 重置数据库"
	@echo "  make db-pending                   - 查看待执行的迁移"
	@echo "  make db-create-migration NAME=xxx - 创建新迁移文件"

# 安装依赖
install: backend-install frontend-install plugin-install

backend-install:
	@echo "Installing backend dependencies..."
	@cd server && clojure -P -M:dev:test

frontend-install:
	@echo "Installing frontend dependencies..."
	@cd server && npm install
	@cd server && npm run setup

plugin-install:
	@echo "Installing plugin dependencies..."
	@cd obsidian-plugin && npm install

# 开发模式
# 支持自定义端口: make dev FRONTEND_PORT=3000 CONSOLE_PORT=4000
FRONTEND_PORT ?= 4000
CONSOLE_PORT ?= 9090

dev:
	@echo "Starting backend development server..."
	@echo "Frontend Port: $(FRONTEND_PORT), Console Port: $(CONSOLE_PORT)"
	@echo "Use Ctrl+C to stop"
	@cd server && FRONTEND_PORT=$(FRONTEND_PORT) CONSOLE_PORT=$(CONSOLE_PORT) clojure -M:dev

backend-dev:
	@echo "Starting backend development server..."
	@echo "Frontend Port: $(FRONTEND_PORT), Console Port: $(CONSOLE_PORT)"
	@cd server && FRONTEND_PORT=$(FRONTEND_PORT) CONSOLE_PORT=$(CONSOLE_PORT) clojure -M:dev

backend-repl:
	@echo "Starting backend REPL..."
	@cd server && clojure -M:repl

frontend-dev:
	@echo "Starting frontend CSS watch mode..."
	@cd server && npm run watch:css

plugin-dev:
	@echo "Starting Obsidian plugin development mode..."
	@cd obsidian-plugin && npm run dev

# 构建
build: backend-build frontend-build plugin-build

backend-build:
	@echo "Building backend uberjar..."
	@cd server && clojure -T:build uberjar
	@echo "Backend built: server/target/server-standalone.jar"

frontend-build:
	@echo "Building frontend CSS..."
	@cd server && npm run build:css
	@echo "Frontend CSS built: server/resources/publics/frontend/css/frontend.css"

plugin-build:
	@echo "Building Obsidian plugin..."
	@cd obsidian-plugin && npm run build
	@echo "Plugin built: obsidian-plugin/dist/"

plugin-package:
	@echo "Packaging Obsidian plugin..."
	@cd obsidian-plugin && npm run package
	@echo "Plugin packaged: obsidian-plugin/markdownbrain-plugin.zip"

# 测试
test: backend-test

backend-test:
	@echo "Running backend tests..."
	@cd server && clojure -M:test

# 数据库操作
db-migrate:
	@echo "Running database migrations..."
	@cd server && clojure -M:dev -m markdownbrain.migrations migrate

db-reset:
	@echo "Resetting database..."
	@rm -f data/markdownbrain.db data/.secrets.edn
	@cd server && clojure -M:dev -m markdownbrain.migrations migrate
	@echo "Database reset complete"

db-pending:
	@echo "Checking pending migrations..."
	@cd server && clojure -M:dev -m markdownbrain.migrations pending

db-create-migration:
	@echo "Creating new migration..."
	@cd server && clojure -M:dev -m markdownbrain.migrations create $(NAME)

# 清理
clean:
	@echo "Cleaning build artifacts..."
	@rm -rf server/target/
	@rm -rf server/.cpcache/
	@rm -rf obsidian-plugin/dist/
	@rm -rf obsidian-plugin/node_modules/
	@rm -f obsidian-plugin/main.js
	@rm -f obsidian-plugin/main.js.map
	@rm -f obsidian-plugin/*.zip
	@echo "Clean complete"

# 快速启动（用于日常开发）
start: backend-dev

# 发布准备
release: clean install test build plugin-package
	@echo ""
	@echo "Release artifacts ready:"
	@echo "  - Backend: server/target/server-standalone.jar"
	@echo "  - Plugin: obsidian-plugin/markdownbrain-plugin.zip"
