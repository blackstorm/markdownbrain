(ns markdownbrain.handlers.console
  "Console handlers facade - re-exports functions from submodules.

   This namespace maintains backward compatibility for routes.clj and other consumers
   while the actual implementation is split across:
   - markdownbrain.handlers.console.auth (login, logout, init)
   - markdownbrain.handlers.console.vaults (vault CRUD, notes, root-note)
   - markdownbrain.handlers.console.logo (logo upload, delete, serve)
   - markdownbrain.handlers.console.common (shared utilities)"
  (:require
   [markdownbrain.handlers.console.auth :as auth]
   [markdownbrain.handlers.console.common :as common]
   [markdownbrain.handlers.console.logo :as logo]
   [markdownbrain.handlers.console.vaults :as vaults]))

;; ============================================================
;; Auth handlers
;; ============================================================

(def init-console auth/init-console)
(def login auth/login)
(def logout auth/logout)
(def login-page auth/login-page)
(def init-page auth/init-page)
(def change-password auth/change-password)

;; ============================================================
;; Vault handlers
;; ============================================================

(def console-home vaults/console-home)
(def list-vaults vaults/list-vaults)
(def create-vault vaults/create-vault)
(def update-vault vaults/update-vault)
(def delete-vault vaults/delete-vault)
(def search-vault-notes vaults/search-vault-notes)
(def update-vault-root-note vaults/update-vault-root-note)
(def get-root-note-selector vaults/get-root-note-selector)
(def renew-vault-sync-key vaults/renew-vault-sync-key)
(def update-custom-head-html vaults/update-custom-head-html)

;; ============================================================
;; Logo handlers
;; ============================================================

(def upload-vault-logo logo/upload-vault-logo)
(def delete-vault-logo logo/delete-vault-logo)
(def serve-vault-logo logo/serve-vault-logo)
(def serve-vault-favicon logo/serve-vault-favicon)

;; ============================================================
;; Common utilities
;; ============================================================

(def serve-console-asset common/serve-console-asset)
(def console-asset-url common/console-asset-url)
