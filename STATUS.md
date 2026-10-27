# STATUS - mcp-server-template (ws-03)

M1: osiągnięty (2026-10-27) - template na starterze 3.0.0-alpha-3, `get_record` z poświadczeniem z C3, 401 bez JWT, `new-server.sh demo` buduje się i przechodzi testy bez zmian ręcznych.

Starter: BOM `pl.aiwcorpo.mcp:mcp-platform-bom:3.0.0-alpha-3` (kontrakty 2.0.2, Spring Boot 4.1.0, Spring AI
2.0.0, Java 25). M2 nie jest zaczęty.

## Co działa

- Migracja z 2.0.0: usunięte OIDC, `guard-policy-local.yml`, `GuardedToolsTest`, `TestIdentityProvider`, tryb
  PAT, mock w pamięci i trzy narzędzia `example_*`. Pakiety przeniesione do `pl.aiwcorpo.mcp.example.*`
  (wcześniej `pl.aiwcorpo.mcp.*`, co przy skanowaniu łapało też pakiety startera), artefakty `mcp-example-*`.
- `get_record(recordId)`, `READ_ONLY`: walidacja (`InvalidToolArgumentException`), adapter z
  `CredentialContext.require("example")` + `credential::applyTo`, timeouty (2 s / 10 s), bez przekierowań,
  błędy systemu bez treści; 404 i 403 -> `not_found_or_denied` (ten sam tekst), 401 -> `credential_required`.
- Konfiguracja bazowa produkcyjna: mTLS (`client-auth: need`, bundle z `MCP_TLS_*`), JWKS po TLS z CA
  wewnętrznym, `jti` w Redisie (`MCP_REDIS_*`); profil `dev` tylko dla testów (bez TLS).
- Testy (`./mvnw verify`, JDK 25, bez Dockera): 11, 0 błędów.
  - `GetRecordToolTest` (8): poświadczenie z C3 dochodzi do backendu, `readOnlyHint`, brak poświadczenia i 401
    systemu, 404 = 403, zły `recordId` nie dochodzi do backendu, adapter bez poświadczenia poza żądaniem,
    poświadczenie i treść błędu backendu nie trafiają do wyniku ani logu.
  - `GatewayOnlyAccessTest` (3): bez JWT 401 dla `tools/call`, `ping`, `initialize` (także z podrzuconym
    `X-MCP-Credential`); JWT innego serwera, innego narzędzia, wariant serwisowy, przeterminowany -> 401; bez
    JWT tylko health.
  - `ExampleServiceTest` (2).
- Smoke konfiguracji produkcyjnej na hoście z `mcp-platform-dev` (certyfikat `mcp-jira`, Redis 16379): start,
  `/actuator/health` po mTLS z certyfikatem gateway'a 200, `POST /mcp` bez JWT 401, bez certyfikatu klienta
  handshake zerwany. Pełnego przebiegu z prawdziwym gateway'em nie robiłem (DoD briefu, test integracyjny z
  ws-01; plan na M2).
- `new-server.sh <system> [katalog]` sprawdzony w katalogu tymczasowym poza repo dla `demo`, `jira` i `gitlab`:
  każdy `./mvnw verify` od zera (świeży `git init`), 11 testów, 0 błędów, w projekcie nie zostaje `example`.
  Katalog tymczasowy usunięty.
- Dockerfile (obraz pod usługę `mcp-<system>` w profilu `full` compose), unit systemd i skrypty `deploy/`
  przepisane na mTLS i nowe zmienne.
- `docs/jak-napisac-narzedzie.md` (zasady z briefu), README z 4 modułami i miejscem każdej rzeczy.

## Jak wygenerować serwer (dla ws-04)

```bash
cd <workspace>/mcp-server-template
./new-server.sh jira                       # -> ../mcp-server-jira (katalog nie może istnieć)
cd ../mcp-server-jira && git init
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@25/25.0.4.1/libexec/openjdk.jdk/Contents/Home
./mvnw verify                              # 11 testów, przechodzi bez zmian
```

Skrypt zmienia `example`/`Example`/`EXAMPLE` na `<system>`/`<System>`/`<SYSTEM>` w treści i ścieżkach: groupId
i pakiety `pl.aiwcorpo.mcp.<system>`, `server-id` `mcp-<system>`, jar `mcp-<system>.jar`,
`CredentialContext.require("<system>")`, `MCP_<SYSTEM>_URL`, `<system>.backend.*`, klasy `<System>Record`,
`<System>Tool`, ... Endpoint zostaje `/mcp`. Nie kopiuje `new-server.sh`, `STATUS.md` i `docs/nowy-serwer.md`.
Pełna instrukcja i tabela zamian: `docs/nowy-serwer.md`.

Co serwer uzupełnia (szczegóły w `docs/nowy-serwer.md`):

1. Domena: `<System>Record` i `RecordNotFoundException` -> obiekty systemu (np. `JiraIssue`), tylko pola
   oddawane przez narzędzia.
2. Adapter: ścieżki API systemu zamiast `/api/records/{id}`, DTO odpowiedzi; GitLab: `PRIVATE-TOKEN` przez
   `credential.value()`. Mapowanie 404/403 -> pusty wynik i 401 -> `CredentialRequiredException` zostaje.
3. Narzędzia z tabeli ws-04, każde w `McpToolsConfiguration` (sam `@Tool` nie wystarcza); `get_record` usunąć
   albo przerobić.
4. Testy: `StubBackend` udaje API systemu; dla każdego narzędzia pozytywny, `credential_required` (brak i 401),
   `not_found_or_denied`, `invalid_argument`. `GatewayOnlyAccessTest` zostaje (zmienić nazwę narzędzia w
   `GET_RECORD`).
5. README (tabela narzędzi: ryzyko, uprawnienie C9, typ poświadczenia), `docs/api/tools-catalog.md`, własny
   `STATUS.md`.
6. `@ResourceRef`, `QueryScope`, `ResultFilter` i manifest C6 dopiero ze starterem beta (M2 template'u); wzorce
   trzeba będzie przenieść ręcznie.

## Odstępstwa od briefu

- Zmienne środowiska jak w `mcp-platform-dev/docker-compose.yml` (usługa `mcp-jira`) i serwerze testowym
  gateway'a, nie jak w briefie: `MCP_GATEWAY_JWKS_URI` (brief: `MCP_GATEWAY_JWKS_URL`), `MCP_REDIS_HOST/PORT/
  USER/PASSWORD` (brief: `REDIS_URL`), `MCP_<SYSTEM>_URL` (brief: `<SYSTEM>_BASE_URL`), `MCP_TLS_KEYSTORE/
  TRUSTSTORE/STORE_PASSWORD`. `MCP_SERVER_ID` nie istnieje: `server-id` jest stały w `application.yml`
  (ustawia go `new-server.sh`), bo musi się zgadzać z systemem w kodzie adaptera. `MCP_POLICY_*` dopiero z
  paczką polityk (beta).
- Endpoint `/mcp` zamiast `/mcp/<system>`: tak adresuje serwer gateway (`internal-url: .../mcp`) i tak ustawia
  starter od alpha-2. Jeden serwer na host (SAN `mcp-<system>.internal`), więc prefiks systemu w ścieżce nic
  nie daje.
- Narzędzia `update_record`, `purge_records`, `search_records` (M2) nie istnieją; stare `example_rename` i
  `example_purge` usunięte zamiast przerabiania na model gateway'a.
- Adapter: tylko timeouty; retry i circuit breaker w M2 (Spring Framework 7 ma `org.springframework.core.retry`,
  do sprawdzenia przed użyciem).
- CI (pipeline, rejestracja manifestu C5): M2, razem z manifestem.

## Znane ograniczenia

- `credential_required` tylko dla systemów z kontraktu. Starter wysyła wyłącznie błędy zgodne z
  `tool-error.schema.json`, gdzie `system` to zamknięta lista `SystemId` (jira, confluence, gitlab, servicedesk,
  opensearch). Dla `example` (template) i `demo` (DoD) brak poświadczenia daje `internal_error`
  (`event=tool_error_invalid` w logu). Testy sprawdzają pełny `credential_required` z `connectUrl`, gdy system
  jest na liście (czytanej z `common.schema.json` z jara kontraktów), a dla innych `internal_error`; ścieżkę
  kodu potwierdza dodatkowo `CredentialRequiredException` w logu i test adaptera. Sprawdzone: wygenerowane
  `jira` i `gitlab` dostają pełny `credential_required`.
- W `mcp-platform-dev` nie ma certyfikatu ani użytkownika Redis dla `mcp-example`; template uruchamia się tam z
  certyfikatem `mcp-jira` (README).

## Czego potrzebują inni

- ws-02 (starter), bez blokowania M1:
  1. Treść odpowiedzi błędu backendu trafia do logu `event=tool_failed`: Spring AI opakowuje wyjątek w
     `ToolExecutionException`, którego komunikat to komunikat `HttpServerErrorException` razem z ciałem
     odpowiedzi; `McpErrorMapper.describeChain` loguje go dosłownie (poświadczenie jest zredagowane, reszta
     ciała nie). README startera mówi, że ciała błędów nie są logowane. Obejście w template: adapter ma
     `defaultStatusHandler`, który rzuca wyjątek z samym statusem (test `theCredentialNeverAppearsInTheResultOrTheLog`
     sprawdza brak treści). Propozycja: w `describeChain` pomijać komunikat wyjątku, którego przyczyną jest
     `RestClientResponseException`.
  2. `server-id` spoza `ServerId` kontraktu przechodzi start, a `credential_required` po cichu staje się
     `internal_error`. Propozycja: ostrzeżenie przy starcie (zatrzymanie startu zablokowałoby template).
- ws-01: usługa `mcp-jira` w `docker-compose.yml` nie ustawia `MCP_CONNECT_URL` (portal, z `{system}`), bez
  którego serwer z template'u nie wstaje (`credential.connect-url` jest wymagane przez starter). Wartość dla
  dev: `http://localhost:${PORTAL_PORT}/connections?system={system}` albo adres z kontraktu portalu. Reszta
  zmiennych z compose pasuje.
- ws-04: instrukcja wyżej. Start od tego commitu; po M2 template'u wzorce `@ResourceRef`/`QueryScope`/
  `ResultFilter` do przeniesienia ręcznie.
- ws-05: nic nowego; serwer spełnia wymagania z README gateway'a (`/mcp`, streamable HTTP, SAN
  `mcp-<system>.internal`, JWKS gateway'a).

## Propozycje zmian kontraktu

Brak w M1.
