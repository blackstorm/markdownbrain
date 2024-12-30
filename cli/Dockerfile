FROM golang:1.23-bookworm AS builder

WORKDIR /markdownbrain

COPY . .

RUN go build -o markdownbrain-cli cli/main.go

FROM debian:bookworm

WORKDIR /markdownbrain

RUN apt-get update && apt-get install -y ca-certificates sqlite3 && apt-get clean

COPY --from=builder /markdownbrain/cli/config.yml.example /markdownbrain/config.yml

CMD ["./markdownbrain-cli", "-config", "/markdownbrain/config.yml"]
