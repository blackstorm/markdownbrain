(ns user
  "开发环境命名空间，提供热重载功能"
  (:require [nextjournal.beholder :as beholder]
            [clojure.tools.namespace.repl :as repl]
            [selmer.parser :as selmer]))

(def servers (atom nil))
(def watcher (atom nil))

;; 防止 tools.namespace 重载此命名空间
(repl/disable-reload!)

(defn start-servers
  "启动双服务器"
  []
  (when @servers
    (println "Stopping existing servers...")
    ((:stop @servers)))
  (println "Starting servers...")
  ;; 动态 require 避免编译时依赖
  (require 'mdbrain.core :reload)
  (let [core-ns (find-ns 'mdbrain.core)
        start-fn (ns-resolve core-ns 'start-servers)]
    (reset! servers (start-fn))))

(defn stop-servers
  "停止服务器"
  []
  (when @servers
    (println "Stopping servers...")
    ((:stop @servers))
    (reset! servers nil)
    (println "Servers stopped")))

(defn restart-servers
  "重启服务器"
  []
  (println "\n=== Restarting servers ===")
  (stop-servers)
  (println "Reloading namespaces...")
  (try
    (repl/refresh)
    (println "Namespaces reloaded successfully")
    (start-servers)
    (catch Exception e
      (println "Error during reload:")
      (println (.getMessage e))
      (println "\nTrying to start servers anyway...")
      (try
        (start-servers)
        (catch Exception e2
          (println "Failed to start servers:" (.getMessage e2)))))))

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
               (restart-servers)))
           "src"
           "resources/templates")))

(defn start
  "启动开发环境：双服务器 + 文件监听"
  []
  (println "=== Mdbrain Development Mode ===")
  (println "Starting servers and file watcher...")
  ;; 禁用 Selmer 模板缓存以支持热重载
  (selmer/cache-off!)
  (println "Selmer template caching: DISABLED (dev mode)")
  (start-servers)
  (watch-files)
  (println "\nDevelopment environment ready!")
  (println "- Hot reload: Enabled")
  (println "- Press Ctrl+C to stop"))

(defn stop
  "停止开发环境"
  []
  (when @watcher
    (beholder/stop @watcher)
    (reset! watcher nil))
  (stop-servers)
  (println "Development environment stopped"))

;; 自动启动
(defn -main [& args]
  (start)
  ;; 保持进程运行
  @(promise))
