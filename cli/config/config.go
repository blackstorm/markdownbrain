package config

type Config struct {
	Source  string   `yaml:"source"`
	Ignores []string `yaml:"ignores"`
	Server  string   `yaml:"server"`
	APIKey  string   `yaml:"api_key"`
}
