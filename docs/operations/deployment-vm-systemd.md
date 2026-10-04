# Wdrożenie na VM (systemd) — runbook

Serwer uruchamiany bezpośrednio na maszynie wirtualnej jako usługa systemd, bez kontenera.
Aplikacja nasłuchuje po zwykłym HTTP na `:8080`; front stanowi **nginx reverse-proxy gateway**,
który terminuje TLS.

## Wymagania wstępne
- Linux z systemd, dostęp root/sudo.
- JRE/JDK 25 (`/usr/bin/java`).
- Zbudowany artefakt `mcp-server.jar` (z modułu `mcp-app`: `./mvnw -pl mcp-app -am package`).
- nginx gateway z terminacją TLS przed usługą (patrz `docs/security/tls.md`).

## Układ na hoście
```
/opt/mcp-server/mcp-server.jar         # artefakt (0640, owner root:mcp) — profile konfiguracji są w jarze
/etc/mcp-server/                       # środowisko usługi (0750)
└── mcp-server.env                     # sekrety + MCP_PROFILE=prod (0640, root:mcp) — NIE w repo
/var/log/mcp-server/                   # logi/audyt (0750, owner mcp)
```

Konfiguracja profili (`application*.yml`, `logback-spring.xml`) jest spakowana w jarze; produkcyjne
wartości wstrzykujesz przez zmienne środowiskowe (poniżej). Jeśli potrzebujesz nadpisań poza jarem,
uruchom z `--spring.config.additional-location=file:/etc/mcp-server/`.

## Kroki
1. **Konto i katalogi**: `deploy/scripts/install.sh /sciezka/mcp-server.jar` (tworzy usera `mcp`,
   katalogi, kopiuje unit). Przejrzyj skrypt przed uruchomieniem.
2. **Sekrety i profil**: `mcp-server.env.example` → `/etc/mcp-server/mcp-server.env`; ustaw
   `MCP_PROFILE=prod` oraz `EXAMPLE_BACKEND_BASE_URL`; `chown root:mcp`, `chmod 0640`.
3. **Start**: `systemctl enable --now mcp-server`.
4. **Weryfikacja**: `deploy/scripts/healthcheck.sh http://localhost:8080`
   oraz `journalctl -u mcp-server -f`. TLS sprawdzasz na nginx gateway.

## Aktualizacja
`deploy/scripts/update.sh /sciezka/nowy.jar` (backup starego jar + restart).

## Wycofanie (rollback)
Przywróć `mcp-server.jar.bak.<timestamp>` i `systemctl restart mcp-server`.

## Typowe problemy
- 8080 nie nasłuchuje → sprawdź `journalctl`; częsta przyczyna: błąd startu aplikacji lub zajęty port.
- 401/403 z backendu → brak lub nieważny PAT; sprawdź, czy klient wysyła nagłówek `X-PAT-<system>`.
