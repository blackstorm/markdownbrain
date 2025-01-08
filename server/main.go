package main

import (
	"embed"
	"fmt"
	"io"
	"log"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strings"

	"github.com/blackstorm/markdownbrain/common"
	"github.com/blackstorm/markdownbrain/server/config"
	"github.com/gofiber/fiber/v2"
	"github.com/gofiber/fiber/v2/middleware/favicon"
	"github.com/gofiber/template/django/v3"
	_ "github.com/joho/godotenv/autoload"
)

type AppState struct {
	db         *common.DB
	config     *config.Config
	rootNoteId string
}

//go:embed templates
var templatesAssets embed.FS

func main() {
	devMode := os.Getenv("DEV_MODE") == "true"

	workspace := "/markdownbrain"
	if devMode {
		currentDir, err := os.Getwd()
		if err != nil {
			panic(err)
		}
		workspace = filepath.Join(currentDir, "www")
	}

	configPath := filepath.Join(workspace, "config.yml")
	dbPath := filepath.Join(workspace, "/data/notes.db")

	log.Printf("Workspace path: %s", workspace)
	log.Printf("Config path: %s", configPath)
	log.Printf("Database path: %s", dbPath)

	config, err := loadConfig(configPath)
	if err != nil {
		panic(err)
	}

	db, err := common.NewDB(dbPath, true)
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
		File: filepath.Join(workspace, "/static/favicon.ico"),
		URL:  "/favicon.ico",
	}))

	app.Static("/static", filepath.Join(workspace, "static"))
	app.Get("/", withAppState(state, home))
	app.Post("/api/sync", withAuthorization(state, sync))
	app.Get("/:id", withAppState(state, note))
	app.Get("/*", withAppState(state, notes))

	port := os.Getenv("PORT")
	if port == "" {
		port = "3000"
	}

	app.Listen(fmt.Sprintf(":%s", port))
}

// loadConfig loads the application configuration from the specified www path
func loadConfig(configPath string) (*config.Config, error) {
	var conf config.Config
	if err := common.ParseYAMLConfig(configPath, &conf); err != nil {
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

		data := templateValues(state, fiber.Map{
			"root_note_name": state.config.RootNoteName,
			"is_notes_empty": is_notes_empty,
		})

		return c.Render("welcome", data)
	}

	data := templateValues(state, fiber.Map{
		"title":       state.config.Name,
		"description": state.config.Description,
		"notes":       []common.Note{*note},
	})

	return c.Render("home", data)
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

		linkToThis, err := state.db.GetNotesByLinkTo(noteId)
		if err != nil {
			return fiber.ErrInternalServerError
		}
		note.LinkToThis = linkToThis

		c.Set("HX-Push-Url", pushURL.String())
		return c.Render("note", fiber.Map{
			"note": note,
		})
	}

	note.LoadLinkToThisNotes(state.db)

	data := templateValues(state, fiber.Map{
		"title":       note.Title,
		"description": note.Description,
		"notes":       []common.Note{*note},
	})

	return c.Render("home", data)
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

	for i := range notes {
		notes[i].LoadLinkToThisNotes(state.db)
	}

	title := fmt.Sprintf("%s - %s", common.Notes(notes).Titles(), state.config.Name)

	data := templateValues(state, fiber.Map{
		"title":       title,
		"description": title,
		"notes":       notes,
	})

	return c.Render("home", data)
}

func templateValues(state *AppState, values fiber.Map) fiber.Map {
	res := fiber.Map{
		"config": fiber.Map{
			"lang":        state.config.Lang,
			"name":        state.config.Name,
			"description": state.config.Description,
			"templates":   state.config.Templates,
		},
	}

	for key, value := range values {
		res[key] = value
	}

	return res
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
