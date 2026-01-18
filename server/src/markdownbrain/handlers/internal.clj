(ns markdownbrain.handlers.internal
  (:require
   [markdownbrain.db :as db]))

(defn- loopback-request? [req]
  (let [addr (:remote-addr req)]
    (contains? #{"127.0.0.1" "::1" "0:0:0:0:0:0:0:1"} addr)))

(defn health [req]
  (if (loopback-request? req)
    {:status 200 :body "ok"}
    {:status 401 :body "unauthorized"}))

(defn domain-check [req]
  (let [domain (get-in req [:query-params "domain"])]
    (if (and domain (db/get-vault-by-domain domain))
      {:status 200 :body "ok"}
      {:status 404 :body "not found"})))
