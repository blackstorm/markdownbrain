package config

type Config struct {
	Name         string   `yaml:"name"`
	Description  string   `yaml:"description"`
	Lang         string   `yaml:"lang"`
	RootNoteName string   `yaml:"root_note_name"`
	APIKey       string   `yaml:"api_key"`
	HtmxJsUrl    string   `yaml:"htmx_js_url"`
	Templates    []string `yaml:"templates"`
}
