(ns markdownbrain.handlers.frontend
  (:require
   [clojure.string :as str]
   [markdownbrain.db :as db]
   [markdownbrain.markdown :as md]
   [markdownbrain.response :as resp]
   [selmer.parser :as selmer]))

;; ============================================================
;; Vault 解析
;; ============================================================

(defn get-current-vault
  "获取当前域名对应的 vault"
  [request]
  (when-let [host (get-in request [:headers "host"])]
    (let [domain (first (str/split host #":"))]
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

(defn build-push-url
  "构建 HX-Push-Url 路径
   
   参数:
   - current-url: 当前页面 URL (从 HX-Current-URL header)
   - from-note-id: 来源笔记 ID (从 X-From-Note-Id header)
   - target-note-id: 目标笔记 ID
   - root-note-id: 根笔记 ID (可选，用于 / 路径)
   
   返回: 新的 URL 路径 (使用 + 分隔符)"
  [current-url from-note-id target-note-id root-note-id]
  (let [;; 解析当前 URL 获取路径
        current-path (if current-url
                       (-> current-url
                           (str/replace #"^https?://[^/]+" "")
                           (str/replace #"\?.*$" ""))
                       "/")]
    (cond
      ;; 根路径：添加 root-note-id 和 target
      (= current-path "/")
      (if root-note-id
        (str "/" root-note-id "+" target-note-id)
        (str "/" target-note-id))
      
      ;; 非根路径：在 from-note-id 之后追加 target
      :else
      (let [path-parts (-> current-path
                           (str/replace #"^/" "")
                           (str/split #"\+"))]
        (if from-note-id
          ;; 找到 from-note-id，截断后追加
          (let [idx (.indexOf (vec path-parts) from-note-id)
                kept-parts (if (>= idx 0)
                             (take (inc idx) path-parts)
                             path-parts)]
            (str "/" (str/join "+" (concat kept-parts [target-note-id]))))
          ;; 没有 from-note-id，直接追加
          (str "/" (str/join "+" (concat path-parts [target-note-id]))))))))

;; ============================================================
;; 笔记数据准备
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

;; ============================================================
;; HTMX 片段接口
;; ============================================================

(defn get-doc-fragment
  "返回单个笔记的 HTML 片段（用于 HTMX）
   
   支持 HX-Push-Url：
   - 读取 X-From-Note-Id header 获取来源笔记
   - 读取 HX-Current-URL header 获取当前 URL
   - 返回 HX-Push-Url header 用于更新浏览器地址栏

   路径: /docs/:id
   响应: 单个 doc.html 片段 + HX-Push-Url header"
  [request]
  (let [doc-id (get-in request [:path-params :id])
        vault (get-current-vault request)]

    (if-not vault
      {:status 404 :body "Site not found"}

      (if-let [doc (db/get-document doc-id)]
        (let [vault-id (:vault-id doc)
              all-docs (db/list-documents-by-vault vault-id)
              render-data (prepare-doc-data doc vault-id all-docs)
              
              ;; 获取 HTMX headers
              from-note-id (get-in request [:headers "x-from-note-id"])
              current-url (get-in request [:headers "hx-current-url"])
              root-doc-id (:root-doc-id vault)
              
              ;; 构建 push URL
              push-url (build-push-url current-url from-note-id doc-id root-doc-id)
              
              ;; 渲染 HTML
              html-body (selmer/render-file "templates/frontend/doc.html" render-data)]
          
          {:status 200
           :headers {"Content-Type" "text/html; charset=utf-8"
                     "HX-Push-Url" push-url}
           :body html-body})
        {:status 404 :body "Document not found"}))))

;; ============================================================
;; 主页面渲染
;; ============================================================

(defn get-doc
  "渲染笔记（支持滑动面板堆叠）

   URL 格式:
   - / : 根路径，显示 root document 或文档列表
   - /{id} : 单个笔记
   - /{id}+{id}+{id} : 堆叠笔记 (使用 + 分隔符)

   HTMX 请求:
   - 返回单个笔记 HTML 片段
   - 设置 HX-Push-Url header 更新浏览器地址栏

   普通请求:
   - 返回完整页面（包含所有堆叠的笔记）
   - 自动重定向根路径到 root document
   - 容错处理：如果有文档被删除，自动修正 URL"
  [request]
  (let [;; 解析路径参数
        path (get-in request [:path-params :path] "/")
        path-ids (parse-path-ids path)
        vault (get-current-vault request)
        is-htmx? (get-in request [:headers "hx-request"])]

    (if-not vault
      {:status 404 :body "Site not found"}

      ;; 确定要显示的文档
      (if (empty? path-ids)
        ;; 根路径：显示 root document 或文档列表
        (if-let [root-doc-id (:root-doc-id vault)]
          (if-let [root-doc (db/get-document root-doc-id)]
            ;; 有 root document：直接显示
            (let [vault-id (:vault-id root-doc)
                  all-docs (db/list-documents-by-vault vault-id)
                  render-data (prepare-doc-data root-doc vault-id all-docs)]
              (if is-htmx?
                ;; HTMX 请求：返回片段
                {:status 200
                 :headers {"Content-Type" "text/html; charset=utf-8"}
                 :body (selmer/render-file "templates/frontend/doc.html" render-data)}
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

        ;; 有 ID：显示堆叠的笔记
        (let [;; 查询所有文档，过滤掉不存在的
              valid-docs (keep db/get-document path-ids)
              valid-ids (mapv :id valid-docs)
              
              ;; 如果有文档被删除，构建修正后的 URL
              needs-correction? (not= (count valid-ids) (count path-ids))
              corrected-path (when needs-correction?
                               (if (empty? valid-ids)
                                 "/"
                                 (str "/" (str/join "+" valid-ids))))]

          (cond
            ;; 所有文档都不存在
            (empty? valid-docs)
            {:status 404 :body "Document not found"}

            ;; HTMX 请求：返回最后一个笔记片段
            is-htmx?
            (let [vault-id (:vault-id (first valid-docs))
                  all-docs (db/list-documents-by-vault vault-id)
                  last-doc (last valid-docs)
                  render-data (prepare-doc-data last-doc vault-id all-docs)
                  
                  ;; 获取 HTMX headers 构建 push URL
                  from-note-id (get-in request [:headers "x-from-note-id"])
                  current-url (get-in request [:headers "hx-current-url"])
                  root-doc-id (:root-doc-id vault)
                  push-url (or corrected-path
                               (build-push-url current-url from-note-id (:id last-doc) root-doc-id))]
              
              {:status 200
               :headers {"Content-Type" "text/html; charset=utf-8"
                         "HX-Push-Url" push-url}
               :body (selmer/render-file "templates/frontend/doc.html" render-data)})

            ;; 普通请求：返回完整页面
            :else
            (let [vault-id (:vault-id (first valid-docs))
                  all-docs (db/list-documents-by-vault vault-id)
                  docs-data (mapv #(prepare-doc-data % vault-id all-docs) valid-docs)
                  response-body (selmer/render-file "templates/frontend/doc-page.html"
                                                    {:docs docs-data :vault vault})]
              (if needs-correction?
                ;; 需要修正 URL
                {:status 200
                 :headers {"Content-Type" "text/html; charset=utf-8"
                           "HX-Replace-Url" corrected-path}
                 :body response-body}
                ;; 正常响应
                (resp/html response-body)))))))))
