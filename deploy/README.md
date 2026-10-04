# deploy/

Everything needed to run the MCP server directly on a VM via **systemd**, behind the nginx gateway
(TLS terminated there; the app serves plain HTTP on :8080).

```
deploy/
├── systemd/
│   ├── mcp-server.service        # hardened unit (non-root, sandboxed)
│   └── mcp-server.env.example    # secrets template -> /etc/mcp-server/mcp-server.env
└── scripts/
    ├── install.sh                # first-time provisioning (user, dirs, unit)
    ├── update.sh                 # rolling jar update with backup
    └── healthcheck.sh            # HTTP health probe
```

See `docs/operations/deployment-vm-systemd.md` for the full step-by-step runbook.
