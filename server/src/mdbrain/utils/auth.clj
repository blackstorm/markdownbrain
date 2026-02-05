(ns mdbrain.utils.auth
  (:require
   [buddy.hashers :as hashers]
   [clojure.string :as str]))

(defn hash-password
  [password]
  (hashers/derive password {:alg :bcrypt+sha512}))

(defn verify-password
  [password hash]
  (hashers/check password hash))

(defn- drop-leading-whitespace
  ^String [^String s]
  (let [n (.length s)]
    (loop [i 0]
      (if (and (< i n) (Character/isWhitespace (.charAt s i)))
        (recur (inc i))
        (subs s i)))))

(defn- drop-bearer-prefix
  "If header starts with `Bearer` followed by at least one whitespace char,
   return the remainder with any additional leading whitespace removed."
  ^String [^String header]
  (let [prefix "Bearer"
        plen (.length prefix)
        n (.length header)]
    (if (and (>= n (inc plen))
             (= prefix (subs header 0 plen))
             (Character/isWhitespace (.charAt header plen)))
      (drop-leading-whitespace (subs header plen))
      header)))

(defn parse-auth-header
  [header]
  (when header
    (let [token (drop-bearer-prefix (str header))
          idx (.indexOf token ":")]
      (when (pos? idx)
        (let [vault-id (subs token 0 idx)
              sync-token (subs token (inc idx))]
          (when (and (not (str/blank? vault-id))
                     (not (str/blank? sync-token)))
            {:vault-id vault-id
             :sync-token sync-token})))))) 
