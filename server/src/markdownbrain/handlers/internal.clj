(ns markdownbrain.handlers.internal
  (:require
   [markdownbrain.config :as config]
   [markdownbrain.db :as db]))

(defn- valid-internal-token? [req]
  (let [token (config/internal-token)
        req-token (or (get-in req [:headers "authorization"])
                      (get-in req [:query-params "token"]))]
    (and token
         (or (= req-token token)
             (= req-token (str "Bearer " token))))))

(defn health [req]
  (if (valid-internal-token? req)
    {:status 200 :body "ok"}
    {:status 401 :body "unauthorized"}))

(defn domain-check [req]
  (if-not (valid-internal-token? req)
    {:status 401 :body "unauthorized"}
    (let [domain (get-in req [:query-params "domain"])]
      (if (and domain (db/get-vault-by-domain domain))
        {:status 200 :body "ok"}
        {:status 404 :body "not found"}))))
