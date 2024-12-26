package common

import (
	"os"

	"gopkg.in/yaml.v3"
)

func ParseYAMLConfig(filePath string, config any) error {
	content, err := os.ReadFile(filePath)
	if err != nil {
		return err
	}

	if err := yaml.Unmarshal(content, config); err != nil {
		return err
	}

	return nil
}
