(ns user
  "开发环境命名空间，提供热重载功能"
  (:require [nextjournal.beholder :as beholder]
            [clojure.tools.namespace.repl :as repl]
            [selmer.parser :as selmer]))

(def server (atom nil))
(def watcher (atom nil))

;; 防止 tools.namespace 重载此命名空间
(repl/disable-reload!)

(defn start-server
  "启动服务器"
  []
  (when @server
    (println "Stopping existing server...")
    ((:stop @server)))
  (println "Starting server...")
  ;; 动态 require 避免编译时依赖
  (require 'markdownbrain.core :reload)
  (let [core-ns (find-ns 'markdownbrain.core)
        start-fn (ns-resolve core-ns 'start-server)]
    (reset! server (start-fn))
    (println "Server started on http://localhost:8080")))

(defn stop-server
  "停止服务器"
  []
  (when @server
    (println "Stopping server...")
    ((:stop @server))
    (reset! server nil)
    (println "Server stopped")))

(defn restart-server
  "重启服务器"
  []
  (println "\n=== Restarting server ===")
  (stop-server)
  (println "Reloading namespaces...")
  (try
    (repl/refresh)
    (println "Namespaces reloaded successfully")
    (start-server)
    (catch Exception e
      (println "Error during reload:")
      (println (.getMessage e))
      (println "\nTrying to start server anyway...")
      (try
        (start-server)
        (catch Exception e2
          (println "Failed to start server:" (.getMessage e2)))))))

(defn watch-files
  "监听文件变化并自动重启"
  []
  (when @watcher
    (beholder/stop @watcher))

  (println "Starting file watcher...")
  (println "Watching:")
  (println "  - src/**/*.clj")
  (println "  - resources/**/*.html")

  (reset! watcher
          (beholder/watch
           (fn [{:keys [path type]}]
             (when (or (.endsWith (str path) ".clj")
                       (.endsWith (str path) ".html"))
               (println (format "\n>>> File changed: %s (%s)" path type))
               (restart-server)))
           "src"
           "resources/templates")))

(defn start
  "启动开发环境：服务器 + 文件监听"
  []
  (println "=== MarkdownBrain Development Mode ===")
  (println "Starting server and file watcher...")
  ;; 禁用 Selmer 模板缓存以支持热重载
  (selmer/cache-off!)
  (println "Selmer template caching: DISABLED (dev mode)")
  (start-server)
  (watch-files)
  (println "\nDevelopment server ready!")
  (println "- Server: http://localhost:8080")
  (println "- Hot reload: Enabled")
  (println "- Press Ctrl+C to stop"))

(defn stop
  "停止开发环境"
  []
  (when @watcher
    (beholder/stop @watcher)
    (reset! watcher nil))
  (stop-server)
  (println "Development environment stopped"))

;; 自动启动
(defn -main [& args]
  (start)
  ;; 保持进程运行
  @(promise))
