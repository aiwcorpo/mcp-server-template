#!/usr/bin/env bash
# TEMPLATE — first-time install on the VM. Review before running. Run as root.
set -euo pipefail

APP_USER=mcp
APP_DIR=/opt/mcp-server
CFG_DIR=/etc/mcp-server
LOG_DIR=/var/log/mcp-server
JAR_SRC="${1:?usage: install.sh <path-to-mcp-server.jar>}"

# 1) Dedicated non-login service account
id -u "$APP_USER" &>/dev/null || useradd --system --no-create-home --shell /usr/sbin/nologin "$APP_USER"

# 2) Directories
install -d -o root  -g "$APP_USER" -m 0750 "$APP_DIR"
install -d -o root  -g "$APP_USER" -m 0750 "$CFG_DIR"
install -d -o "$APP_USER" -g "$APP_USER" -m 0750 "$LOG_DIR"

# 3) Artifact + config
install -o root -g "$APP_USER" -m 0640 "$JAR_SRC" "$APP_DIR/mcp-server.jar"
# Copy application-*.yml, logback-spring.xml into $CFG_DIR (0640).
# Copy deploy/systemd/mcp-server.env.example -> $CFG_DIR/mcp-server.env (0640) and fill secrets.

# 4) systemd unit
install -o root -g root -m 0644 deploy/systemd/mcp-server.service /etc/systemd/system/mcp-server.service
systemctl daemon-reload
echo "Installed. Next: place the env file, then: systemctl enable --now mcp-server"
