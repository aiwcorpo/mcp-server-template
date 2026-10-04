#!/usr/bin/env bash
# TEMPLATE — rolling update of the jar. Run as root.
set -euo pipefail
APP_DIR=/opt/mcp-server
JAR_SRC="${1:?usage: update.sh <path-to-new.jar>}"
cp -a "$APP_DIR/mcp-server.jar" "$APP_DIR/mcp-server.jar.bak.$(date +%Y%m%d%H%M%S)"
install -o root -g mcp -m 0640 "$JAR_SRC" "$APP_DIR/mcp-server.jar"
systemctl restart mcp-server
systemctl --no-pager status mcp-server | head -n 15
