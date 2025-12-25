(ns markdownbrain.markdown-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [markdownbrain.markdown :as md]))

;;; 测试 1: 基础 Markdown 转换
(deftest test-basic-markdown-conversion
  (testing "将简单的 markdown 转换为 HTML"
    (is (= "<p>Hello world</p>"
           (md/md->html "Hello world"))))

  (testing "标题转换"
    (is (= "<h1>Title</h1>"
           (md/md->html "# Title"))))

  (testing "粗体和斜体"
    (is (= "<p><strong>bold</strong> and <em>italic</em></p>"
           (md/md->html "**bold** and *italic*"))))

  (testing "代码块"
    (let [html (md/md->html "```python\nprint('hello')\n```")]
      (is (or (str/includes? html "language-python")
              (str/includes? html "class=\"python\""))))))

;;; 测试 2: 解析单个 Obsidian 链接
(deftest test-parse-obsidian-link
  (testing "解析简单链接 [[filename]]"
    (is (= {:type :link
            :embed? false
            :path "filename"
            :display "filename"
            :anchor nil}
           (md/parse-obsidian-link "[[filename]]"))))

  (testing "解析带显示文本的链接 [[filename|display text]]"
    (is (= {:type :link
            :embed? false
            :path "filename"
            :display "display text"
            :anchor nil}
           (md/parse-obsidian-link "[[filename|display text]]"))))

  (testing "解析带锚点的链接 [[filename#heading]]"
    (is (= {:type :link
            :embed? false
            :path "filename"
            :display "filename#heading"
            :anchor "heading"}
           (md/parse-obsidian-link "[[filename#heading]]"))))

  (testing "解析图片嵌入 ![[image.png]]"
    (is (= {:type :embed
            :embed? true
            :path "image.png"
            :display "image.png"
            :anchor nil}
           (md/parse-obsidian-link "![[image.png]]")))))

;;; 测试 3: 提取数学公式
(deftest test-extract-math
  (testing "提取行内公式 $x^2$"
    (let [result (md/extract-math "The formula $x^2$ is simple")]
      (is (= "The formula ___MATH_0___ is simple" (:content result)))
      (is (= [{:type :inline :formula "x^2"}] (:formulas result)))))

  (testing "提取块级公式 $$...$$"
    (let [result (md/extract-math "$$E = mc^2$$")]
      (is (= "___MATH_0___" (:content result)))
      (is (= [{:type :block :formula "E = mc^2"}] (:formulas result)))))

  (testing "提取多个公式"
    (let [result (md/extract-math "Inline $a + b$ and block $$c = d$$")]
      ;; 注意：先提取块级再提取行内，所以顺序是反的
      (is (= "Inline ___MATH_1___ and block ___MATH_0___" (:content result)))
      (is (= [{:type :block :formula "c = d"}
              {:type :inline :formula "a + b"}]
             (:formulas result))))))

;;; 测试 4: 还原数学公式
(deftest test-restore-math
  (testing "还原行内公式"
    (is (= "<p>The formula <span class=\"math-inline\">x^2</span> is simple</p>"
           (md/restore-math "<p>The formula ___MATH_0___ is simple</p>"
                            [{:type :inline :formula "x^2"}]))))

  (testing "还原块级公式"
    (is (= "<div class=\"math-block\">E = mc^2</div>"
           (md/restore-math "___MATH_0___"
                            [{:type :block :formula "E = mc^2"}])))))

;;; 测试 5: 提取标题
(deftest test-extract-title
  (testing "从内容中提取第一个标题"
    (is (= "My Title"
           (md/extract-title "# My Title\n\nSome content"))))

  (testing "无标题时返回 nil"
    (is (nil? (md/extract-title "Just content without title"))))

  (testing "提取 H2 标题"
    (is (= "Subtitle"
           (md/extract-title "## Subtitle\n\nContent")))))

;;; 测试 6: 替换 Obsidian 链接
(deftest test-replace-obsidian-links
  (let [;; 模拟文档数据
        documents [{:id "doc-1" :client-id "client-1" :path "Note A.md"}
                   {:id "doc-2" :client-id "client-2" :path "Note B.md"}
                   {:id "doc-3" :client-id "client-3" :path "image.png"}]]

    (testing "替换简单链接"
      (is (str/includes?
           (md/replace-obsidian-links "Check [[Note A]]" documents)
           "href=\"/doc-1\"")))

    (testing "替换带显示文本的链接"
      (let [result (md/replace-obsidian-links "See [[Note B|my note]]" documents)]
        (is (str/includes? result "href=\"/doc-2\""))
        (is (str/includes? result ">my note</a>"))))

    (testing "链接不存在时显示为 broken"
      (let [result (md/replace-obsidian-links "[[Non Existent]]" documents)]
        (is (str/includes? result "broken"))
        (is (str/includes? result "Non Existent"))))

    (testing "图片嵌入"
      (let [result (md/replace-obsidian-links "![[image.png]]" documents)]
        (is (str/includes? result "<img"))
        (is (str/includes? result "/doc-3"))))))

;;; 测试 7: 完整渲染流程
(deftest test-render-markdown
  (let [documents [{:id "doc-1" :client-id "client-1" :path "Other Note.md"}]
        content "# Test Note\n\nThis is a [[Other Note]] with $x^2$ formula.\n\n$$E = mc^2$$"]

    (testing "完整渲染包含所有功能"
      (let [result (md/render-markdown content documents)]
        ;; 检查 HTML 标题
        (is (str/includes? result "<h1"))
        (is (str/includes? result "Test Note"))
        ;; 检查 Obsidian 链接被替换
        (is (str/includes? result "href=\"/doc-1\""))
        ;; 检查数学公式被标记
        (is (str/includes? result "math-inline"))
        (is (str/includes? result "math-block"))))))
