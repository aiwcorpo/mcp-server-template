# mcp-example: serwer MCP platformy

Serwer MCP dla systemu `example`, zbudowany na `mcp-platform-starter` 3.0.0-beta-4 (Java 25, Spring Boot
4.1.0, Spring AI 2.0.0, kontrakty `mcp-contracts` 3.1.0). Stoi wyłącznie za gateway'em platformy: przyjmuje
ruch tylko po mTLS z wewnętrznym JWT gateway'a (C2), a do systemu docelowego woła z poświadczeniem
użytkownika, które gateway dołącza do każdego żądania (C3). Bez poprawnego JWT odpowiada 401, nie ma trybu
anonimowego.

Logowania użytkowników, decyzji o dostępie do narzędzi i potwierdzeń akcji destrukcyjnych serwer nie robi:
to zadania gateway'a. Serwer odpowiada za narzędzia, adapter do systemu, poprawne błędy (C10), audyt (C8),
który dokłada starter, oraz za opis swoich zasobów dla polityki: starter ocenia na nich tę samą podpisaną paczkę
Cedar (C7), którą ocenia gateway, i odmawia dostępu do zasobów zastrzeżonych (`restricted`).

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
| `mcp-core` | domena i porty, czysta Java | `ExampleRecord`, `ExampleRecordHit`, `ExampleResourceModel` (typy zasobów polityki, wzorce identyfikatorów z kontraktów), port wejściowy `ExampleUseCase`, port wyjściowy `ExampleResourcePort` |
| `mcp-application` | przypadki użycia (bez Springa) | `ExampleService` |
| `mcp-integration` | adaptery wyjściowe do systemu | `ExampleResourceAdapter` (REST, poświadczenie z C3, timeouty, bez przekierowań, `https` poza `dev`), `BoundedBodyInterceptor` (limit rozmiaru odpowiedzi), `BackendResponseException` (odpowiedź bez treści w komunikacie), `ExampleBackendProperties`, `ExampleRecordResolver` (`ResourceResolver`: rekord z systemu poświadczeniem wywołującego), `ExampleRecordResolver` także z `fetchAll` (jedno wywołanie na hity wyszukiwania), `ExampleResourceDescription` (rekord jako zasób polityki: id, rodzic i klasyfikacja z odpowiedzi systemu) |
| `mcp-tools` | narzędzia MCP, powierzchnia publiczna | `ExampleTool` (`get_record`, `search_records`), `ExampleSearchQuery` (zapytanie do systemu budowane przez serwer), `ExampleRecordView` i `ExampleSearchView` (wyniki z `UntrustedText`, hit z `@ResourceRef`); `@ArgumentLimits` i `SchemaLimitedToolCallbacks` pochodzą ze startera (limity argumentów w `inputSchema`) |
| `mcp-app` | bootstrap, wiring, jedyny jar, testy end-to-end | `McpServerApplication`, `McpToolsConfiguration` (lista narzędzi), `ApplicationWiringConfiguration` |

Zależności idą do środka: `mcp-tools` -> `mcp-application` -> `mcp-core` <- `mcp-integration`; `mcp-app` składa
całość. Resolver zasobów jest w `mcp-integration` obok adaptera, bo atrybuty zasobu (projekt, klasyfikacja) pochodzą z
systemu, nie z argumentu narzędzia.

## Narzędzia

| Narzędzie | Tytuł | Ryzyko | Akcja | Uprawnienie (C9) | Argumenty | Wynik i błędy |
| --- | --- | --- | --- | --- | --- | --- |
| `get_record` | Pobierz rekord | `READ_ONLY` | `read` | `mcp-example-read` | `recordId` z `@ResourceRef`, wzorzec i długość z kontraktu (`ExampleResourceModel`) | `{id, project, title, status}`; `not_found_or_denied` (brak rekordu, 404 i 403 z systemu, projekt `restricted`, rekord przeniesiony do takiego projektu, klasyfikacja nieznana), `access_denied` (brak grupy), `credential_required`, `invalid_argument`, `upstream_unavailable`, `internal_error` |
| `search_records` | Szukaj rekordów | `READ_ONLY` | `read` | `mcp-example-read` | `project` (zakres, wzorzec z kontraktu), `text` (1-200 znaków); zapytanie do systemu buduje serwer | `{hits: [{id, title}]}`, najwyżej 50; zapytanie (JQL) buduje `StructuredQuery` z zakresu linku wirtualnego serwera i wartości od modelu, hity są filtrowane polityką (`ResultFilter`) |

Nazwa widziana przez klienta to `example__get_record` (prefiks dokłada gateway, C5). Tytuły są częścią definicji
narzędzia, więc ich zmiana zmienia `definitionHash`.

Wybór zakresu: model podaje wartości (projekt, tekst), nigdy fragmentu języka zapytań. `ExampleSearchQuery` składa
zapytanie przez `scope.jql(pola).where(pole, operator, wartość).build()` (Confluence: `scope.cql`): starter dokleja
zawężenie z linku (`project in (...)`) i cytuje każdą wartość, więc cudzysłów ani nawias w tekście nie zmienia
zapytania. Systemy bez budowniczego w starterze (GitLab, service desk, OpenSearch) sprawdzają zakres ręcznie
(`linkIds()`, `nothingToQuery()`), a gdzie system nie stosuje praw użytkownika, ograniczają zapytanie do
`grantedIds()` (`grantsNothing()` = nie pytaj systemu).

## Jak przechodzi wywołanie

```
gateway --mTLS (CN mcp-gateway) + JWT C2 (aud mcp-example, tool get_record) + X-MCP-Credential-->
  starter: InternalJwtFilter (inaczej 401) -> CredentialHeaderFilter -> AuditingToolCallback (C8)
  -> starter: Cedar `call` na narzędziu (grupa, link), potem dla @ResourceRef:
     ExampleRecordResolver -> ExampleResourceAdapter: CredentialContext + credential::applyTo
     -> GET {MCP_EXAMPLE_URL}/api/records/{id}; opis zasobu (projekt i klasyfikacja Z SYSTEMU) -> Cedar `read`
  -> ExampleTool.getRecord: rekord, który pobrał resolver (bez drugiego wywołania)
  -> search_records: ExampleSearchQuery (QueryScope + StructuredQuery) -> ExampleService -> adapter
     -> GET /api/records?query=..; hity rozwiązane jednym fetchAll, wynik filtrowany po @ResourceRef hitów
```

Adapter nie podąża za przekierowaniami (nagłówek z poświadczeniem nie może pójść pod inny adres), czyta najwyżej
`max-response-size` (1 MB) i tylko pola domeny, a treści odpowiedzi systemu (także 2xx, której nie da się
sparsować) nie wkłada do żadnego komunikatu ani logu. Starter od 3.0.0-alpha-5 robi to samo w logu
`tool_failed`; adapter zostaje drugą warstwą.

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
| `MCP_EXAMPLE_URL` | adres API systemu; poza `dev` tylko `https` | wymagane |
| `MCP_EXAMPLE_ALLOW_PLAIN_HTTP` | `true` dopuszcza `http` poza `dev`, wyłącznie dla mocków `mcp-platform-dev` (poświadczenie idzie wtedy otwartym tekstem; ostrzeżenie w logu) | `false` |
| `MCP_EXAMPLE_SSL_BUNDLE` | bundle SSL dla HTTPS systemu, np. `mcp-internal-ca`; puste = zaufanie JDK | puste |
| `MCP_REDIS_HOST`, `MCP_REDIS_PORT`, `MCP_REDIS_USER`, `MCP_REDIS_PASSWORD` | Redis dla jednorazowego `jti` (użytkownik ACL `mcp-example`, klucze `jti:mcp-example:*`) | `localhost`, `6379`, `mcp-example`, puste |
| `MCP_POLICY_URL` | `latest.json` kanału głównego paczki polityk (C7), `https` | wymagane |
| `MCP_POLICY_KEY_ID`, `MCP_POLICY_PUBLIC_KEY_FILE` | identyfikator i plik klucza publicznego Ed25519 (SPKI PEM) paczki; klucze `test-*` są odrzucane poza `dev` | wymagane |
| `MCP_POLICY_STATE_DIR` | katalog na instancję z rewizją ostatniej przyjętej paczki; chroni przed cofnięciem po restarcie | wymagane |
| `MCP_INTERNAL_URL` | adres serwera w manifeście (`https://mcp-example.internal:8443/mcp`) | wymagane |
| `MCP_ACCEPTED_CREDENTIAL_TYPES` | typy poświadczeń, które serwer przyjmuje (manifest) | `user-secret` |
| `MCP_PORT`, `MCP_BIND` | port i adres nasłuchu | `8443`, `0.0.0.0` |

Serwer nie wstaje bez pierwszej podpisanej paczki polityk w 30 s (`policy.startup-timeout`), a wersja w manifeście to
wersja projektu. `GET /mcp-manifest` (wewnętrzny JWT serwisowy gateway'a) zwraca manifest C6 zbudowany z tego, co
serwer naprawdę publikuje; służy do rejestracji w gateway'u (`manifest.output-file` zapisuje go też do pliku).
W profilu `dev` paczka idzie z repozytorium polityk środowiska (`http://127.0.0.1:18747/policies/main/latest.json`,
klucz `mcp-policies/keys/dev/test-policies-main-dev.public.pem`, względem katalogu repozytorium serwera), a
`policy.allow-test-keys` jest włączone tylko tam.

Endpoint MCP to `/mcp` (domyślne ustawienie startera, tak jak oczekuje gateway: `internal-url:
https://mcp-example.internal:8443/mcp`). Bez JWT odpowiada tylko `/actuator/health` (i `liveness`,
`readiness`), ale TLS i tak wymaga certyfikatu klienta z CA wewnętrznego.

Poza profilem `dev` serwer nie wstaje bez `MCP_GATEWAY_JWKS_URI` (tylko `https`), `MCP_CONNECT_URL`,
`MCP_EXAMPLE_URL` (`https`) i plików TLS, a od startera 3.0.0-alpha-4 także wtedy, gdy system nie jest na liście
`SystemId` kontraktów (`jira`, `confluence`, `gitlab`, `servicedesk`, `opensearch`). Prawdziwy serwer musi więc
mieć nazwę z tej listy (`new-server.sh` innej nie przyjmie bez `--trial`). Serwer próbny i sam template działają
tylko w profilu `dev`, z ostrzeżeniem w logu.

`java` w `PATH` może być starsza niż 25 (wtedy `UnsupportedClassVersionError`), dlatego poniżej zawsze
`$JAVA_HOME/bin/java`.

## Uruchomienie lokalne bez środowiska

Profil `dev` (musi być jedynym aktywnym profilem; start odmawia, gdy `SPRING_PROFILES_ACTIVE=dev` trafi do `.env`, compose, jednostki systemd albo obrazu): zwykłe HTTP na 127.0.0.1, `jti` w pamięci, bez Redisa,
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

Ryzyko: `application-dev.yml` jest w jarze produkcyjnym, a profil `dev` przyjmuje JWT podpisane opublikowanymi
kluczami testowymi kontraktów i nie wymaga certyfikatu klienta. Dlatego `dev` słucha wyłącznie na adresie pętli
zwrotnej: starter (od 3.0.0-alpha-5) odmawia startu z `dev` przy innym `server.address`, a test
`DevProfileLoopbackTest` pilnuje tego w serwerze. Nie ustawiaj `SERVER_ADDRESS` ani `MCP_BIND` przy `dev` i
nigdy nie włączaj `dev` na hoście ani w kontenerze, do którego łączy się gateway: tam działa wyłącznie
konfiguracja bazowa z mTLS.

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
export MCP_PORT=18490 MCP_BIND=127.0.0.1 MCP_REDIS_PORT=16379
export MCP_JIRA_URL=http://localhost:18081 MCP_JIRA_ALLOW_PLAIN_HTTP=true   # mock Jiry środowiska jest po http
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

## Uruchomienie w stosie dev w kontenerach (profil `devstack`)

`dev` jest zabroniony w kontenerze, więc usługa `mcp-example` w compose `mcp-platform-dev` działa z konfiguracją bazową
(mTLS, Redis, https) i profilem `devstack`, który zdejmuje tylko zakaz kluczy `test-*` (klucze podpisu paczek polityk i
JWT gateway'a stosu). Obie zgody ustawia konfiguracja stosu, nie obraz serwera:

```yaml
environment:
  SPRING_PROFILES_ACTIVE: devstack              # jedyny aktywny profil
  MCP_DEVSTACK_ACKNOWLEDGED: "true"
  AIWCORPO_MCP_PLATFORM_DEVSTACK_ACKNOWLEDGED: "true"
  AIWCORPO_MCP_PLATFORM_POLICY_ALLOW_TEST_KEYS: "true"
  AIWCORPO_MCP_PLATFORM_POLICY_MAIN_KEY_ID: test-policies-main-dev
  AIWCORPO_MCP_PLATFORM_INTERNAL_JWT_ALLOW_TEST_KEYS: "true"   # gdy gateway podpisuje kluczami testowymi
  MCP_POLICY_URL: https://...                    # repozytorium paczek stosu (https)
  MCP_POLICY_PUBLIC_KEY_FILE: /run/policy/test-policies-main-dev.public.pem
  MCP_POLICY_STATE_DIR: /var/lib/mcp-example/policy-state
```

Reszta zostaje jak na produkcji (TLS i mTLS, Redis na `jti`, `policy.state-dir`). Każdy start loguje
`WARN event=devstack_profile`, a metryka `mcp_server_devstack_active` ma wartość 1. Bez obu zgód albo z drugim profilem
start jest odrzucany.

## Testy

| Klasa | Co sprawdza |
| --- | --- |
| `GetRecordToolTest` | poświadczenie z C3 dociera do systemu, jedno wywołanie na rekord; `readOnlyHint`, tytuł, zamknięty schemat, `maxLength` i `pattern`; brak poświadczenia i 401 systemu -> `credential_required`; 404 i 403 -> ten sam `not_found_or_denied`; zły `recordId` nie dociera do systemu i nie wraca w błędzie; nieczytelna odpowiedź 200 i rekord bez `id` -> `internal_error` bez treści w logu; przekierowanie nie jest wykonywane; timeout -> `upstream_unavailable`; za duża odpowiedź ucięta; poświadczenie nie trafia do wyniku ani logu |
| `ResourcePolicyTest` | rekord w projekcie `restricted` i rekord przeniesiony do takiego projektu dają `not_found_or_denied` identyczne jak nieistniejący; rekord z nieznaną klasyfikacją nie jest wydawany (`internal_error`, kontrakt); `access_denied` bez grupy i bez wywołania systemu; link zawężony do innego projektu ukrywa rekord; awaria systemu to nie odmowa |
| `SearchRecordsToolTest` | wyniki bez rekordów zastrzeżonych i przeniesionych; link zawężony do jednego projektu nie zwraca rekordów spoza niego i jest w zapytaniu; wyszukiwanie poza linkiem jest puste; cudzysłów w tekście od modelu zostaje literałem; hity rozwiązane jednym wywołaniem; `credential_required`, `access_denied`, błędne argumenty |
| `ManifestTest` | manifest przechodzi reguły gateway'a, definicje równe `tools/list` (zgodny `definitionHash`), tytuły i zamknięte schematy, `resourceRefs`, 401 bez JWT |
| `GatewayOnlyAccessTest` | bez JWT 401 (`tools/call`, `ping`, `initialize`), JWT dla innego serwera, innego narzędzia, wariant serwisowy i przeterminowany -> 401; bez JWT tylko health; system zgodny z `SystemId` (albo jawnie próbny) |
| `ProductionConfigurationTest` | konfiguracja bazowa: `client-auth: need`, paczka polityk i manifest wymagane, bez kluczy testowych; klucze testowe i repozytorium polityk dev tylko w `application-dev.yml`; backend `http` poza `dev` odrzucony (chyba że jawnie dopuszczony); `dev` na 127.0.0.1 |
| `DevProfileLoopbackTest` | reguła adresu `dev` (`assertDevProfileLoopbackRule`) i obecność guardów w kontekście serwera (`assertServerIsGuarded`) |
| `ExampleToolTest` | walidacja tekstu wyszukiwania (stały komunikat), limity i zamknięte schematy obu narzędzi, `get_record` z kontekstem z `TestResourceContext` |
| `ExampleServiceTest` | limit wyników wyszukiwania bez Springa |

Nowe narzędzie dostaje test w tym samym stylu: klasa dziedziczy po `ServerTestSupport`, odpowiedź systemu
dopisuje się w `StubBackend`. Polityka w testach to paczka z przykładów kontraktów (`TestGateway`; dla serwera spoza pięciu systemów
generowana: grupy `mcp-<system>-read|write|destructive`, odczyt, `restricted`, link). Serwer próbny nie ma poprawnego manifestu
(`serverId` jest enumem), więc `ManifestTest` pomija go, a `credential_required` jest w nim `internal_error`. 
Identyfikatory przykładowych rekordów muszą pasować do wzorca typu zasobu z kontraktów (starter sprawdza go przed
resolverem), dlatego `SampleScope` jest osobny dla każdego systemu.

## Dokumentacja

- `docs/jak-napisac-narzedzie.md`: zasady i kolejność kroków przy nowym narzędziu.
- `docs/architecture/overview.md`: architektura i decyzje.
- `docs/security/SECURITY.md`: niezmienniki bezpieczeństwa w tym serwerze.
- `docs/api/tools-catalog.md`: rejestr narzędzi.
- `docs/operations/deployment-vm-systemd.md`, `deploy/`, `Dockerfile`: wdrożenie.
