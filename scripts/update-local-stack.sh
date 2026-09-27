#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
project_dir=$(CDPATH= cd -- "$script_dir/.." && pwd)
cd "$project_dir"

docker compose up -d --build

backend_port=$(docker compose port backend 8080 | awk -F: 'NR == 1 { print $NF }')
if [ -z "$backend_port" ]; then
    echo "Could not determine the Backend port." >&2
    exit 1
fi
backend_url="http://localhost:$backend_port"

attempt=0
until curl -fsS "$backend_url/health" >/dev/null; do
    attempt=$((attempt + 1))
    if [ "$attempt" -ge 30 ]; then
        echo "Backend did not become ready within 30 seconds." >&2
        exit 1
    fi
    sleep 1
done

for sync_path in sync gambling/sync; do
    status=$(curl -sS -o /dev/null -w '%{http_code}' \
        -X POST \
        -H 'Content-Type: application/json' \
        --data '{' \
        "$backend_url/api/v1/$sync_path")
    if [ "$status" != "400" ]; then
        echo "Required API route /api/v1/$sync_path is unavailable (HTTP $status)." >&2
        exit 1
    fi
done

echo "Local stack is current and both sync APIs are available."
