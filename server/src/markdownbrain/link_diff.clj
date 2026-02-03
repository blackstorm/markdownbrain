(ns markdownbrain.link-diff
  (:require [clojure.set :as set]))

;; 链接操作类型
(defrecord LinkOperation [op target-client-id link-data])

(defn- make-link-key
  "从链接数据生成唯一键"
  [link]
  (:target-client-id link))

(defn- normalize-existing-link
  "标准化现有数据库链接格式"
  [db-link]
  {:target-client-id (:target-client-id db-link)
   :target-path (:target-path db-link)
   :link-type (:link-type db-link)
   :display-text (:display-text db-link)
   :original (:original db-link)})

(defn- normalize-new-link
  "标准化新链接格式（来自客户端）"
  [client-link]
  {:target-client-id (:targetClientId client-link)
   :target-path (:link client-link)
   :link-type (or (:linkType client-link) "link")
   :display-text (or (:displayText client-link) "")
   :original (:original client-link)})

(defn compute-link-diff
  "计算链接差异，返回操作列表

   参数:
   - existing-links: 数据库中的现有链接列表
   - new-links: 客户端发送的新链接列表

   返回:
   操作列表，每个操作是一个 map:
   {:op :insert/:delete
    :target-client-id \"xxx\"
    :link-data {...}}  ;; 仅 insert 操作需要"
  [existing-links new-links]
  (let [;; 标准化链接数据
        existing-normalized (map normalize-existing-link existing-links)
        new-normalized (map normalize-new-link new-links)

        ;; 构建 target-client-id -> link-data 的映射
        existing-map (into {} (map (juxt make-link-key identity) existing-normalized))
        new-map (into {} (map (juxt make-link-key identity) new-normalized))

        ;; 获取 target-client-id 集合
        existing-keys (set (keys existing-map))
        new-keys (set (keys new-map))

        ;; 计算差异
        to-delete (set/difference existing-keys new-keys)
        to-add (set/difference new-keys existing-keys)
        to-keep (set/intersection existing-keys new-keys)

        ;; 检查保留的链接是否需要更新（内容变化）
        to-update (filter (fn [key]
                           (not= (existing-map key) (new-map key)))
                         to-keep)]

    ;; 构建操作列表（按顺序：先删除，再更新，最后插入）
    (concat
      ;; 删除操作
      (map (fn [target-id]
             {:op :delete
              :target-client-id target-id})
           to-delete)

      ;; 更新操作（先删除再插入）
      (mapcat (fn [target-id]
                [{:op :delete
                  :target-client-id target-id}
                 {:op :insert
                  :target-client-id target-id
                  :link-data (new-map target-id)}])
              to-update)

      ;; 插入操作
      (map (fn [target-id]
             {:op :insert
              :target-client-id target-id
              :link-data (new-map target-id)})
           to-add))))

(defn summarize-diff
  "生成差异摘要，用于日志输出"
  [operations]
  (let [grouped (group-by :op operations)
        deletes (get grouped :delete [])
        inserts (get grouped :insert [])
        ;; 检测更新：成对的删除+插入操作，target-client-id 相同
        delete-targets (set (map :target-client-id deletes))
        insert-targets (set (map :target-client-id inserts))
        updated-targets (set/intersection delete-targets insert-targets)
        pure-deletes (set/difference delete-targets updated-targets)
        pure-inserts (set/difference insert-targets updated-targets)]
    {:total-operations (count operations)
     :deletes (count pure-deletes)
     :inserts (count pure-inserts)
     :updates (count updated-targets)}))
