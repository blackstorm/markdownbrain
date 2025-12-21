# 数据库迁移和部署指南

## 1. resources/migrations/001-initial-schema.sql

完整的数据库初始化脚本（已在 BACKEND_CODE.md 中提供）

## 2. 部署步骤

### 2.1 开发环境部署

```bash
# 1. 克隆项目
git clone <your-repo>
cd markdownbrain

# 2. 后端开发
clj -P  # 下载 Clojure 依赖

# 初始化数据库
clj -M -m markdownbrain.db/init-db!

# 启动服务器
clj -M -m markdownbrain.core

# 3. 前端开发（另开终端）
npm install
npm run watch          # ClojureScript
npm run tailwind:watch # TailwindCSS

# 复制 HTMX
cp node_modules/htmx.org/dist/htmx.min.js resources/public/js/

# 4. Obsidian 插件开发
cd obsidian-plugin
npm install
npm run dev
```

### 2.2 生产环境部署

```bash
# 1. 构建前端
npm run build
npm run tailwind:build
cp node_modules/htmx.org/dist/htmx.min.js resources/public/js/

# 2. 构建 Obsidian 插件
cd obsidian-plugin
npm run build

# 3. 使用 Uberjar 打包后端（可选）
clj -T:build uber

# 4. 运行
java -jar target/markdownbrain.jar
# 或
clj -M -m markdownbrain.core
```

## 3. Nginx 反向代理配置

### 3.1 完整 Nginx 配置

```nginx
# /etc/nginx/sites-available/markdownbrain

# 管理后台
server {
    listen 80;
    server_name admin.yourdomain.com;

    # 强制 HTTPS
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl http2;
    server_name admin.yourdomain.com;

    ssl_certificate /etc/letsencrypt/live/admin.yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/admin.yourdomain.com/privkey.pem;

    location / {
        proxy_pass http://localhost:3000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}

# 用户 Vault 泛域名
server {
    listen 80;
    server_name *.yourdomain.com;

    # 排除 admin 子域名
    if ($host = admin.yourdomain.com) {
        return 404;
    }

    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name *.yourdomain.com;

    # 泛域名证书
    ssl_certificate /etc/letsencrypt/live/yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/yourdomain.com/privkey.pem;

    # 排除 admin
    if ($host = admin.yourdomain.com) {
        return 404;
    }

    location / {
        proxy_pass http://localhost:3000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

### 3.2 启用配置

```bash
# 创建软链接
sudo ln -s /etc/nginx/sites-available/markdownbrain /etc/nginx/sites-enabled/

# 测试配置
sudo nginx -t

# 重载 Nginx
sudo systemctl reload nginx
```

## 4. SSL 证书配置（Let's Encrypt）

```bash
# 安装 Certbot
sudo apt install certbot python3-certbot-nginx

# 申请管理后台证书
sudo certbot --nginx -d admin.yourdomain.com

# 申请泛域名证书（需要 DNS 验证）
sudo certbot certonly --manual --preferred-challenges=dns \
  -d yourdomain.com -d "*.yourdomain.com"

# 按照提示添加 TXT 记录到 DNS
# 记录名: _acme-challenge.yourdomain.com
# 记录类型: TXT
# 记录值: <certbot 给出的值>

# 自动续期
sudo certbot renew --dry-run
```

## 5. Systemd 服务配置

### 5.1 创建服务文件

```ini
# /etc/systemd/system/markdownbrain.service

[Unit]
Description=MarkdownBrain Server
After=network.target

[Service]
Type=simple
User=www-data
WorkingDirectory=/var/www/markdownbrain
ExecStart=/usr/bin/clojure -M -m markdownbrain.core
Restart=on-failure
RestartSec=10

Environment="DB_PATH=/var/www/markdownbrain/data/markdownbrain.db"
Environment="SESSION_SECRET=your-secret-key-here"
Environment="SERVER_IP=123.45.67.89"
Environment="PORT=3000"

[Install]
WantedBy=multi-user.target
```

### 5.2 启动服务

```bash
# 重载 systemd
sudo systemctl daemon-reload

# 启动服务
sudo systemctl start markdownbrain

# 开机自启动
sudo systemctl enable markdownbrain

# 查看状态
sudo systemctl status markdownbrain

# 查看日志
sudo journalctl -u markdownbrain -f
```

## 6. 数据库备份

### 6.1 自动备份脚本

```bash
#!/bin/bash
# /usr/local/bin/backup-markdownbrain.sh

BACKUP_DIR="/var/backups/markdownbrain"
DB_PATH="/var/www/markdownbrain/data/markdownbrain.db"
DATE=$(date +%Y%m%d_%H%M%S)

mkdir -p $BACKUP_DIR

# 备份数据库
sqlite3 $DB_PATH ".backup '$BACKUP_DIR/markdownbrain_$DATE.db'"

# 压缩
gzip "$BACKUP_DIR/markdownbrain_$DATE.db"

# 删除 30 天前的备份
find $BACKUP_DIR -name "*.gz" -mtime +30 -delete

echo "Backup completed: markdownbrain_$DATE.db.gz"
```

### 6.2 添加到 Cron

```bash
# 编辑 crontab
sudo crontab -e

# 每天凌晨 2 点备份
0 2 * * * /usr/local/bin/backup-markdownbrain.sh >> /var/log/markdownbrain-backup.log 2>&1
```

## 7. 监控和日志

### 7.1 日志配置

```clojure
;; src/markdownbrain/core.clj
(ns markdownbrain.core
  (:require [clojure.tools.logging :as log]))

;; 使用 log 代替 println
(log/info "Server started on port" port)
(log/error "Database error:" error)
```

### 7.2 配置 Logback

```xml
<!-- resources/logback.xml -->
<configuration>
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>/var/log/markdownbrain/app.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>/var/log/markdownbrain/app.%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="FILE" />
    </root>
</configuration>
```

## 8. 安全加固

### 8.1 防火墙配置

```bash
# 只允许 80, 443, 22 端口
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
```

### 8.2 数据库文件权限

```bash
sudo chown www-data:www-data /var/www/markdownbrain/data/markdownbrain.db
sudo chmod 600 /var/www/markdownbrain/data/markdownbrain.db
```

### 8.3 环境变量安全

不要在代码中硬编码密钥，使用环境变量：

```bash
# /etc/environment
SESSION_SECRET=your-random-secret-key-here
```

## 9. 性能优化

### 9.1 SQLite 优化

```clojure
;; src/markdownbrain/db.clj
(defn init-db! []
  (execute! ["PRAGMA journal_mode=WAL"])
  (execute! ["PRAGMA synchronous=NORMAL"])
  (execute! ["PRAGMA cache_size=-64000"]) ; 64MB cache
  (execute! ["PRAGMA temp_store=MEMORY"])
  ;; 然后执行 schema
  )
```

### 9.2 Nginx 缓存

```nginx
# 静态文件缓存
location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg)$ {
    expires 1y;
    add_header Cache-Control "public, immutable";
}
```

## 10. 初始化管理员

```bash
# 方法 1: 使用 curl
curl -X POST http://localhost:3000/api/admin/init \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "your-secure-password",
    "tenant_name": "My Organization"
  }'

# 方法 2: 使用 Clojure REPL
clj
(require '[markdownbrain.db :as db])
(require '[markdownbrain.utils :as utils])
(db/init-db!)
(let [tenant-id (utils/generate-uuid)
      user-id (utils/generate-uuid)]
  (db/create-tenant! tenant-id "My Organization")
  (db/create-user! user-id tenant-id "admin" (utils/hash-password "password")))
```

## 11. 故障排查

### 常见问题

1. **数据库锁定**: 使用 WAL 模式（已在优化中配置）
2. **Session 不持久**: 检查 cookie 配置和 HTTPS
3. **CORS 错误**: 确保中间件正确配置
4. **插件无法连接**: 检查防火墙和 HTTPS 证书

### 日志查看

```bash
# Nginx 错误日志
sudo tail -f /var/log/nginx/error.log

# 应用日志
sudo journalctl -u markdownbrain -f

# SQLite 检查
sqlite3 /var/www/markdownbrain/data/markdownbrain.db "PRAGMA integrity_check;"
```
