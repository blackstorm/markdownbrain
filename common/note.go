package common

import (
	"strings"
)

type Note struct {
	ID            string `json:"id" db:"id"`
	Title         string `json:"title" db:"title"`
	Description   string `json:"description" db:"description"`
	HTMLContent   string `json:"html_content" db:"html_content"`
	CreatedAt     string `json:"created_at" db:"created_at"`
	LastUpdatedAt string `json:"last_updated_at" db:"last_updated_at"`
	LinkNoteIDs   string `json:"link_note_ids" db:"link_note_ids"`
	LinkToThis    []Note
}

func (n *Note) LoadLinkToThisNotes(db *DB) error {
	linkToThis, err := db.GetNotesByLinkTo(n.ID)
	if err != nil {
		return err
	}
	n.LinkToThis = linkToThis
	return nil
}

type Notes []Note

func (n Notes) Titles() string {
	titles := make([]string, len(n))
	for i, note := range n {
		titles[i] = note.Title
	}
	return strings.Join(titles, "|")
}
