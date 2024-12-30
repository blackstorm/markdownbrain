package common

import (
	"database/sql"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"sync/atomic"
	"time"

	"github.com/jmoiron/sqlx"
	_ "modernc.org/sqlite"
)

var _DRIVER_NAME = "sqlite"

var _SCHEMA = `
CREATE TABLE IF NOT EXISTS notes (
	id TEXT PRIMARY KEY,
	title TEXT,
	description TEXT,
	html_content TEXT,
	created_at TEXT,
	last_updated_at TEXT,
	link_note_ids TEXT
)
`

type DB struct {
	Path    string
	connStr string
	pool    atomic.Pointer[sqlx.DB]
}

func NewDBWithTempDir() (*DB, error) {
	tempDir := os.TempDir()
	tempAt := time.Now().Unix()
	path := filepath.Join(tempDir, fmt.Sprintf("temp_%d.db", tempAt))

	return NewDB(path, false)
}

func NewDB(path string, readonly bool) (*DB, error) {
	if path == "" {
		tempDir := os.TempDir()
		tempAt := time.Now().Unix()
		path = filepath.Join(tempDir, fmt.Sprintf("temp_%d.db", tempAt))
	}

	mode := "rwc"
	args := ""
	if readonly {
		mode = "ro"
		args = "immutable=1&_query_only=1&_journal_mode=OFF"
	}

	connStr := fmt.Sprintf("file:%s?cache=shared&mode=%s&%s", path, mode, args)

	sqlxDB, err := sqlx.Connect(_DRIVER_NAME, connStr)
	if err != nil {
		return nil, err
	}

	// Create table if not exists
	_, err = sqlxDB.Exec(_SCHEMA)
	if err != nil {
		return nil, err
	}

	db := &DB{
		Path:    path,
		connStr: connStr,
	}
	// Store the connection pool
	db.pool.Store(sqlxDB)

	return db, nil
}

func (db *DB) Close() error {
	return db.pool.Load().Close()
}

func (db *DB) FromBytes(bytes []byte) error {
	// Get current connection pool
	oldPool := db.pool.Load()

	// Close old connection pool
	if oldPool != nil {
		oldPool.Close()
	}

	// Remove old file
	if _, err := os.Stat(db.Path); err == nil {
		if err := os.Remove(db.Path); err != nil {
			return err
		}
	}

	// Write new file
	if err := os.WriteFile(db.Path, bytes, 0644); err != nil {
		return err
	}

	// Create new connection pool
	newPool, err := sqlx.Connect(_DRIVER_NAME, db.connStr)
	if err != nil {
		return err
	}

	// Atomically replace the connection pool
	db.pool.Store(newPool)
	return nil
}

// Helper method: get current connection pool
func (db *DB) getPool() *sqlx.DB {
	return db.pool.Load()
}

func (db *DB) InsertNote(note *Note) error {
	_, err := db.getPool().NamedExec(`
		INSERT INTO notes (id, title, description, html_content, created_at, last_updated_at, link_note_ids) 
		VALUES (:id, :title, :description, :html_content, :created_at, :last_updated_at, :link_note_ids)`,
		note,
	)
	return err
}

func (db *DB) GetNote(id string) (*Note, error) {
	note := &Note{}
	err := db.getPool().Get(note, "SELECT * FROM notes WHERE id = ?", id)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, nil
		}
		return nil, err
	}
	return note, nil
}

func (db *DB) CountNote() (int64, error) {
	var count int64
	err := db.getPool().Get(&count, "SELECT COUNT(*) FROM notes")
	if err != nil {
		return 0, err
	}
	return count, nil
}

func (db *DB) GetNotesByIDs(ids []string) ([]Note, error) {
	query, args, err := sqlx.In("SELECT * FROM notes WHERE id IN (?)", ids)
	if err != nil {
		return nil, err
	}

	// http://jmoiron.github.io/sqlx/#inQueries
	// sqlx.In returns queries with the MySQL placeholder (?), we need to rebind it
	// for SQLite
	query = db.getPool().Rebind(query)

	notes := []Note{}
	err = db.getPool().Select(&notes, query, args...)
	if err != nil {
		return nil, err
	}

	return notes, nil
}

func (db *DB) GetNotesByLinkTo(id string) ([]Note, error) {
	notes := []Note{}
	err := db.getPool().Select(&notes, "SELECT * FROM notes WHERE EXISTS (SELECT 1 FROM json_each(link_note_ids) WHERE value = ?)", id)
	if err != nil {
		return nil, err
	}

	return notes, nil
}
