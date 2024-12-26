package main

import (
	"embed"
	"flag"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"path/filepath"
	"strings"

	"github.com/blackstorm/markdownbrain/common"
	"github.com/blackstorm/markdownbrain/server/config"
	"github.com/gofiber/fiber/v2"
	"github.com/gofiber/fiber/v2/middleware/favicon"
	"github.com/gofiber/template/django/v3"
)

type Args struct {
	WWW string
}

type AppState struct {
	db         *common.DB
	config     *config.Config
	rootNoteId string
}

//go:embed templates
var templatesAssets embed.FS

func main() {
	var args Args
	flag.StringVar(&args.WWW, "config", ".", "CLI config path.")
	flag.Parse()

	config, err := loadConfig(args.WWW)
	if err != nil {
		panic(err)
	}

	db, err := common.NewDB(filepath.Join(args.WWW, "/data/notes.db"), true)
	if err != nil {
		panic(err)
	}

	rootNoteHashId := common.NewSqidsIdGenerator().Generate(config.RootNoteName)
	state := &AppState{
		db:         db,
		config:     config,
		rootNoteId: rootNoteHashId,
	}

	// Use views example: django.New("./templates", ".html"),
	app := fiber.New(fiber.Config{
		Views: django.NewPathForwardingFileSystem(http.FS(templatesAssets), "/templates", ".html"),
	})

	app.Use(favicon.New(favicon.Config{
		File: filepath.Join(args.WWW, "/static/favicon.ico"),
		URL:  "/favicon.ico",
	}))

	app.Static("/static", filepath.Join(args.WWW, "static"))
	app.Get("/", withAppState(state, home))
	app.Post("/api/sync", withAuthorization(state, sync))
	app.Get("/:id", withAppState(state, note))
	app.Get("/*", withAppState(state, notes))

	app.Listen(fmt.Sprintf(":%d", config.Port))
}

// loadConfig loads the application configuration from the specified www path
func loadConfig(wwwPath string) (*config.Config, error) {
	var conf config.Config
	path := filepath.Join(wwwPath, "config.yml")
	if err := common.ParseYAMLConfig(path, &conf); err != nil {
		return nil, err
	}
	return &conf, nil
}

// home handles the root path ("/") request
// It either shows the welcome page for empty notes or renders the root note
func home(state *AppState, c *fiber.Ctx) error {
	note, err := state.db.GetNote(state.rootNoteId)
	if err != nil {
		return fiber.ErrInternalServerError
	}

	if note == nil {
		count, err := state.db.CountNote()
		if err != nil {
			return c.Render("500", fiber.Map{})
		}

		is_notes_empty := "true"
		if count > 0 {
			is_notes_empty = "false"
		}

		return c.Render("welcome", fiber.Map{
			"root_note_name": state.config.RootNoteName,
			"is_notes_empty": is_notes_empty,
		})
	}

	notes := []common.Note{*note}

	return c.Render("home", fiber.Map{
		"title":       state.config.Name,
		"name":        state.config.Name,
		"lang":        state.config.Lang,
		"description": state.config.Description,
		"notes":       notes,
	})
}

// note handles single note display requests
// It supports both regular HTTP requests and HTMX requests with proper URL handling
func note(state *AppState, c *fiber.Ctx) error {
	noteId := c.Params("id")
	fromNoteId := c.Get("X-From-Note-Id")
	currentURL := c.Get("HX-Current-URL")

	isHtmxReq := c.Get("HX-Request") == "true"

	if isHtmxReq && (fromNoteId == "" || currentURL == "") {
		return fiber.ErrBadRequest
	}

	note, err := state.db.GetNote(noteId)
	if err != nil {
		return fiber.ErrInternalServerError
	}

	if note == nil {
		return fiber.ErrNotFound
	}

	if isHtmxReq {
		var pushURL strings.Builder

		parsedURL, err := url.Parse(currentURL)
		if err != nil {
			return fiber.ErrBadRequest
		}
		currentPath := parsedURL.Path

		if currentPath == "/" {
			pushURL.WriteString(fmt.Sprintf("/%s/%s", state.rootNoteId, noteId))
		} else {
			pathParts := strings.Split(currentPath, "/")
			for _, part := range pathParts {
				if part == "" {
					continue
				}
				pushURL.WriteString("/")
				pushURL.WriteString(part)
				if part == fromNoteId {
					pushURL.WriteString("/")
					pushURL.WriteString(noteId)
					break
				}
			}
		}

		c.Set("HX-Push-Url", pushURL.String())
		return c.Render("note", fiber.Map{
			"note": note,
		})
	}

	notes := []common.Note{*note}
	return c.Render("home", fiber.Map{
		"title":       note.Title,
		"name":        state.config.Name,
		"lang":        state.config.Lang,
		"description": note.Description,
		"notes":       notes,
	})
}

// notes handles requests for displaying multiple notes
// It processes the path to extract note IDs and renders them in a combined view
func notes(state *AppState, c *fiber.Ctx) error {
	// Deduplication note id
	seen := make(map[string]bool)
	ids := make([]string, 0)
	for _, id := range strings.Split(c.Path(), "/") {
		if id != "" && !seen[id] {
			seen[id] = true
			ids = append(ids, id)
		}
	}

	notes, err := state.db.GetNotesByIDs(ids)
	if err != nil {
		return fiber.ErrInternalServerError
	}

	// Reorder notes according to ids order
	orderedNotes := make([]common.Note, len(ids))
	noteMap := make(map[string]common.Note)
	for _, note := range notes {
		noteMap[note.ID] = note
	}
	for i, id := range ids {
		if note, ok := noteMap[id]; ok {
			orderedNotes[i] = note
		}
	}
	notes = orderedNotes

	title := fmt.Sprintf("%s - %s", common.Notes(notes).Titles(), state.config.Name)

	return c.Render("home", fiber.Map{
		"title":       title,
		"name":        state.config.Name,
		"lang":        state.config.Lang,
		"description": title,
		"notes":       notes,
	})
}

// sync handles database synchronization requests
// It accepts a database file upload and updates the server's database
func sync(state *AppState, c *fiber.Ctx) error {
	file, err := c.FormFile("db")
	if err != nil {
		return fiber.ErrBadRequest
	}

	uploadedFile, err := file.Open()
	if err != nil {
		return fiber.ErrInternalServerError
	}
	defer uploadedFile.Close()

	bytes, err := io.ReadAll(uploadedFile)
	if err != nil {
		return fiber.ErrInternalServerError
	}

	if err := state.db.FromBytes(bytes); err != nil {
		return fiber.ErrInternalServerError
	}

	return nil
}

type withStateHandler func(state *AppState, c *fiber.Ctx) error

// withAppState is a middleware that injects the application state into request handlers
func withAppState(state *AppState, handler withStateHandler) fiber.Handler {
	return func(c *fiber.Ctx) error {
		return handler(state, c)
	}
}

// withAuthorization is a middleware that validates API key authentication
// It checks for a valid Bearer token in the Authorization header
func withAuthorization(state *AppState, handler withStateHandler) fiber.Handler {
	return func(c *fiber.Ctx) error {
		apiKey := c.Get("Authorization")
		if apiKey == "" {
			return fiber.ErrUnauthorized
		}

		apiKey = strings.TrimPrefix(apiKey, "Bearer ")

		if apiKey != state.config.APIKey {
			return fiber.ErrUnauthorized
		}

		return handler(state, c)
	}
}
