(ns markdownbrain.utils.htmx)

(defn is-htmx?
  [req]
  (boolean (get-in req [:headers "hx-request"])))

