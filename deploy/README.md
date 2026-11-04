# deploy/

Running the server on a VM under systemd. Step by step: `docs/operations/deployment-vm-systemd.md` (in Polish).
The container image is built from the `Dockerfile` in the repository root.

```
deploy/
|-- systemd/
|   |-- mcp-example.service         unit (account mcp, systemd sandbox, logs to journald)
|   `-- mcp-example.env.template    environment variables -> /etc/mcp-example/mcp-example.env
`-- scripts/
    |-- install.sh                  first installation (account, directories, unit)
    |-- update.sh                   replaces the jar and keeps a copy for rollback
    `-- healthcheck.sh              /actuator/health over mTLS
```
