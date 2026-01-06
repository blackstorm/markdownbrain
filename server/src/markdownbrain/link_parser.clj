(ns markdownbrain.link-parser
  "服务端链接解析器 - 统一解析 Obsidian [[link]] 语法
   
   职责：
   1. extract-links: 从 markdown 内容中提取所有 [[link]] 和 ![[embed]]
   2. resolve-links: 通过 path 匹配找到 target_client_id
   3. deduplicate-by-target: 去重（同一目标只保留一个链接）
   
   设计原则：
   - 纯函数，无副作用
   - 每个函数只做一件事
    - 输出格式可直接写入 note_links 表"
  (:require [clojure.string :as str]))

;;; ============================================================
;;; 1. 提取链接
;;; ============================================================

(defn- parse-link-inner
  "解析 [[...]] 内部的内容
   
   输入: 'path#anchor|display' 或 'path|display' 或 'path#anchor' 或 'path'
   输出: {:path :display :anchor}"
  [inner]
  (let [;; 先按 | 分割获取显示文本
        [path-anchor-part display-part] (str/split inner #"\|" 2)
        ;; 再按 # 分割获取锚点
        [path anchor] (str/split path-anchor-part #"#" 2)
        ;; 显示文本：优先用 | 后面的，否则用完整的 path-anchor-part
        display (or display-part path-anchor-part)]
    {:path (str/trim path)
     :display (str/trim display)
     :anchor (when anchor (str/trim anchor))}))

(defn extract-links
  "从 markdown 内容中提取所有 [[link]] 和 ![[embed]]
   
   输入: markdown 字符串
   输出: [{:original \"[[Note A]]\"
           :path \"Note A\"
           :display \"Note A\"
           :anchor nil
           :link-type \"link\"} ...]
   
   支持的语法:
   - [[filename]]
   - [[filename|display text]]
   - [[filename#heading]]
   - [[filename#heading|display]]
   - ![[image.png]] (embed)"
  [content]
  (if (or (nil? content) (str/blank? content))
    []
    (let [;; 匹配 [[...]] 和 ![[...]]
          pattern #"(!?)\[\[([^\]]+)\]\]"
          matches (re-seq pattern content)]
      (mapv (fn [[original is-embed inner]]
              (let [parsed (parse-link-inner inner)]
                {:original original
                 :path (:path parsed)
                 :display (:display parsed)
                 :anchor (:anchor parsed)
                 :link-type (if (= is-embed "!") "embed" "link")}))
            matches))))

;;; ============================================================
;;; 2. 解析链接到 target_client_id
;;; ============================================================

(defn- normalize-path
  "规范化路径用于匹配
   - 移除 .md 扩展名
   - 转小写
   
   示例: 'Folder/Note A.md' -> 'folder/note a'"
  [path]
  (-> path
      str/trim
      (str/replace #"\.md$" "")
      str/lower-case))

(defn- extract-filename
  "从路径中提取文件名（不含目录）
   
   示例: 'folder/subfolder/Note.md' -> 'note'"
  [path]
  (-> path
      normalize-path
      (str/split #"/")
      last))

(defn- build-note-index
  [notes]
  {:by-full-path (into {} (map (fn [note]
                                 [(normalize-path (:path note)) note])
                               notes))
   :by-filename (into {} (map (fn [note]
                                [(extract-filename (:path note)) note])
                              notes))})

(defn- find-note
  "查找文档：先精确匹配完整路径，再按文件名匹配（Obsidian 风格）"
  [path note-index]
  (let [normalized (normalize-path path)
        filename (extract-filename path)]
    (or
     (get-in note-index [:by-full-path normalized])
     (get-in note-index [:by-filename filename]))))

(defn resolve-links
  "将提取的链接解析到 target_client_id
   
   输入: 
   - links: extract-links 的输出
    - notes: vault 中的笔记列表 [{:client-id :path} ...]
   
   输出: [{:target-client-id \"...\" 或 nil
           :target-path \"...\"
           :link-type \"link\"/\"embed\"
           :display-text \"...\"
           :original \"[[...]]\"} ...]"
  [links notes]
  (let [note-index (build-note-index notes)]
    (mapv (fn [link]
            (let [target-note (find-note (:path link) note-index)]
              {:target-client-id (:client-id target-note)
               :target-path (:path link)
               :link-type (:link-type link)
               :display-text (:display link)
               :original (:original link)}))
          links)))

;;; ============================================================
;;; 3. 去重
;;; ============================================================

(defn deduplicate-by-target
  "按 target_client_id 去重，保留第一个
   
   用途：同一个文档被多次链接（不同别名）时，只记录一条"
  [resolved-links]
  (->> resolved-links
       (reduce (fn [acc link]
                 (let [key (or (:target-client-id link) (:target-path link))]
                   (if (contains? (:seen acc) key)
                     acc
                     (-> acc
                         (update :seen conj key)
                         (update :result conj link)))))
               {:seen #{} :result []})
       :result))
