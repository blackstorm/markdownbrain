#!/usr/bin/env sh
set -eu

token_file="${DATA_PATH:-/app/data}/.health-token"
if [ ! -s "$token_file" ]; then
  exit 1
fi

token="$(cat "$token_file")"
if [ -z "$token" ]; then
  exit 1
fi

curl -fsS -H "X-Health-Token: $token" "http://localhost:${CONSOLE_PORT:-9090}/console/health" >/dev/null
