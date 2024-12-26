package common

import "testing"

func TestGenerator(t *testing.T) {
	generator := NewSqidsIdGenerator()

	tests := []struct {
		input    string
		expected string
	}{
		{"welcome", "nWd9WFI"},
		{"欢迎", "Q6YVebZ"},
		{"bienvenue", "psxRKiT"},
		{"むかえる", "4thJ4Q7"},
	}

	for _, tt := range tests {
		result := generator.Generate(tt.input)
		if result != tt.expected {
			t.Errorf("Generate(%s) = %s; want %s", tt.input, result, tt.expected)
		}
	}
}
