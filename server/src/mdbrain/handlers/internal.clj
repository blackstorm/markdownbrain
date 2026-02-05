(ns mdbrain.handlers.internal
  (:require
   [clojure.tools.logging :as log]
   [mdbrain.config :as config]
   [mdbrain.db :as db]))

(defn- health-token-from-request [req]
  (or (get-in req [:headers "x-health-token"])
      (get-in req [:query-params "token"])
      (get-in req [:params "token"])))

(defn health [req]
  (let [ok? (= (health-token-from-request req) (config/health-token))]
    (log/debug "Health check" {:ok ok?})
    (if ok?
      {:status 200 :body "ok"}
      {:status 401 :body "unauthorized"})))

(defn robots [_]
  {:status 200
   :headers {"Content-Type" "text/plain; charset=utf-8"}
   :body "User-agent: *\nDisallow: /\n"})

(defn domain-check [req]
  (let [domain (get-in req [:query-params "domain"])]
    (if (and domain (db/get-vault-by-domain domain))
      {:status 200 :body "ok"}
      {:status 404 :body "not found"})))
