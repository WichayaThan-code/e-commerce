#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export PATH="$HOME/Applications/Postgres.app/Contents/Versions/17/bin:$PATH"
PGDATA_DIR="${PGDATA:-$HOME/Library/Application Support/Postgres/var-17}"
case "${1:-status}" in
  start) pg_ctl -D "$PGDATA_DIR" -l "$PGDATA_DIR/postgresql.log" -o '-h 127.0.0.1 -p 5432 -k /private/tmp' start ;;
  stop) pg_ctl -D "$PGDATA_DIR" stop -m fast ;;
  status) pg_ctl -D "$PGDATA_DIR" status ;;
  *) echo "Usage: $0 {start|stop|status}"; exit 1 ;;
esac
