(ns markdownbrain.markdown
  "Markdown rendering with Obsidian syntax support
   采用第一性原理：每个函数只做一件事，可独立测试"
  (:require [markdown.core :as markdown]
            [clojure.string :as str]))

;;; ============================================================
;;; 1. 基础 Markdown 转换
;;; ============================================================

(defn md->html
  "将 Markdown 转换为 HTML（基础版本）
   输入: Markdown 字符串
   输出: HTML 字符串"
  [md-str]
  (when md-str
    (-> md-str
        str/trim
        (markdown/md-to-html-string))))

;;; ============================================================
;;; 2. Obsidian 链接解析
;;; ============================================================

(defn parse-obsidian-link
  "解析单个 Obsidian 链接
   输入: 链接字符串，如 '[[filename]]' 或 '![[image.png]]'
   输出: {:type :link/:embed
          :embed? true/false
          :path \"文件路径\"
          :display \"显示文本\"
          :anchor \"锚点\" (可选)}

   示例:
   [[filename]] -> {:type :link :embed? false :path \"filename\" :display \"filename\" :anchor nil}
   [[file|text]] -> {:type :link :embed? false :path \"file\" :display \"text\" :anchor nil}
   [[file#head]] -> {:type :link :embed? false :path \"file\" :display \"file#head\" :anchor \"head\"}
   ![[image]] -> {:type :embed :embed? true :path \"image\" :display \"image\" :anchor nil}"
  [link-str]
  (when link-str
    (let [;; 检查是否是嵌入 ![[...]]
          embed? (str/starts-with? link-str "!")
          ;; 提取 [[ 和 ]] 之间的内容
          inner (-> link-str
                    (str/replace #"^!?\[\[" "")
                    (str/replace #"\]\]$" ""))
          ;; 分割 | 获取路径和显示文本
          [path-part display-part] (str/split inner #"\|" 2)
          ;; 分割 # 获取文件和锚点
          [path anchor] (str/split path-part #"#" 2)
          ;; 显示文本（优先使用自定义显示文本，否则使用完整路径部分）
          display-text (or display-part path-part)]
      {:type (if embed? :embed :link)
       :embed? embed?
       :path (str/trim path)
       :display (str/trim display-text)
       :anchor (when anchor (str/trim anchor))})))

;;; ============================================================
;;; 3. 数学公式处理
;;; ============================================================

(defn extract-math
  "从内容中提取数学公式，替换为占位符
   输入: 包含 LaTeX 的字符串
   输出: {:content \"替换后的内容\"
          :formulas [{:type :inline/:block :formula \"公式内容\"}...]}

   支持:
   - 行内公式: $x^2$
   - 块级公式: $$E = mc^2$$"
  [content]
  (let [formulas (atom [])
        ;; 1. 先提取块级公式 $$...$$
        content-1 (str/replace content
                               #"\$\$([^\$]+?)\$\$"
                               (fn [[_ formula]]
                                 (let [idx (count @formulas)]
                                   (swap! formulas conj {:type :block :formula (str/trim formula)})
                                   (str "___MATH_" idx "___"))))
        ;; 2. 再提取行内公式 $...$
        content-2 (str/replace content-1
                               #"(?<!\$)\$(?!\$)([^\$\n]+?)\$(?!\$)"
                               (fn [[_ formula]]
                                 (let [idx (count @formulas)]
                                   (swap! formulas conj {:type :inline :formula (str/trim formula)})
                                   (str "___MATH_" idx "___"))))]
    {:content content-2
     :formulas @formulas}))

(defn restore-math
  "将占位符还原为 KaTeX 标记
   输入: HTML 字符串和公式列表
   输出: 替换后的 HTML

   占位符格式: ___MATH_0___, ___MATH_1___, ...
   输出格式:
   - 行内: <span class=\"math-inline\">formula</span>
   - 块级: <div class=\"math-block\">formula</div>"
  [html formulas]
  (reduce-kv
   (fn [result idx {:keys [type formula]}]
     (let [placeholder (str "___MATH_" idx "___")
           replacement (if (= type :inline)
                         (format "<span class=\"math-inline\">%s</span>" formula)
                         (format "<div class=\"math-block\">%s</div>" formula))]
       (str/replace result placeholder replacement)))
   html
   (vec formulas)))

;;; ============================================================
;;; 4. 提取元数据
;;; ============================================================

(defn extract-title
  "从 Markdown 内容中提取标题（第一个 # 开头的行）
   输入: Markdown 字符串
   输出: 标题字符串或 nil"
  [content]
  (when content
    (let [lines (str/split-lines content)
          title-line (first (filter #(str/starts-with? % "#") lines))]
      (when title-line
        (-> title-line
            (str/replace #"^#+\s+" "")
            str/trim)))))

;;; ============================================================
;;; 5. Obsidian 链接替换
;;; ============================================================

(defn- normalize-path
  "规范化文件路径：移除 .md 扩展名，转小写
   示例: 'Note A.md' -> 'note a'"
  [path]
  (-> path
      str/trim
      (str/replace #"\.md$" "")
      str/lower-case))

(defn- build-path-index
  "从文档列表构建路径到文档的索引
   输入: 文档列表
   输出: {\"normalized-path\" {:id \"...\" :client-id \"...\" ...}}"
  [documents]
  (into {}
        (map (fn [doc]
               [(normalize-path (:path doc)) doc])
             documents)))

(defn- find-document-by-path
  "通过路径查找文档
   输入: 路径字符串和路径索引
   输出: 文档 map 或 nil"
  [path path-index]
  (get path-index (normalize-path path)))

(defn- render-internal-link
  "渲染内部链接为 HTML
   输入: 目标文档、显示文本、可选锚点
   输出: HTML 字符串"
  [target-doc display-text anchor]
  (let [href (if anchor
               (format "/%s#%s" (:id target-doc) anchor)
               (format "/%s" (:id target-doc)))]
    (format "<a href=\"%s\" class=\"internal-link\" data-doc-id=\"%s\">%s</a>"
            href
            (:id target-doc)
            (str/escape display-text {\< "&lt;" \> "&gt;" \" "&quot;"}))))

(defn- render-broken-link
  "渲染不存在的链接为 HTML
   输入: 路径、显示文本
   输出: HTML 字符串"
  [path display-text]
  (format "<span class=\"internal-link broken\" title=\"文档不存在: %s\">%s</span>"
          (str/escape path {\< "&lt;" \> "&gt;" \" "&quot;"})
          (str/escape display-text {\< "&lt;" \> "&gt;" \" "&quot;"})))

(defn- render-image-embed
  "渲染图片嵌入为 HTML
   输入: 目标文档、显示文本
   输出: HTML 字符串"
  [target-doc display-text]
  (format "<img src=\"/documents/%s/content\" alt=\"%s\" class=\"obsidian-embed\">"
          (:id target-doc)
          (str/escape display-text {\< "&lt;" \> "&gt;" \" "&quot;"})))

(defn replace-obsidian-links
  "替换文本中的所有 Obsidian 链接为 HTML 链接
   输入: 内容字符串、文档列表
   输出: 替换后的字符串

   处理:
   - [[链接]] -> 内部链接
   - [[链接|文本]] -> 带自定义文本的链接
   - [[链接#锚点]] -> 带锚点的链接
   - ![[图片]] -> 图片嵌入"
  [content documents]
  (let [path-index (build-path-index documents)
        ;; 匹配 [[...]] 和 ![[...]]
        pattern #"(!?)\[\[([^\]]+)\]\]"]
    (str/replace content pattern
                 (fn [[_ is-embed link-text]]
                   (let [parsed (parse-obsidian-link (str is-embed "[[" link-text "]]"))
                         target-doc (find-document-by-path (:path parsed) path-index)]
                     (cond
                       ;; 图片嵌入
                       (and (:embed? parsed) target-doc)
                       (render-image-embed target-doc (:display parsed))

                       ;; 普通链接，找到目标
                       target-doc
                       (render-internal-link target-doc (:display parsed) (:anchor parsed))

                       ;; 链接不存在
                       :else
                       (render-broken-link (:path parsed) (:display parsed))))))))

;;; ============================================================
;;; 6. 完整渲染流程
;;; ============================================================

(defn render-markdown
  "完整的 Markdown 渲染流程
   输入: Markdown 内容、文档列表
   输出: HTML 字符串

   处理流程:
   1. 提取并替换数学公式为占位符
   2. 替换 Obsidian 链接为 HTML 链接
   3. Markdown -> HTML 转换
   4. 还原数学公式为 KaTeX 标记"
  [content documents]
  (when content
    (let [;; 1. 提取数学公式
          {:keys [content formulas]} (extract-math content)
          ;; 2. 替换 Obsidian 链接
          content-with-links (replace-obsidian-links content documents)
          ;; 3. Markdown -> HTML
          html (md->html content-with-links)
          ;; 4. 还原数学公式
          final-html (restore-math html formulas)]
      final-html)))
