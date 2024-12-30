FROM golang:1.23-bookworm AS builder

WORKDIR /markdownbrain

COPY . .

RUN go build -o markdownbrain server/main.go

FROM debian:bookworm

WORKDIR /markdownbrain

COPY --from=builder /markdownbrain/server/www/static /markdownbrain/static
COPY --from=builder /markdownbrain/server/www/config.yml /markdownbrain/config.yml
COPY --from=builder /markdownbrain/markdownbrain /markdownbrain/markdownbrain

EXPOSE ${PORT:-3000}

CMD ["./markdownbrain"]
