package common

import (
	"database/sql"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"sync"
	"time"

	"github.com/jmoiron/sqlx"
	_ "github.com/mattn/go-sqlite3"
)

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
	Path string
	pool *sqlx.DB
	mu   sync.RWMutex
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

	db, err := sqlx.Connect("sqlite3", connStr)
	if err != nil {
		return nil, err
	}

	// Create table if not exists
	_, err = db.Exec(_SCHEMA)
	if err != nil {
		return nil, err
	}

	return &DB{
		Path: path,
		pool: db,
	}, nil
}

func (db *DB) Close() error {
	db.mu.Lock()
	defer db.mu.Unlock()
	return db.pool.Close()
}

func (db *DB) FromBytes(bytes []byte) error {
	db.mu.Lock()
	defer db.mu.Unlock()

	// Close existing pool
	if db.pool != nil {
		db.pool.Close()
	}

	// Remove old db file
	if _, err := os.Stat(db.Path); err == nil {
		if err := os.Remove(db.Path); err != nil {
			return err
		}
	}

	// Write new bytes to file
	if err := os.WriteFile(db.Path, bytes, 0644); err != nil {
		return err
	}

	// Recreate pool with new file
	newPool, err := sqlx.Connect("sqlite3", fmt.Sprintf("file:%s?cache=shared&mode=rwc", db.Path))
	if err != nil {
		return err
	}
	db.pool = newPool

	return nil
}

func (db *DB) InsertNote(note *Note) error {
	db.mu.RLock()
	defer db.mu.RUnlock()

	_, err := db.pool.NamedExec(`
		INSERT INTO notes (id, title, description, html_content, created_at, last_updated_at, link_note_ids) 
		VALUES (:id, :title, :description, :html_content, :created_at, :last_updated_at, :link_note_ids)`,
		note,
	)
	return err
}

func (db *DB) GetNote(id string) (*Note, error) {
	db.mu.RLock()
	defer db.mu.RUnlock()

	note := &Note{}
	err := db.pool.Get(note, "SELECT * FROM notes WHERE id = ?", id)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, nil
		}
		return nil, err
	}
	return note, nil
}

func (db *DB) CountNote() (int64, error) {
	db.mu.RLock()
	defer db.mu.RUnlock()
	var count int64
	err := db.pool.Get(&count, "SELECT COUNT(*) FROM notes")
	if err != nil {
		return 0, err
	}
	return count, nil
}

func (db *DB) GetNotesByIDs(ids []string) ([]Note, error) {
	db.mu.RLock()
	defer db.mu.RUnlock()

	query, args, err := sqlx.In("SELECT * FROM notes WHERE id IN (?)", ids)
	if err != nil {
		return nil, err
	}

	// http://jmoiron.github.io/sqlx/#inQueries
	// sqlx.In returns queries with the MySQL placeholder (?), we need to rebind it
	// for SQLite
	query = db.pool.Rebind(query)

	notes := []Note{}
	err = db.pool.Select(&notes, query, args...)
	if err != nil {
		return nil, err
	}

	return notes, nil
}

func (db *DB) GetNotesByLinkTo(id string) ([]Note, error) {
	db.mu.RLock()
	defer db.mu.RUnlock()

	notes := []Note{}
	err := db.pool.Select(&notes, "SELECT * FROM notes WHERE EXISTS (SELECT 1 FROM json_each(link_note_ids) WHERE value = ?)", id)
	if err != nil {
		return nil, err
	}

	return notes, nil
}
