# mcp-example: serwer MCP platformy

Serwer MCP dla systemu `example`, zbudowany na `mcp-platform-starter` 3.0.0-alpha-4 (Java 25, Spring Boot
4.1.0, Spring AI 2.0.0, kontrakty `mcp-contracts` 2.0.2). Stoi wyłącznie za gateway'em platformy: przyjmuje
ruch tylko po mTLS z wewnętrznym JWT gateway'a (C2), a do systemu docelowego woła z poświadczeniem
użytkownika, które gateway dołącza do każdego żądania (C3). Bez poprawnego JWT odpowiada 401, nie ma trybu
anonimowego.

Logowania użytkowników, decyzji o dostępie do narzędzi i potwierdzeń akcji destrukcyjnych serwer nie robi:
to zadania gateway'a. Serwer odpowiada za narzędzia, adapter do systemu, poprawne błędy (C10) i audyt (C8),
który dokłada starter.

## Szybki start

```bash
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@25/25.0.4.1/libexec/openjdk.jdk/Contents/Home
./mvnw verify                 # build, testy przez TestGateway i atrapę backendu; jar: mcp-app/target/mcp-example.jar
```

Testy nie potrzebują Dockera ani środowiska `mcp-platform-dev`: `TestGateway` z `mcp-platform-test` wystawia
JWKS z kluczy testowych kontraktów i podpisuje wewnętrzne JWT, a `StubBackend` (test) udaje API systemu.

## Moduły i miejsce każdej rzeczy

| Moduł | Co w nim jest | W tym serwerze |
| --- | --- | --- |
| `mcp-core` | domena i porty, czysta Java | `ExampleRecord`, `RecordNotFoundException`, port wejściowy `ExampleUseCase`, port wyjściowy `ExampleResourcePort` |
| `mcp-application` | przypadki użycia (bez Springa) | `ExampleService` |
| `mcp-integration` | adaptery wyjściowe do systemu | `ExampleResourceAdapter` (REST, poświadczenie z C3, timeouty), `ExampleBackendProperties` |
| `mcp-tools` | narzędzia MCP, powierzchnia publiczna | `ExampleTool` (`get_record`), `ExampleToolErrors` (wyjątki domeny -> kody C10) |
| `mcp-app` | bootstrap, wiring, jedyny jar, testy end-to-end | `McpServerApplication`, `McpToolsConfiguration` (lista narzędzi), `ApplicationWiringConfiguration` |

Zależności idą do środka: `mcp-tools` -> `mcp-application` -> `mcp-core` <- `mcp-integration`; `mcp-app` składa
całość. Resolver zasobów (`@ResourceRef`, starter beta) dojdzie w `mcp-integration` obok adaptera, bo atrybuty
zasobu (projekt, klasyfikacja) pochodzą z systemu.

## Narzędzia

| Narzędzie | Ryzyko | Argumenty | Wynik i błędy |
| --- | --- | --- | --- |
| `get_record` | `READ_ONLY` | `recordId`: 1-64 znaki `[A-Za-z0-9_-]`, zaczyna się literą lub cyfrą | `{id, project, title, status}`; `not_found_or_denied` (404 i 403 z systemu), `credential_required` (brak poświadczenia albo 401 z systemu), `invalid_argument`, `upstream_unavailable`, `internal_error` |

Nazwa widziana przez klienta to `example__get_record` (prefiks dokłada gateway, C5).

## Jak przechodzi wywołanie

```
gateway --mTLS (CN mcp-gateway) + JWT C2 (aud mcp-example, tool get_record) + X-MCP-Credential-->
  starter: InternalJwtFilter (inaczej 401) -> CredentialHeaderFilter -> AuditingToolCallback (C8)
  -> ExampleTool.getRecord: walidacja recordId
  -> ExampleService -> ExampleResourceAdapter: CredentialContext.require("example") + credential::applyTo
  -> GET {MCP_EXAMPLE_URL}/api/records/{id}
```

Adapter nie podąża za przekierowaniami (nagłówek z poświadczeniem nie może pójść pod inny adres), czyta tylko
pola domeny i nie wkłada treści odpowiedzi systemu do żadnego komunikatu.

## Konfiguracja

Konfiguracja bazowa (`mcp-app/src/main/resources/application.yml`) jest produkcyjna: mTLS, JWKS gateway'a po
TLS z CA wewnętrznym, `jti` w Redisie. Profil `dev` (`application-dev.yml`, testy i uruchomienie lokalne)
wyłącza TLS i mTLS, trzyma `jti` w pamięci i dopuszcza klucze testowe; starter odrzuca te ustawienia poza `dev`.

| Zmienna | Znaczenie | Domyślnie |
| --- | --- | --- |
| `MCP_TLS_KEYSTORE`, `MCP_TLS_TRUSTSTORE`, `MCP_TLS_STORE_PASSWORD` | PKCS12: certyfikat serwera (SAN `mcp-example.internal`) i CA wewnętrzne; truststore służy też do JWKS | wymagane |
| `MCP_GATEWAY_JWKS_URI` | JWKS wewnętrznego JWT, np. `https://mcp-gateway.internal:8443/.well-known/jwks.json` | wymagane |
| `MCP_GATEWAY_ISSUER` | `iss` C2 | `https://mcp-gateway.internal` |
| `MCP_CONNECT_URL` | strona portalu do połączenia konta, z `{system}`; trafia do `credential_required` | wymagane |
| `MCP_EXAMPLE_URL` | adres API systemu | wymagane |
| `MCP_EXAMPLE_SSL_BUNDLE` | bundle SSL dla HTTPS systemu, np. `mcp-internal-ca`; puste = zaufanie JDK | puste |
| `MCP_REDIS_HOST`, `MCP_REDIS_PORT`, `MCP_REDIS_USER`, `MCP_REDIS_PASSWORD` | Redis dla jednorazowego `jti` (użytkownik ACL `mcp-example`, klucze `jti:mcp-example:*`) | `localhost`, `6379`, `mcp-example`, puste |
| `MCP_PORT`, `MCP_BIND` | port i adres nasłuchu | `8443`, `0.0.0.0` |

Endpoint MCP to `/mcp` (domyślne ustawienie startera, tak jak oczekuje gateway: `internal-url:
https://mcp-example.internal:8443/mcp`). Bez JWT odpowiada tylko `/actuator/health` (i `liveness`,
`readiness`), ale TLS i tak wymaga certyfikatu klienta z CA wewnętrznego.

Poza profilem `dev` serwer nie wstaje bez `MCP_GATEWAY_JWKS_URI` (tylko `https`), `MCP_CONNECT_URL`,
`MCP_EXAMPLE_URL` i plików TLS, a od startera 3.0.0-alpha-4 także wtedy, gdy system nie jest na liście `SystemId`
kontraktów (`jira`, `confluence`, `gitlab`, `servicedesk`, `opensearch`). Serwer systemu spoza tej listy
(także sam template) działa więc tylko w profilu `dev`, z ostrzeżeniem w logu.

`java` w `PATH` może być starsza niż 25 (wtedy `UnsupportedClassVersionError`), dlatego poniżej zawsze
`$JAVA_HOME/bin/java`.

## Uruchomienie lokalne bez środowiska

Profil `dev` (musi być jedynym aktywnym profilem): zwykłe HTTP na 127.0.0.1, `jti` w pamięci, bez Redisa,
certyfikatów i sekretów. Gateway'a nie ma, więc każde żądanie z JWT kończy się 503 (JWKS niedostępny); ten tryb
służy do sprawdzenia, że serwer wstaje i odrzuca ruch bez JWT. Narzędzia sprawdzają testy (`./mvnw verify`).

```bash
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@25/25.0.4.1/libexec/openjdk.jdk/Contents/Home
./mvnw -B verify
SPRING_PROFILES_ACTIVE=dev MCP_PORT=18491 "$JAVA_HOME/bin/java" -jar mcp-app/target/mcp-example.jar
# w drugim terminalu:
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:18491/actuator/health          # 200
curl -s -o /dev/null -w '%{http_code}\n' -X POST -H 'Content-Type: application/json' \
     -H 'Accept: application/json, text/event-stream' \
     -d '{"jsonrpc":"2.0","id":1,"method":"ping"}' http://127.0.0.1:18491/mcp            # 401
```

Wartości domyślne profilu są w `mcp-app/src/main/resources/application-dev.yml` (backend
`http://localhost:18081`, portal `http://localhost:18090`); każdą można nadpisać zmienną z tabeli wyżej.

## Uruchomienie z mcp-platform-dev (mTLS)

Konfiguracja produkcyjna na hoście, z certyfikatami i Redisem środowiska `mcp-platform-dev` (`make up` już
uruchomione). Działa tylko dla systemu z listy `SystemId`: środowisko ma certyfikaty (`certs/mcp-<system>`, SAN
`mcp-<system>.internal`, z `scripts/gen-certs.sh`) i użytkowników Redisa (`mcp-<system>`, `redis/start.sh`)
dokładnie dla tych pięciu systemów. Nowy system wymaga najpierw zmiany kontraktu (`SystemId`) i dopisania go w
ws-01 (lista `identities` w `gen-certs.sh`, pętla w `redis/start.sh`); bez tego zostaje tryb `dev`.

Magazyny PKCS12 i Redis są chronione hasłami z `$MCP_PLATFORM_DEV/.secrets/dev.env`, więc ten wariant wymaga
ich odczytu. Przykład dla serwera `mcp-jira` (wygenerowanego `new-server.sh jira`; inny system: zamień `jira`/
`JIRA` w nazwach):

```bash
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@25/25.0.4.1/libexec/openjdk.jdk/Contents/Home
export MCP_PLATFORM_DEV=<workspace>/mcp-platform-dev           # ścieżka bezwzględna
export MCP_TLS_STORE_PASSWORD="$(grep '^TLS_STORE_PASSWORD=' "$MCP_PLATFORM_DEV/.secrets/dev.env" | cut -d= -f2-)"
export MCP_REDIS_PASSWORD="$(grep '^REDIS_MCP_JIRA_PASSWORD=' "$MCP_PLATFORM_DEV/.secrets/dev.env" | cut -d= -f2-)"
export MCP_TLS_KEYSTORE="$MCP_PLATFORM_DEV/certs/mcp-jira/keystore.p12"
export MCP_TLS_TRUSTSTORE="$MCP_PLATFORM_DEV/certs/ca/truststore.p12"
export MCP_PORT=18490 MCP_BIND=127.0.0.1 MCP_REDIS_PORT=16379 MCP_JIRA_URL=http://localhost:18081
export MCP_GATEWAY_JWKS_URI=https://localhost:18445/.well-known/jwks.json
export MCP_CONNECT_URL='http://localhost:18090/connections?system={system}'
"$JAVA_HOME/bin/java" -jar mcp-app/target/mcp-jira.jar
# w drugim terminalu (te same zmienne MCP_PLATFORM_DEV), certyfikat klienta gateway'a:
TLS=(--cacert "$MCP_PLATFORM_DEV/certs/ca/ca.crt" --resolve mcp-jira.internal:18490:127.0.0.1
     --cert "$MCP_PLATFORM_DEV/certs/mcp-gateway/tls.crt" --key "$MCP_PLATFORM_DEV/certs/mcp-gateway/tls.key")
curl -s -o /dev/null -w '%{http_code}\n' "${TLS[@]}" https://mcp-jira.internal:18490/actuator/health   # 200
curl -s -o /dev/null -w '%{http_code}\n' "${TLS[@]}" -X POST -H 'Content-Type: application/json' \
     -d '{"jsonrpc":"2.0","id":1,"method":"ping"}' https://mcp-jira.internal:18490/mcp                     # 401
```

Sprawdzone 2026-10-27 w czystej kopii `new-server.sh jira`. Połączenie bez certyfikatu klienta jest zrywane w
handshake'u. Pełne wywołanie narzędzia wymaga gateway'a z hosta (`mcp-gateway/scripts/run-dev-host.sh`, JWKS na
`localhost:18445`) skonfigurowanego na ten serwer.

## Testy

| Klasa | Co sprawdza |
| --- | --- |
| `GetRecordToolTest` | poświadczenie z C3 dociera do systemu; `readOnlyHint`; brak poświadczenia i 401 systemu -> `credential_required`; 404 i 403 -> ten sam `not_found_or_denied`; zły `recordId` nie dociera do systemu; poświadczenie nie trafia do wyniku ani logu |
| `GatewayOnlyAccessTest` | bez JWT 401 (`tools/call`, `ping`, `initialize`), JWT dla innego serwera, innego narzędzia, wariant serwisowy i przeterminowany -> 401; bez JWT tylko health |
| `ExampleServiceTest` | przypadek użycia bez Springa |

Nowe narzędzie dostaje test w tym samym stylu: klasa dziedziczy po `ServerTestSupport`, odpowiedź systemu
dopisuje się w `StubBackend`.

## Dokumentacja

- `docs/jak-napisac-narzedzie.md`: zasady i kolejność kroków przy nowym narzędziu.
- `docs/architecture/overview.md`: architektura i decyzje.
- `docs/security/SECURITY.md`: niezmienniki bezpieczeństwa w tym serwerze.
- `docs/api/tools-catalog.md`: rejestr narzędzi.
- `docs/operations/deployment-vm-systemd.md`, `deploy/`, `Dockerfile`: wdrożenie.
