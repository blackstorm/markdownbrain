.PHONY: build build-server build-cli

build: build-server build-cli

build-server:
	go build -o bin/markdownbrain server/main.go

build-cli:
	go build -o bin/markdownbrain-cli cli/main.go
