FROM golang:1.23.4-bookworm AS builder

WORKDIR /markdownbrain

COPY . .

RUN CGO_ENABLED=1 go build -o markdownbrain server/main.go

FROM debian:bookworm

WORKDIR /markdownbrain

RUN apt-get update && apt-get install -y ca-certificates sqlite3 && apt-get clean

COPY --from=builder /markdownbrain/server/www/static /markdownbrain/static
COPY --from=builder /markdownbrain/server/www/config.yml /markdownbrain/config.yml
COPY --from=builder /markdownbrain/markdownbrain /markdownbrain/markdownbrain

CMD ["./markdownbrain"]
