module github.com/blackstorm/markdownbrain/cli

go 1.23.4

require (
	github.com/go-resty/resty/v2 v2.16.2
	github.com/grokify/html-strip-tags-go v0.1.0
	github.com/yuin/goldmark v1.7.8
	gopkg.in/yaml.v3 v3.0.1
)

require (
	golang.org/x/net v0.27.0 // indirect
	gopkg.in/check.v1 v1.0.0-20201130134442-10cb98267c6c // indirect
)

replace github.com/blockstorm/markdownbrain/common => ../common
