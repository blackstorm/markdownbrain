(ns markdownbrain.link-parser-test
  "链接解析器测试 - 服务端统一解析 [[link]] 语法
   
   设计目标：
   1. 从 markdown 内容中提取所有 [[link]] 和 ![[embed]]
   2. 解析链接的各个部分（路径、显示文本、锚点）
   3. 通过 path 匹配找到 target_client_id
   4. 生成可直接写入 document_links 表的数据"
  (:require [clojure.test :refer :all]
            [markdownbrain.link-parser :as lp]))

;;; ============================================================
;;; 1. 提取链接测试
;;; ============================================================

(deftest test-extract-links
  (testing "提取简单链接 [[filename]]"
    (let [content "See [[Note A]] for details"
          links (lp/extract-links content)]
      (is (= 1 (count links)))
      (is (= {:original "[[Note A]]"
              :path "Note A"
              :display "Note A"
              :anchor nil
              :link-type "link"}
             (first links)))))

  (testing "提取带显示文本的链接 [[path|display]]"
    (let [content "Check [[Note B|my note]] here"
          links (lp/extract-links content)]
      (is (= 1 (count links)))
      (is (= {:original "[[Note B|my note]]"
              :path "Note B"
              :display "my note"
              :anchor nil
              :link-type "link"}
             (first links)))))

  (testing "提取带锚点的链接 [[path#anchor]]"
    (let [content "See [[Note C#section]]"
          links (lp/extract-links content)]
      (is (= 1 (count links)))
      (is (= {:original "[[Note C#section]]"
              :path "Note C"
              :display "Note C#section"
              :anchor "section"
              :link-type "link"}
             (first links)))))

  (testing "提取带锚点和显示文本的链接 [[path#anchor|display]]"
    (let [content "See [[Note D#heading|click here]]"
          links (lp/extract-links content)]
      (is (= 1 (count links)))
      (is (= {:original "[[Note D#heading|click here]]"
              :path "Note D"
              :display "click here"
              :anchor "heading"
              :link-type "link"}
             (first links)))))

  (testing "提取嵌入 ![[image]]"
    (let [content "Here is ![[image.png]]"
          links (lp/extract-links content)]
      (is (= 1 (count links)))
      (is (= {:original "![[image.png]]"
              :path "image.png"
              :display "image.png"
              :anchor nil
              :link-type "embed"}
             (first links)))))

  (testing "提取多个链接"
    (let [content "Link to [[Note A]] and [[Note B|B note]] and ![[img.png]]"
          links (lp/extract-links content)]
      (is (= 3 (count links)))
      (is (= #{"[[Note A]]" "[[Note B|B note]]" "![[img.png]]"}
             (set (map :original links))))))

  (testing "没有链接返回空列表"
    (let [content "Just plain text without any links"
          links (lp/extract-links content)]
      (is (empty? links))))

  (testing "空内容返回空列表"
    (is (empty? (lp/extract-links nil)))
    (is (empty? (lp/extract-links ""))))

  (testing "处理路径中的 .md 扩展名"
    (let [content "Link to [[Note.md]] and [[Another]]"
          links (lp/extract-links content)]
      ;; 保留原始 path，规范化在 resolve 阶段处理
      (is (= "Note.md" (:path (first links))))
      (is (= "Another" (:path (second links))))))

  (testing "处理子目录路径"
    (let [content "See [[folder/subfolder/Note]]"
          links (lp/extract-links content)]
      (is (= "folder/subfolder/Note" (:path (first links)))))))

;;; ============================================================
;;; 2. 解析链接到 target_client_id 测试
;;; ============================================================

(deftest test-resolve-links
  (let [;; 模拟 vault 中的文档
        documents [{:client-id "client-a" :path "Note A.md"}
                   {:client-id "client-b" :path "Note B.md"}
                   {:client-id "client-c" :path "folder/Note C.md"}
                   {:client-id "client-img" :path "image.png"}]]

    (testing "通过路径解析到 target_client_id"
      (let [links [{:original "[[Note A]]" :path "Note A" :display "Note A" :anchor nil :link-type "link"}]
            resolved (lp/resolve-links links documents)]
        (is (= 1 (count resolved)))
        (is (= "client-a" (:target-client-id (first resolved))))))

    (testing "路径匹配忽略 .md 扩展名"
      (let [links [{:original "[[Note B.md]]" :path "Note B.md" :display "Note B.md" :anchor nil :link-type "link"}]
            resolved (lp/resolve-links links documents)]
        (is (= "client-b" (:target-client-id (first resolved))))))

    (testing "路径匹配忽略大小写"
      (let [links [{:original "[[note a]]" :path "note a" :display "note a" :anchor nil :link-type "link"}]
            resolved (lp/resolve-links links documents)]
        (is (= "client-a" (:target-client-id (first resolved))))))

    (testing "匹配子目录路径"
      (let [links [{:original "[[folder/Note C]]" :path "folder/Note C" :display "folder/Note C" :anchor nil :link-type "link"}]
            resolved (lp/resolve-links links documents)]
        (is (= "client-c" (:target-client-id (first resolved))))))

    (testing "Obsidian 风格：只用文件名匹配（不含目录）"
      ;; Obsidian 中 [[Note C]] 可以匹配 folder/Note C.md
      (let [links [{:original "[[Note C]]" :path "Note C" :display "Note C" :anchor nil :link-type "link"}]
            resolved (lp/resolve-links links documents)]
        (is (= "client-c" (:target-client-id (first resolved))))))

    (testing "未找到目标时 target_client_id 为 nil"
      (let [links [{:original "[[Non Existent]]" :path "Non Existent" :display "Non Existent" :anchor nil :link-type "link"}]
            resolved (lp/resolve-links links documents)]
        (is (= 1 (count resolved)))
        (is (nil? (:target-client-id (first resolved))))
        ;; 保留 target-path 用于显示
        (is (= "Non Existent" (:target-path (first resolved))))))

    (testing "resolved 结果包含写入 document_links 所需的所有字段"
      (let [links [{:original "[[Note A|click me]]" :path "Note A" :display "click me" :anchor "heading" :link-type "link"}]
            resolved (lp/resolve-links links documents)
            result (first resolved)]
        (is (= "client-a" (:target-client-id result)))
        (is (= "Note A" (:target-path result)))
        (is (= "link" (:link-type result)))
        (is (= "click me" (:display-text result)))
        (is (= "[[Note A|click me]]" (:original result)))))))

;;; ============================================================
;;; 3. 端到端：从 content 到 document_links 数据
;;; ============================================================

(deftest test-parse-and-resolve
  (let [documents [{:client-id "client-hello" :path "Hello world.md"}
                   {:client-id "client-about" :path "About.md"}]
        content "# My Note\n\nSee [[Hello world]] for intro.\n\nAlso check [[About|关于页面]].\n\n[[Missing Link]] is broken."]

    (testing "完整流程：提取 + 解析"
      (let [links (lp/extract-links content)
            resolved (lp/resolve-links links documents)]
        ;; 提取了 3 个链接
        (is (= 3 (count resolved)))
        ;; 2 个有 target_client_id
        (is (= 2 (count (filter :target-client-id resolved))))
        ;; 1 个是 broken link
        (is (= 1 (count (filter #(nil? (:target-client-id %)) resolved))))))))

;;; ============================================================
;;; 4. 去重测试
;;; ============================================================

(deftest test-deduplicate-links
  (testing "同一目标的多个链接只保留一个"
    (let [content "[[Note A]] and [[Note A|别名]] and [[note a]]"
          documents [{:client-id "client-a" :path "Note A.md"}]
          links (lp/extract-links content)
          resolved (lp/resolve-links links documents)
          ;; 去重：同一个 target_client_id 只保留一个
          deduped (lp/deduplicate-by-target resolved)]
      ;; 原始有 3 个
      (is (= 3 (count resolved)))
      ;; 去重后 1 个
      (is (= 1 (count deduped)))
      (is (= "client-a" (:target-client-id (first deduped)))))))
