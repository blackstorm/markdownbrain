# 前端代码示例 (ClojureScript + HTMX + TailwindCSS)

## 1. shadow-cljs.edn - ClojureScript 构建配置

```clojure
{:source-paths ["src"]

 :dependencies
 [[reagent "1.2.0"]
  [cljs-ajax "0.8.4"]]

 :builds
 {:frontend
  {:target :browser
   :output-dir "resources/public/js"
   :asset-path "/js"
   :modules {:app {:init-fn markdownbrain-frontend.core/init}}
   :devtools {:http-root "resources/public"
              :http-port 8020}}}}
```

## 2. package.json - NPM 依赖

```json
{
  "name": "markdownbrain-frontend",
  "version": "1.0.0",
  "scripts": {
    "watch": "shadow-cljs watch frontend",
    "build": "shadow-cljs release frontend",
    "tailwind:watch": "tailwindcss -i ./tailwind.css -o ./resources/public/css/app.css --watch",
    "tailwind:build": "tailwindcss -i ./tailwind.css -o ./resources/public/css/app.css --minify"
  },
  "devDependencies": {
    "shadow-cljs": "^2.26.2",
    "tailwindcss": "^3.4.0"
  },
  "dependencies": {
    "htmx.org": "^1.9.10"
  }
}
```

## 3. tailwind.config.js - TailwindCSS 配置

```javascript
module.exports = {
  content: [
    "./resources/templates/**/*.html",
    "./src/**/*.cljs"
  ],
  theme: {
    extend: {
      colors: {
        primary: {
          50: '#f0f9ff',
          500: '#0ea5e9',
          600: '#0284c7',
          700: '#0369a1',
        }
      }
    },
  },
  plugins: [],
}
```

## 4. tailwind.css - Tailwind 入口文件

```css
@tailwind base;
@tailwind components;
@tailwind utilities;

@layer components {
  .btn {
    @apply px-4 py-2 rounded-lg font-medium transition-colors;
  }

  .btn-primary {
    @apply bg-primary-600 text-white hover:bg-primary-700;
  }

  .btn-secondary {
    @apply bg-gray-200 text-gray-700 hover:bg-gray-300;
  }

  .input {
    @apply w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500;
  }

  .card {
    @apply bg-white rounded-lg shadow-md p-6;
  }
}
```

## 5. src/markdownbrain_frontend/core.cljs - ClojureScript 辅助函数

```clojure
(ns markdownbrain-frontend.core
  (:require [ajax.core :refer [GET POST]]
            [goog.dom :as gdom]))

;; HTMX 辅助函数
(defn htmx-get [url target]
  (.setAttribute (gdom/getElement target)
                 "hx-get" url))

(defn htmx-post [url target]
  (.setAttribute (gdom/getElement target)
                 "hx-post" url))

;; 格式化时间
(defn format-date [date-str]
  (when date-str
    (let [date (js/Date. date-str)]
      (.toLocaleDateString date "zh-CN"
                          #js {:year "numeric"
                               :month "2-digit"
                               :day "2-digit"
                               :hour "2-digit"
                               :minute "2-digit"}))))

;; 复制到剪贴板
(defn copy-to-clipboard [text]
  (when-let [clipboard (.-clipboard js/navigator)]
    (.writeText clipboard text)
    (js/alert "已复制到剪贴板")))

;; 显示通知
(defn show-notification [message type]
  (let [container (gdom/getElement "notification-container")
        notification (gdom/createElement "div")
        color (case type
                :success "bg-green-500"
                :error "bg-red-500"
                :info "bg-blue-500"
                "bg-gray-500")]
    (set! (.-className notification)
          (str "fixed top-4 right-4 px-6 py-3 rounded-lg text-white shadow-lg " color))
    (set! (.-textContent notification) message)
    (gdom/appendChild container notification)
    (js/setTimeout #(gdom/removeNode notification) 3000)))

;; 初始化
(defn ^:export init []
  (println "MarkdownBrain frontend initialized"))

;; 暴露给 HTML 使用的函数
(set! (.-copyToClipboard js/window) copy-to-clipboard)
(set! (.-showNotification js/window) show-notification)
```

## 6. resources/templates/base.html - 基础模板

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>{% block title %}MarkdownBrain{% endblock %}</title>
    <link rel="stylesheet" href="/css/app.css">
    <script src="/js/htmx.min.js"></script>
    <script src="/js/app.js"></script>
</head>
<body class="bg-gray-50 min-h-screen">
    <div id="notification-container"></div>

    {% block content %}{% endblock %}
</body>
</html>
```

## 7. resources/templates/admin/login.html - 登录页面

```html
{% extends "base.html" %}

{% block title %}管理员登录 - MarkdownBrain{% endblock %}

{% block content %}
<div class="min-h-screen flex items-center justify-center">
    <div class="card max-w-md w-full">
        <h1 class="text-2xl font-bold text-gray-900 mb-6">管理员登录</h1>

        <form hx-post="/api/admin/login"
              hx-target="#login-result"
              hx-swap="innerHTML"
              class="space-y-4">

            <div>
                <label for="username" class="block text-sm font-medium text-gray-700 mb-1">
                    用户名
                </label>
                <input type="text"
                       id="username"
                       name="username"
                       required
                       class="input"
                       placeholder="请输入用户名">
            </div>

            <div>
                <label for="password" class="block text-sm font-medium text-gray-700 mb-1">
                    密码
                </label>
                <input type="password"
                       id="password"
                       name="password"
                       required
                       class="input"
                       placeholder="请输入密码">
            </div>

            <button type="submit" class="btn btn-primary w-full">
                登录
            </button>
        </form>

        <div id="login-result" class="mt-4"></div>
    </div>
</div>

<script>
// HTMX 成功后重定向
document.body.addEventListener('htmx:afterRequest', function(evt) {
    if (evt.detail.successful && evt.detail.target.id === 'login-result') {
        const response = JSON.parse(evt.detail.xhr.responseText);
        if (response.success) {
            window.location.href = '/admin';
        }
    }
});
</script>
{% endblock %}
```

## 8. resources/templates/admin/vaults.html - Vault 管理页面

```html
{% extends "base.html" %}

{% block title %}Vault 管理 - MarkdownBrain{% endblock %}

{% block content %}
<div class="container mx-auto px-4 py-8 max-w-6xl">
    <div class="flex justify-between items-center mb-8">
        <h1 class="text-3xl font-bold text-gray-900">Vault 管理</h1>
        <button onclick="document.getElementById('create-modal').classList.remove('hidden')"
                class="btn btn-primary">
            + 创建 Vault
        </button>
    </div>

    <!-- Vault 列表 -->
    <div hx-get="/api/admin/vaults"
         hx-trigger="load"
         hx-target="#vault-list"
         hx-swap="innerHTML">
        <div id="vault-list">
            <p class="text-gray-500">加载中...</p>
        </div>
    </div>

    <!-- 创建 Vault 模态框 -->
    <div id="create-modal" class="hidden fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center">
        <div class="card max-w-lg w-full">
            <div class="flex justify-between items-center mb-4">
                <h2 class="text-xl font-bold">创建新 Vault</h2>
                <button onclick="document.getElementById('create-modal').classList.add('hidden')"
                        class="text-gray-500 hover:text-gray-700">
                    ✕
                </button>
            </div>

            <form hx-post="/api/admin/vaults"
                  hx-target="#create-result"
                  hx-swap="innerHTML"
                  class="space-y-4">

                <div>
                    <label for="name" class="block text-sm font-medium text-gray-700 mb-1">
                        Vault 名称
                    </label>
                    <input type="text"
                           id="name"
                           name="name"
                           required
                           class="input"
                           placeholder="例如: 我的博客">
                </div>

                <div>
                    <label for="domain" class="block text-sm font-medium text-gray-700 mb-1">
                        域名
                    </label>
                    <input type="text"
                           id="domain"
                           name="domain"
                           required
                           class="input"
                           placeholder="例如: blog.example.com">
                </div>

                <button type="submit" class="btn btn-primary w-full">
                    创建
                </button>
            </form>

            <div id="create-result" class="mt-4"></div>
        </div>
    </div>
</div>

<script>
// Vault 列表模板
document.body.addEventListener('htmx:afterRequest', function(evt) {
    if (evt.detail.target.id === 'vault-list') {
        const vaults = JSON.parse(evt.detail.xhr.responseText);
        const html = vaults.map(vault => `
            <div class="card mb-4">
                <div class="flex justify-between items-start">
                    <div class="flex-1">
                        <h3 class="text-lg font-semibold text-gray-900">${vault.name}</h3>
                        <p class="text-sm text-gray-600 mt-1">
                            <strong>域名:</strong> ${vault.domain}
                        </p>
                        <p class="text-sm text-gray-600 mt-1">
                            <strong>Vault ID:</strong>
                            <code class="bg-gray-100 px-2 py-1 rounded">${vault.id}</code>
                        </p>
                        <p class="text-sm text-gray-600 mt-1">
                            <strong>Sync Token:</strong>
                            <code class="bg-gray-100 px-2 py-1 rounded">${vault.sync_token}</code>
                            <button onclick="copyToClipboard('${vault.sync_token}')"
                                    class="text-primary-600 hover:text-primary-700 ml-2">
                                复制
                            </button>
                        </p>
                    </div>
                </div>

                <div class="mt-4 p-4 bg-gray-50 rounded">
                    <h4 class="font-semibold text-gray-700 mb-2">DNS 配置信息：</h4>
                    <pre class="text-xs text-gray-600 whitespace-pre-wrap">${vault.domain_record}</pre>
                </div>
            </div>
        `).join('');
        evt.detail.target.innerHTML = html || '<p class="text-gray-500">暂无 Vault</p>';
    }

    // 创建成功后刷新列表
    if (evt.detail.target.id === 'create-result' && evt.detail.successful) {
        const response = JSON.parse(evt.detail.xhr.responseText);
        if (response.success) {
            document.getElementById('create-modal').classList.add('hidden');
            htmx.trigger('#vault-list', 'load');
        }
    }
});
</script>
{% endblock %}
```

## 9. resources/templates/frontend/home.html - Vault 前端首页

```html
{% extends "base.html" %}

{% block title %}{{ vault.name }} - MarkdownBrain{% endblock %}

{% block content %}
<div class="container mx-auto px-4 py-8 max-w-4xl">
    <header class="mb-8">
        <h1 class="text-4xl font-bold text-gray-900">{{ vault.name }}</h1>
        <p class="text-gray-600 mt-2">{{ vault.domain }}</p>
    </header>

    <div class="card">
        <h2 class="text-2xl font-semibold text-gray-900 mb-4">文档列表</h2>

        {% if documents %}
        <div class="space-y-2">
            {% for doc in documents %}
            <a href="/documents/{{ doc.id }}"
               class="block p-4 hover:bg-gray-50 rounded-lg transition-colors border border-gray-200">
                <div class="flex justify-between items-center">
                    <div>
                        <h3 class="font-medium text-gray-900">{{ doc.path }}</h3>
                        <p class="text-sm text-gray-500 mt-1">
                            最后修改: {{ doc.mtime }}
                        </p>
                    </div>
                    <svg class="w-5 h-5 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7"/>
                    </svg>
                </div>
            </a>
            {% endfor %}
        </div>
        {% else %}
        <p class="text-gray-500 text-center py-8">
            暂无文档。请使用 Obsidian 插件同步您的笔记。
        </p>
        {% endif %}
    </div>
</div>
{% endblock %}
```

## 10. 构建和运行

```bash
# 安装 NPM 依赖
npm install

# 开发模式（自动重新编译）
npm run watch         # ClojureScript
npm run tailwind:watch  # TailwindCSS

# 生产构建
npm run build
npm run tailwind:build

# 复制 HTMX
cp node_modules/htmx.org/dist/htmx.min.js resources/public/js/
```

## 11. 目录结构检查

确保以下目录存在：

```bash
mkdir -p resources/public/css
mkdir -p resources/public/js
mkdir -p resources/templates/admin
mkdir -p resources/templates/frontend
```
