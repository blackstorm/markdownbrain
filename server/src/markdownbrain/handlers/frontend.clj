(ns markdownbrain.handlers.frontend
  (:require
   [clojure.string :as str]
   [markdownbrain.db :as db]
   [markdownbrain.markdown :as md]
   [markdownbrain.response :as resp]
   [selmer.parser :as selmer]))

;; 获取当前域名对应的 vault
(defn get-current-vault [request]
  (when-let [host (get-in request [:headers "host"])]
    (let [domain (first (clojure.string/split host #":"))]
      (db/get-vault-by-domain domain))))

;; ============================================================
;; 路径解析
;; ============================================================

(defn parse-path-ids
  "从路径解析文档 ID 列表
   输入: \"/doc-a+doc-b+doc-c\" 或 \"/doc-a\" 或 \"/\"
   输出: [\"doc-a\" \"doc-b\" \"doc-c\"] 或 [] (如果是根路径)"
  [path]
  (if (or (nil? path) (= path "/") (= path ""))
    []
    (-> path
        (str/replace #"^/" "")
        (str/split #"\+")
        vec)))

;; ============================================================
;; 笔记展示（Andy Matuschak 滑动面板）
;; ============================================================

(defn- prepare-doc-data
  "准备单个笔记的渲染数据

   返回: {:doc {:id :title :html-content :path :updated-at}
          :backlinks [...]}"
  [doc vault-id all-docs]
  (let [;; 渲染 Markdown
        html-content (md/render-markdown (:content doc) all-docs)
        ;; 提取标题
        title (or (md/extract-title (:content doc))
                  (-> (:path doc)
                      (str/replace #"\.md$" "")
                      (str/replace #"/" " / ")))
        ;; 获取反向链接
        backlinks (db/get-backlinks-with-docs vault-id (:client-id doc))
        ;; 为每个反向链接提取标题和描述
        backlinks-with-meta (mapv (fn [backlink]
                                    (assoc backlink
                                           :title (or (md/extract-title (:content backlink))
                                                      (str/replace (:path backlink) #"\.md$" ""))
                                           :description (when (:content backlink)
                                                          (let [lines (str/split-lines (:content backlink))
                                                                first-para (->> lines
                                                                                (drop-while #(str/starts-with? % "#"))
                                                                                (filter #(not (str/blank? %)))
                                                                                first)]
                                                            (when first-para
                                                              (subs first-para 0 (min 100 (count first-para))))))))
                                  backlinks)]
    {:doc {:id (:id doc)
            :title title
            :html-content html-content
            :path (:path doc)
            :updated-at (:updated-at doc)}
     :backlinks backlinks-with-meta}))

(defn get-doc-fragment
  "返回单个笔记的 HTML 片段（用于 HTMX）

   路径: /docs/:id
   响应: 单个 doc.html 片段"
  [request]
  (let [doc-id (get-in request [:path-params :id])
        vault (get-current-vault request)]

    (if-not vault
      {:status 404 :body "Site not found"}

      (if-let [doc (db/get-document doc-id)]
        (let [vault-id (:vault-id doc)
              all-docs (db/list-documents-by-vault vault-id)
              render-data (prepare-doc-data doc vault-id all-docs)]
          (resp/html (selmer/render-file "templates/frontend/doc.html" render-data)))
        {:status 404 :body "Document not found"}))))

(defn get-doc
  "渲染笔记（支持滑动面板堆叠）

   URL 格式:
   - 根路径: /
   - 单个笔记: /doc-a
   - 堆叠笔记: /doc-a+doc-b+doc-c

   功能:
   - 渲染 Markdown 内容为 HTML
   - 处理 Obsidian [[链接]]
   - 显示反向链接（Link to this note）
   - 支持多笔记堆叠展示
   - 自动重定向根路径到 root document
   - 容错处理：如果有文档被删除，自动修正 URL

   响应:
   - 如果是普通请求：返回完整页面（包含所有堆叠的笔记）
   - 如果有文档被删除：使用 HX-Replace-Url 自动修正 URL"
  [request]
  (let [;; 解析路径参数
        path (get-in request [:path-params :path] "/")
        path-ids (parse-path-ids path)

        ;; 兼容旧的 query parameter 方式（向后兼容）
        query-stacked (get-in request [:query-params "stacked"])
        query-ids (cond
                    (nil? query-stacked) []
                    (string? query-stacked) [query-stacked]
                    :else query-stacked)

        ;; 优先使用路径 ID，fallback 到 query parameter
        requested-ids (if (seq path-ids) path-ids query-ids)

        vault (get-current-vault request)]

    (if-not vault
      {:status 404
       :body "Site not found"}

      ;; 确定要显示的文档
      (if (empty? requested-ids)
        ;; 没有 ID：显示 root document 或文档列表
        (if-let [root-doc-id (:root-doc-id vault)]
          (if-let [root-doc (db/get-document root-doc-id)]
            ;; 有 root document：直接显示（不重定向）
            (let [vault-id (:vault-id root-doc)
                  all-docs (db/list-documents-by-vault vault-id)
                  is-htmx? (get-in request [:headers "hx-request"])
                  render-data (prepare-doc-data root-doc vault-id all-docs)]
              (if is-htmx?
                ;; HTMX 请求：返回片段
                (resp/html (selmer/render-file "templates/frontend/doc.html" render-data))
                ;; 普通请求：返回完整页面
                (resp/html (selmer/render-file "templates/frontend/doc-page.html"
                                                {:docs [render-data]
                                                 :vault vault}))))
            ;; root document 不存在：显示文档列表
            (let [documents (db/list-documents-by-vault (:id vault))]
              (resp/html (selmer/render-file "templates/frontend/home.html"
                                              {:vault vault
                                               :documents documents}))))
          ;; 没有 root document：显示文档列表
          (let [documents (db/list-documents-by-vault (:id vault))]
            (resp/html (selmer/render-file "templates/frontend/home.html"
                                            {:vault vault
                                             :documents documents}))))

        ;; 有 ID：显示堆叠的笔记（容错处理）
        (let [;; 查询所有文档，过滤掉不存在的
              valid-docs (keep db/get-document requested-ids)
              valid-ids (mapv :id valid-docs)

              ;; 如果有文档被删除，构建修正后的 URL
              needs-correction? (not= (count valid-ids) (count requested-ids))
              corrected-path (when needs-correction?
                               (if (empty? valid-ids)
                                 "/"
                                 (str "/" (str/join "+" valid-ids))))

              is-htmx? (get-in request [:headers "hx-request"])]

          (cond
            ;; 所有文档都不存在
            (empty? valid-docs)
            {:status 404
             :body "Document not found"}

            ;; 渲染笔记（可能需要修正 URL）
            :else
            (let [vault-id (:vault-id (first valid-docs))
                  all-docs (db/list-documents-by-vault vault-id)
                  response-body (if is-htmx?
                                  ;; HTMX 请求：只返回最后一个笔记片段
                                  (let [last-doc (last valid-docs)
                                        render-data (prepare-doc-data last-doc vault-id all-docs)]
                                    (selmer/render-file "templates/frontend/doc.html" render-data))

                                  ;; 普通请求：返回完整页面
                                  (let [docs-data (mapv #(prepare-doc-data % vault-id all-docs) valid-docs)]
                                    (selmer/render-file "templates/frontend/doc-page.html"
                                                        {:docs docs-data :vault vault})))]

              (if needs-correction?
                ;; 需要修正 URL
                {:status 200
                 :headers {"Content-Type" "text/html; charset=utf-8"
                           "HX-Replace-Url" corrected-path}
                 :body response-body}
                ;; 正常响应
                (resp/html response-body)))))))))
