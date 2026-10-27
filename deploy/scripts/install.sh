#!/usr/bin/env bash
# First installation on a VM, as root: install.sh <mcp-example.jar>. Review before running.
set -euo pipefail

APP_USER=mcp
APP_DIR=/opt/mcp-example
CFG_DIR=/etc/mcp-example
DEPLOY_DIR="$(cd "$(dirname "$0")/.." && pwd)"
JAR_SRC="${1:?usage: install.sh <path-to-mcp-example.jar>}"

id -u "$APP_USER" &>/dev/null || useradd --system --no-create-home --shell /usr/sbin/nologin "$APP_USER"

install -d -o root -g "$APP_USER" -m 0750 "$APP_DIR" "$CFG_DIR" "$CFG_DIR/tls"
install -o root -g "$APP_USER" -m 0640 "$JAR_SRC" "$APP_DIR/mcp-example.jar"
# The environment file holds passwords: created once from the template, never overwritten.
if [[ ! -e "$CFG_DIR/mcp-example.env" ]]; then
    install -o root -g "$APP_USER" -m 0640 "$DEPLOY_DIR/systemd/mcp-example.env.template" "$CFG_DIR/mcp-example.env"
fi
install -o root -g root -m 0644 "$DEPLOY_DIR/systemd/mcp-example.service" /etc/systemd/system/mcp-example.service
systemctl daemon-reload
echo "Installed. Next: fill $CFG_DIR/mcp-example.env, put keystore.p12 and truststore.p12 into $CFG_DIR/tls,"
echo "then: systemctl enable --now mcp-example"
