(ns markdownbrain.validation
  (:require [malli.core :as m]
            [malli.error :as me]
            [clojure.tools.logging :as log]))

;; Sync 请求的 schema
(def sync-request-schema
  [:map
   [:path [:string {:min 1}]]
   [:clientId [:string {:min 1}]]
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
  "验证 create/modify action 必须包含 content"
  [action data]
  (if (and (or (= action "create") (= action "modify"))
           (or (nil? (:content data))
               (empty? (:content data))))
    {:valid? false
     :errors {:content ["Content is required for create/modify actions"]}
     :message "Content is required for create/modify actions"}
    {:valid? true
     :data data}))
