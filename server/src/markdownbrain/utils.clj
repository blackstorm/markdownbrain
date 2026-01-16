(ns markdownbrain.utils
  (:require
   [buddy.hashers :as hashers]
   [clojure.string :as str])
  (:import
   [java.security MessageDigest]
   [java.util UUID]))

(defn generate-uuid []
  (str (UUID/randomUUID)))

(defn sha256-hex
  "Compute SHA-256 hash of byte array and return as hex string.
   Optional length parameter specifies number of bytes to use (default: 32 = full hash)."
  ([^bytes data]
   (sha256-hex data 32))
  ([^bytes data length]
   (let [md (MessageDigest/getInstance "SHA-256")
         hash-bytes (.digest md data)
         truncated (take length hash-bytes)]
     (apply str (map #(format "%02x" %) truncated)))))

(defn generate-note-id
  [vault-id path]
  (let [md (MessageDigest/getInstance "SHA-256")
        input (str vault-id ":" path)
        hash-bytes (.digest md (.getBytes input "UTF-8"))
        hex (apply str (map #(format "%02x" %) (take 16 hash-bytes)))
        uuid-str (str (subs hex 0 8) "-"
                      (subs hex 8 12) "-"
                      (subs hex 12 16) "-"
                      (subs hex 16 20) "-"
                      (subs hex 20 32))]
    uuid-str))

(defn normalize-link-path
  [link-path]
  (let [path (str/trim link-path)]
    (if (str/ends-with? path ".md")
      path
      (str path ".md"))))

(defn hash-password [password]
  (hashers/derive password {:alg :bcrypt+sha512}))

(defn verify-password [password hash]
  (hashers/check password hash))

(defn parse-auth-header [header]
  (when header
    (let [token (str/replace header #"^Bearer\s+" "")
          [vault-id sync-token] (str/split token #":" 2)]
      (when (and vault-id sync-token
                 (not (str/blank? vault-id))
                 (not (str/blank? sync-token)))
        {:vault-id vault-id
         :sync-token sync-token}))))

(defn is-htmx? [req]
  (boolean (get-in req [:headers "hx-request"])))
