package config

type Config struct {
	Port         uint16 `yaml:"port"`
	Name         string `yaml:"name"`
	Description  string `yaml:"description"`
	Lang         string `yaml:"lang"`
	RootNoteName string `yaml:"root_note_name"`
	APIKey       string `yaml:"api_key"`
}
