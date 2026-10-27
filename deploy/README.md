# deploy/

Uruchomienie serwera na VM przez systemd. Opis krok po kroku: `docs/operations/deployment-vm-systemd.md`.
Obraz kontenera: `Dockerfile` w katalogu głównym.

```
deploy/
├── systemd/
│   ├── mcp-example.service         unit (konto mcp, sandbox systemd, logi do journald)
│   └── mcp-example.env.template    zmienne środowiska -> /etc/mcp-example/mcp-example.env
└── scripts/
    ├── install.sh                  pierwsza instalacja (konto, katalogi, unit)
    ├── update.sh                   podmiana jara z kopią do wycofania
    └── healthcheck.sh              /actuator/health po mTLS
```
