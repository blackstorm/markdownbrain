(ns markdownbrain.utils
  (:require
   [markdownbrain.utils.auth :as auth]
   [markdownbrain.utils.crypto :as crypto]
   [markdownbrain.utils.htmx :as htmx]
   [markdownbrain.utils.id :as id]
   [markdownbrain.utils.paths :as paths]))

;; Backward-compatible facade; implementations live under markdownbrain.utils.*
(def generate-uuid id/generate-uuid)

(def sha256-hex crypto/sha256-hex)

(def normalize-link-path paths/normalize-link-path)

(def hash-password auth/hash-password)
(def verify-password auth/verify-password)
(def parse-auth-header auth/parse-auth-header)

(def is-htmx? htmx/is-htmx?)
