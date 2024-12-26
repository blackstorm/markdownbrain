package main

import (
	"bytes"
	"flag"
	"fmt"
	"log"
	"os"
	"path/filepath"
	"time"

	"github.com/blackstorm/markdownbrain/cli/builder"
	"github.com/blackstorm/markdownbrain/cli/config"
	"github.com/blackstorm/markdownbrain/common"
	"github.com/go-resty/resty/v2"
)

type Args struct {
	ConfigPath string
}

func main() {
	log.SetFlags(log.LstdFlags | log.Lshortfile)

	args, err := parseArgs()
	if err != nil {
		panic(err)
	}

	config, err := loadConfig(args.ConfigPath)
	if err != nil {
		log.Fatalf("Failed to load config: %v", err)
	}

	db, err := common.NewDBWithTempDir()
	if err != nil {
		log.Fatalf("Failed to create database: %v", err)
	}
	log.Printf("Temporary database at: %s", db.Path)

	// 构建数据库
	log.Printf("Building database...")
	buildStart := time.Now()
	builder := builder.NewObsidianBuilder(mixIgnores(config), db)
	if err := builder.Build(config.Source); err != nil {
		log.Fatalf("Failed to build database: %v", err)
	}
	log.Printf("Database built in %v", time.Since(buildStart))

	// 同步到服务器
	log.Printf("Syncing to server...")
	syncStart := time.Now()
	if err := syncToServer(config, db); err != nil {
		log.Fatalf("Failed to sync to server: %v", err)
	}
	log.Printf("Synced to server in %v", time.Since(syncStart))
}

func mixIgnores(config *config.Config) []string {
	ignores := make(map[string]bool)
	defaultIgnores := []string{".git", ".obsidian", ".DS_Store"}
	for _, ignore := range defaultIgnores {
		ignores[ignore] = true
	}
	for _, ignore := range config.Ignores {
		ignores[ignore] = true
	}

	res := make([]string, 0)
	for item, ok := range ignores {
		if ok {
			res = append(res, item)
		}
	}

	return res
}

func syncToServer(config *config.Config, db *common.DB) error {
	client := resty.New()

	content, err := os.ReadFile(db.Path)
	if err != nil {
		return fmt.Errorf("failed to read database file: %w", err)
	}

	resp, err := client.R().
		SetHeader("Authorization", fmt.Sprintf("Bearer %s", config.APIKey)).
		SetFileReader("db", "notes.db", bytes.NewReader(content)).
		Post(fmt.Sprintf("%s/api/sync", config.Server))

	if err != nil {
		return fmt.Errorf("failed to send request: %w", err)
	}

	// 检查响应状态
	if !resp.IsSuccess() {
		return fmt.Errorf("server returned error status %d: %s", resp.StatusCode(), resp.String())
	}

	return nil
}

func parseArgs() (*Args, error) {
	homeDir, err := os.UserHomeDir()
	if err != nil {
		return nil, err
	}

	path := filepath.Join(homeDir, "/markdownbrain/config.yml")

	var args Args
	flag.StringVar(&args.ConfigPath, "config", path, "CLI config path.")

	flag.Parse()

	return &args, nil
}

func loadConfig(path string) (*config.Config, error) {
	if path[:2] == "~/" {
		homeDir, err := os.UserHomeDir()
		if err != nil {
			return nil, fmt.Errorf("failed to get home directory: %w", err)
		}
		path = filepath.Join(homeDir, path[2:])
	}

	var conf config.Config

	if err := common.ParseYAMLConfig(path, &conf); err != nil {
		return nil, err
	}

	return &conf, nil
}
