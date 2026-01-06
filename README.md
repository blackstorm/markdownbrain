# MarkdownBrain

将 Obsidian 笔记同步发布为网站的多租户平台。支持自定义域名和自动 SSL 证书。

## 功能特性

- Obsidian 插件实时同步笔记到服务器
- 多租户架构，支持多个独立站点
- 自定义域名，自动获取 SSL 证书
- 内部链接自动解析
- 反向链接展示

## 快速开始

### 开发环境

```bash
# 安装依赖
make install

# 启动开发服务器
make dev

# Frontend: http://localhost:8080
# Admin: http://localhost:9090
```

### 其他开发命令

```bash
make frontend-dev    # CSS watch 模式
make plugin-dev      # Obsidian 插件开发模式
make test            # 运行测试
make build           # 构建所有项目
```

## 部署指南

### 前置条件

- Docker 和 Docker Compose
- 域名已解析到服务器 IP
- 服务器 80/443 端口可用

### 环境变量

| 变量 | 说明 | 必填 |
|------|------|------|
| `INTERNAL_TOKEN` | 内部 API 认证 Token (Caddy 域名验证用) | 是 |
| `SESSION_SECRET` | Session 加密密钥 | 否 (自动生成) |

### 部署步骤

1. 克隆项目

```bash
git clone https://github.com/example/markdownbrain.git
cd markdownbrain
```

2. 生成 Token

```bash
export INTERNAL_TOKEN=$(openssl rand -hex 32)
```

3. 启动服务

```bash
docker compose up -d
```

4. 查看日志

```bash
docker compose logs -f
```

### 访问 Admin 面板

Admin 端口 (9090) 仅绑定到 localhost，需要通过 SSH 隧道访问：

```bash
ssh -L 9090:localhost:9090 user@your-server
```

然后在本地浏览器访问 `http://localhost:9090/admin`

### 添加站点域名

1. 登录 Admin 面板
2. 创建新站点，填写域名（如 `notes.example.com`）
3. 将域名 DNS 解析到服务器 IP
4. Caddy 会自动为该域名获取 SSL 证书

## Obsidian 插件

### 安装

1. 从 Release 下载 `markdownbrain-plugin.zip`
2. 解压到 `.obsidian/plugins/markdownbrain/`
3. 在 Obsidian 设置中启用插件

### 配置

1. 在 Admin 面板创建站点，获取 Sync Key
2. 在插件设置中填写：
   - Server URL: `https://your-admin-domain.com`
   - Sync Key: 从 Admin 面板复制

### 同步

- 自动同步：文件修改后自动同步
- 手动同步：命令面板执行 `MarkdownBrain: Sync All`

## 架构

```
                    ┌─────────────┐
                    │   Caddy     │
                    │  (80/443)   │
                    └──────┬──────┘
                           │
              ┌────────────┴────────────┐
              │                         │
              ▼                         ▼
    ┌─────────────────┐      ┌─────────────────┐
    │  Frontend:8080  │      │  Admin:9090     │
    │  (公开访问)      │      │  (仅 localhost) │
    └─────────────────┘      └─────────────────┘
              │                         │
              └────────────┬────────────┘
                           │
                           ▼
                    ┌─────────────┐
                    │   SQLite    │
                    └─────────────┘
```
