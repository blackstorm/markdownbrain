#!/bin/sh
# Caddy entrypoint script that selects Caddyfile based on CADDY_ON_DEMAND_TLS_ENABLED

set -e

CADDYFILE_DIR="/etc/caddy"

if [ "$CADDY_ON_DEMAND_TLS_ENABLED" = "true" ]; then
    echo "On-demand TLS enabled, using Caddyfile.on-demand-tls"
    cp "${CADDYFILE_DIR}/Caddyfile.on-demand-tls" "${CADDYFILE_DIR}/Caddyfile"
else
    echo "On-demand TLS disabled, using Caddyfile.simple"
    cp "${CADDYFILE_DIR}/Caddyfile.simple" "${CADDYFILE_DIR}/Caddyfile"
fi

exec caddy run --config "${CADDYFILE_DIR}/Caddyfile" --adapter caddyfile
