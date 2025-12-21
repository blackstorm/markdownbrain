(ns markdownbrain-plugin.sync
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [goog.object :as gobj])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;; 全局配置
(def ^:private config (atom nil))

;; 初始化配置
(defn ^:export init [opts]
  (let [js-config (js->clj opts :keywordize-keys true)]
    (reset! config js-config)
    (println "MarkdownBrain sync initialized:" js-config)))

;; 销毁
(defn ^:export destroy []
  (reset! config nil))

;; 同步文件
(defn ^:export sync [file-data]
  (go
    (let [js-data (js->clj file-data :keywordize-keys true)
          {:keys [serverUrl vaultId syncToken]} @config]

      (if (and serverUrl vaultId syncToken)
        (let [url (str serverUrl "/api/sync")
              payload (merge js-data
                            {:vault_id vaultId
                             :sync_token syncToken})
              response (<! (http/post url
                                      {:json-params payload
                                       :headers {"Authorization" (str "Bearer " vaultId ":" syncToken)}}))]

          (if (:success response)
            (do
              (println "Sync success:" (:path js-data))
              (clj->js {:success true}))
            (do
              (println "Sync failed:" (:error-text response))
              (clj->js {:success false
                       :error (:error-text response)}))))

        (do
          (println "Config not initialized")
          (clj->js {:success false
                   :error "Plugin not configured"}))))))

;; 批量同步
(defn ^:export sync-batch [files-data]
  (go
    (let [files (js->clj files-data :keywordize-keys true)
          results (atom [])]

      (doseq [file files]
        (let [result (<! (sync (clj->js file)))]
          (swap! results conj result)))

      (clj->js @results))))
