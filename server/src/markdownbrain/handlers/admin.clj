(ns markdownbrain.handlers.admin
  "Admin handlers facade - re-exports functions from submodules.
   
   This namespace maintains backward compatibility for routes.clj and other consumers
   while the actual implementation is split across:
   - markdownbrain.handlers.admin.auth (login, logout, init)
   - markdownbrain.handlers.admin.vaults (vault CRUD, notes, root-note)
   - markdownbrain.handlers.admin.logo (logo upload, delete, serve)
   - markdownbrain.handlers.admin.common (shared utilities)"
  (:require
   [markdownbrain.handlers.admin.auth :as auth]
   [markdownbrain.handlers.admin.common :as common]
   [markdownbrain.handlers.admin.logo :as logo]
   [markdownbrain.handlers.admin.vaults :as vaults]))

;; ============================================================
;; Auth handlers
;; ============================================================

(def init-admin auth/init-admin)
(def login auth/login)
(def logout auth/logout)
(def login-page auth/login-page)
(def init-page auth/init-page)

;; ============================================================
;; Vault handlers
;; ============================================================

(def admin-home vaults/admin-home)
(def list-vaults vaults/list-vaults)
(def create-vault vaults/create-vault)
(def update-vault vaults/update-vault)
(def delete-vault vaults/delete-vault)
(def search-vault-notes vaults/search-vault-notes)
(def update-vault-root-note vaults/update-vault-root-note)
(def get-root-note-selector vaults/get-root-note-selector)
(def renew-vault-sync-key vaults/renew-vault-sync-key)

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

(def serve-admin-asset common/serve-admin-asset)
(def admin-asset-url common/admin-asset-url)
(def format-storage-size common/format-storage-size)
