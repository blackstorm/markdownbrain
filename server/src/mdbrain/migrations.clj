(ns mdbrain.migrations
  (:require [mdbrain.db :as db]
            [migratus.core :as migratus]
            [clojure.tools.logging :as log]))

(defn migrate!
  "Run all pending migrations."
  []
  (log/info "Running database migrations...")
  (migratus/migrate (db/migratus-config))
  (log/info "Migrations complete."))

(defn pending-list
  "List pending migrations."
  []
  (migratus/pending-list (db/migratus-config)))

(defn create-migration
  "Create a new migration file with the given name."
  [name]
  (migratus/create (db/migratus-config) name))

(defn -main
  "CLI entry point for migrations.
   Usage: clojure -M -m mdbrain.migrations <command>
   Commands:
     migrate  - Run all pending migrations
     pending  - List pending migrations
     create   - Create a new migration (requires name argument)"
  [& [cmd & args]]
  (case cmd
    "migrate" (migrate!)
    "pending" (println (pending-list))
    "create"  (if (first args)
                (create-migration (first args))
                (do (println "Error: migration name required")
                    (System/exit 1)))
    (do (println "Usage: clojure -M -m mdbrain.migrations <command>")
        (println "Commands: migrate, pending, create <name>")
        (System/exit 1))))
