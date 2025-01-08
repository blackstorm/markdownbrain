package builder

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"strings"

	"github.com/blackstorm/markdownbrain/common"
	strip "github.com/grokify/html-strip-tags-go"
	"github.com/yuin/goldmark"
	"github.com/yuin/goldmark/extension"
	"github.com/yuin/goldmark/renderer/html"
	"gopkg.in/yaml.v3"
)

type Error struct {
	Message string
	Err     error
}

func (e *Error) Error() string {
	return fmt.Sprintf("%s: %v", e.Message, e.Err)
}

type NoteData struct {
	ID            string
	Title         string
	Description   string
	FilePath      string
	CreatedAt     string
	LastUpdatedAt string
}

type ObsidianBuilder struct {
	idGenerator *common.IdGenerator
	ignores     map[string]bool
	db          *common.DB
	md          goldmark.Markdown
}

func NewObsidianBuilder(ignores []string, db *common.DB) *ObsidianBuilder {
	ignoremap := make(map[string]bool)
	for _, i := range ignores {
		ignoremap[i] = true
	}

	md := goldmark.New(
		goldmark.WithExtensions(extension.GFM),
		goldmark.WithRendererOptions(
			html.WithUnsafe(),
			html.WithHardWraps(),
		),
	)

	return &ObsidianBuilder{
		idGenerator: common.NewSqidsIdGenerator(),
		ignores:     ignoremap,
		db:          db,
		md:          md,
	}
}

// Build build from a note source dir
func (b *ObsidianBuilder) Build(src string) error {
	srcPath := b.resolveHomePath(src)

	info, err := os.Stat(srcPath)
	if err != nil {
		return &Error{"Failed to access source path", err}
	}

	if !info.IsDir() {
		return &Error{"Source path is not a directory", nil}
	}

	notes, err := b.collectNotes(srcPath)
	if err != nil {
		return err
	}

	return b.processNotes(notes)
}

// collectNotes collect src path all
func (b *ObsidianBuilder) collectNotes(srcPath string) (map[string]*NoteData, error) {
	notes := make(map[string]*NoteData)

	err := filepath.Walk(srcPath, func(path string, info os.FileInfo, err error) error {
		if err != nil {
			return err
		}

		for component := range b.ignores {
			if strings.Contains(path, component) {
				return nil
			}
		}

		if !info.IsDir() && strings.HasSuffix(strings.ToLower(path), ".md") {
			note, err := b.getNoteMetadata(path)
			if err != nil {
				return err
			}

			filename := strings.TrimSuffix(filepath.Base(path), filepath.Ext(path))
			notes[filename] = note
		}

		return nil
	})

	if err != nil {
		return nil, &Error{"Failed to collect notes", err}
	}

	return notes, nil
}

func (b *ObsidianBuilder) processNotes(notes map[string]*NoteData) error {
	for _, data := range notes {
		content, err := os.ReadFile(data.FilePath)
		if err != nil {
			return &Error{"Failed to read note content", err}
		}

		// remove YAML frontmatter
		contentStr := string(content)
		if strings.HasPrefix(contentStr, "---") {
			if idx := strings.Index(contentStr[3:], "---"); idx != -1 {
				contentStr = contentStr[idx+6:]
			}
		}

		htmlContent, linkIDs, err := b.processContent(data.ID, contentStr, notes)
		if err != nil {
			return &Error{"Failed to process note content", err}
		}

		linkIDsJSON, err := json.Marshal(linkIDs)
		if err != nil {
			return errors.New("failed to marshal link IDs")
		}

		if data.Description == "" {
			htmlContentStripped := strings.ReplaceAll(strip.StripTags(htmlContent), "\n", "")
			data.Description = string([]rune(htmlContentStripped)[:common.MinInt(len([]rune(htmlContentStripped)), 100)])
		}

		note := &common.Note{
			ID:            data.ID,
			Title:         data.Title,
			Description:   data.Description,
			HTMLContent:   htmlContent,
			CreatedAt:     data.CreatedAt,
			LastUpdatedAt: data.LastUpdatedAt,
			LinkNoteIDs:   string(linkIDsJSON),
		}

		if err := b.db.InsertNote(note); err != nil {
			return &Error{"Failed to insert note into database", err}
		}
	}

	return nil
}

// processContent process note markdown to html and convert link
func (b *ObsidianBuilder) processContent(noteID string, content string, notes map[string]*NoteData) (string, []string, error) {
	linkIDs := make([]string, 0)
	processed := b.processLinks(noteID, content, notes, &linkIDs)

	htmlContent, err := b.markdownToHTML(processed)
	if err != nil {
		return "", nil, err
	}

	return htmlContent, linkIDs, nil
}

// processLinks process [[link]] syntax
func (b *ObsidianBuilder) processLinks(noteID string, content string, notes map[string]*NoteData, linkIDs *[]string) string {
	re := regexp.MustCompile(`\[\[([^\[\]]+)\]\]`)

	return re.ReplaceAllStringFunc(content, func(match string) string {
		inner := match[2 : len(match)-2]
		name, display := b.parseLinkParts(inner)

		if note, ok := notes[name]; ok {
			*linkIDs = append(*linkIDs, note.ID)
			return b.createLinkHTML(noteID, note.ID, display)
		}

		return display
	})
}

// parseLinkParts parse double [] link and alias
func (b *ObsidianBuilder) parseLinkParts(content string) (name string, display string) {
	parts := strings.Split(content, "|")

	if len(parts) == 1 {
		name = filepath.Base(parts[0])
		return strings.TrimSpace(name), strings.TrimSpace(name)
	}

	name = filepath.Base(parts[0])
	return strings.TrimSpace(name), strings.TrimSpace(parts[1])
}

// createLinkHTML create htmx link
func (b *ObsidianBuilder) createLinkHTML(fromID string, toID string, display string) string {
	return fmt.Sprintf(`<a class="note-link" href="/%s" id="note-link" `+
		`hx-get="/%s" hx-headers='{"X-From-Note-Id": "%s"}' `+
		`hx-target="#note-%s" hx-swap="afterend">%s</a>`,
		toID, toID, fromID, fromID, display)
}

// getNoteMetadata read note content meta data
func (b *ObsidianBuilder) getNoteMetadata(path string) (*NoteData, error) {
	content, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}

	info, err := os.Stat(path)
	if err != nil {
		return nil, err
	}

	filename := strings.TrimSuffix(filepath.Base(path), filepath.Ext(path))

	note := &NoteData{
		Title:         filename,
		FilePath:      path,
		CreatedAt:     info.ModTime().Format("2006-01-02"),
		LastUpdatedAt: info.ModTime().Format("2006-01-02"),
	}

	if bytes.HasPrefix(content, []byte("---")) {
		if idx := bytes.Index(content[3:], []byte("---")); idx != -1 {
			var meta struct {
				Title         string `yaml:"title"`
				Description   string `yaml:"description"`
				CreatedAt     string `yaml:"created_at"`
				LastUpdatedAt string `yaml:"last_updated_at"`
			}
			// If note contains metadata, then use the custom metadata.
			if err := yaml.Unmarshal(content[3:idx+3], &meta); err == nil {
				if meta.Title != "" {
					note.Title = meta.Title
				}
				if meta.Description != "" {
					note.Description = meta.Description
				}
				if meta.CreatedAt != "" {
					note.CreatedAt = meta.CreatedAt
				}
				if meta.LastUpdatedAt != "" {
					note.LastUpdatedAt = meta.LastUpdatedAt
				}
			}
		}
	}

	// Generate note id by filename without file ext.
	note.ID = b.idGenerator.Generate(filename)
	return note, nil
}

func (b *ObsidianBuilder) resolveHomePath(path string) string {
	if strings.HasPrefix(path, "~") {
		home, err := os.UserHomeDir()
		if err != nil {
			return path
		}
		return filepath.Join(home, path[1:])
	}
	return path
}

func (b *ObsidianBuilder) markdownToHTML(content string) (string, error) {
	var buf bytes.Buffer
	if err := b.md.Convert([]byte(content), &buf); err != nil {
		return "", err
	}
	return buf.String(), nil
}
