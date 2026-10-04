#!/usr/bin/env bash
# TEMPLATE — liveness check over plain HTTP (TLS is terminated at the nginx gateway).
# Adjust host. Exit non-zero on failure.
set -euo pipefail
HOST="${1:-http://localhost:8080}"
curl -fsS "$HOST/actuator/health" | grep -q '"status":"UP"'
echo "OK"
