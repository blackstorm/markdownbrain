(ns markdownbrain.validation
  (:require [malli.core :as m]
            [malli.error :as me]
            [clojure.tools.logging :as log]))

;; Sync 请求的 schema
(def sync-request-schema
  [:map
   [:path [:string {:min 1}]]
   [:clientId [:string {:min 1}]]
   [:clientType {:optional true} [:maybe :string]]
   [:action [:enum "create" "modify" "delete"]]
   ;; 以下字段是可选的
   [:content {:optional true} [:maybe :string]]
   [:hash {:optional true} [:maybe :string]]
   [:mtime {:optional true} [:maybe :string]]
   [:metadata {:optional true} [:maybe :map]]])

;; 验证函数
(defn validate-sync-request
  "验证 sync 请求数据，返回 {:valid? true/false :data data :errors errors}"
  [data]
  (let [validator (m/validator sync-request-schema)
        valid? (validator data)]
    (if valid?
      {:valid? true
       :data data}
      (let [explainer (m/explainer sync-request-schema)
            explanation (explainer data)
            errors (me/humanize explanation)]
        (log/warn "Validation failed:" errors)
        {:valid? false
         :errors errors
         :message "Invalid request data"}))))

;; 验证 create/modify 请求必须包含 content
(defn validate-content-required
  "验证 create/modify action 必须包含 content 字段（允许空字符串）"
  [action data]
  (if (and (or (= action "create") (= action "modify"))
           (nil? (:content data)))
    {:valid? false
     :errors {:content ["Content field is required for create/modify actions"]}
     :message "Content field is required for create/modify actions"}
    {:valid? true
     :data data}))

;; ============================================================
;; Full Sync 请求验证
;; ============================================================

(def full-sync-request-schema
  "Full-sync 请求的 schema
   客户端发送完整文档列表，服务器清理孤儿文档"
  [:map
   [:clientIds [:vector {:min 1} [:string {:min 1}]]]])

(defn validate-full-sync-request
  "验证 full-sync 请求数据
   返回 {:valid? true/false :data data :errors errors :message message}"
  [data]
  (let [validator (m/validator full-sync-request-schema)
        valid? (validator data)]
    (if valid?
      {:valid? true
       :data data}
      (let [explainer (m/explainer full-sync-request-schema)
            explanation (explainer data)
            errors (me/humanize explanation)]
        (log/warn "Full-sync validation failed:" errors)
        {:valid? false
         :errors errors
         :message "clientIds must be a non-empty array of non-empty strings"}))))

;; ============================================================
;; Asset Sync 请求验证
;; ============================================================

(def asset-sync-request-schema
  [:map
   [:path [:string {:min 1}]]
   [:clientId [:string {:min 1}]]
   [:action [:enum "create" "modify" "delete"]]
   [:size {:optional true} [:maybe :int]]
   [:contentType {:optional true} [:maybe :string]]
   [:sha256 {:optional true} [:maybe :string]]
   [:content {:optional true} [:maybe :string]]])

(defn validate-asset-sync-request
  [data]
  (let [validator (m/validator asset-sync-request-schema)
        valid? (validator data)]
    (if valid?
      {:valid? true
       :data data}
      (let [explainer (m/explainer asset-sync-request-schema)
            explanation (explainer data)
            errors (me/humanize explanation)]
        (log/warn "Asset sync validation failed:" errors)
        {:valid? false
         :errors errors
         :message "Invalid asset sync request"}))))

(defn validate-asset-metadata-required
  [action data]
  (if (and (contains? #{"create" "modify"} action)
           (or (nil? (:size data))
               (nil? (:contentType data))
               (nil? (:sha256 data))))
    {:valid? false
     :errors {:metadata ["size, contentType, and sha256 are required for create/modify actions"]}
     :message "size, contentType, and sha256 are required for create/modify actions"}
    {:valid? true
     :data data}))
