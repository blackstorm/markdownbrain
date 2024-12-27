.PHONY: build build-server build-cli

build: build-server build-cli

build-server:
	CGO_ENABLED=1 go build -o bin/markdownbrain server/main.go

build-cli:
	CGO_ENABLED=1 go build -o bin/markdownbrain-cli cli/main.go
