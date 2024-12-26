package builder

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/blackstorm/markdownbrain/common"
)

func TestObsidianBuilder(t *testing.T) {
	tmpDir, err := os.MkdirTemp("", "notes-test")
	if err != nil {
		t.Fatal(err)
	}
	defer os.RemoveAll(tmpDir)

	dbPath := filepath.Join(tmpDir, "test.db")
	db, err := common.NewDB(dbPath, true)
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()

	noteContent := `---
title: Test Note
created_at: 2024-01-01
---
# Test Note

This is a test note with a [[link]] to another note.`

	notePath := filepath.Join(tmpDir, "test.md")
	if err := os.WriteFile(notePath, []byte(noteContent), 0644); err != nil {
		t.Fatal(err)
	}

	builder := NewObsidianBuilder([]string{".git"}, db)

	if err := builder.Build(tmpDir); err != nil {
		t.Fatal(err)
	}

}
