# mcp-example: serwer MCP platformy

Szablon serwera MCP dla platformy i zarazem działający serwer przykładowy (system `example`), zbudowany na
`mcp-platform-starter` 3.0.0-beta-14 (Java 25, Spring Boot 4.1.0, Spring AI 2.0.0, kontrakty `mcp-contracts` 6.3.2).
Repozytorium jest narzędziem dla autorów serwerów: `new-server.sh <system>` robi z niego nowy serwer
(`docs/nowy-serwer.md`), a ten plik README trafia do każdego wygenerowanego serwera, który jest już produktem do
wdrożenia obok gateway'a. Dokumentacja platformy: repozytorium `mcp-docs`. Serwer stoi wyłącznie za gateway'em platformy: przyjmuje
ruch tylko po mTLS z wewnętrznym JWT gateway'a (C2), a do systemu docelowego woła z poświadczeniem
użytkownika, które gateway dołącza do każdego żądania (C3). Bez poprawnego JWT odpowiada 401, nie ma trybu
anonimowego.

Logowania użytkowników, decyzji o dostępie do narzędzi i potwierdzeń akcji destrukcyjnych serwer nie robi:
to zadania gateway'a. Serwer odpowiada za narzędzia, adapter do systemu, poprawne błędy (C10), audyt (C8),
który dokłada starter, oraz za opis swoich zasobów dla polityki: starter ocenia na nich tę samą podpisaną paczkę
Cedar (C7), którą ocenia gateway, i odmawia dostępu do zasobów zastrzeżonych (`restricted`).

## Szybki start

```bash
./mvnw verify                 # build, testy przez TestGateway i atrapę backendu; jar: mcp-app/target/mcp-example.jar
```

Potrzebny jest JDK 25 (`ci/run.sh verify` sam go szuka; `./mvnw` wymaga `JAVA_HOME` ustawionego na JDK 25). Linux i macOS z
bash 3.2 lub nowszym; Windows tylko przez WSL2.

Wersja startera to właściwość `mcp-platform.version` w `pom.xml` (importuje ją `mcp-platform-bom`, który przypina też
kontrakty 6.3.2); `new-server.sh` czyta wersję z tej właściwości, więc podbicie robi się w jednym miejscu.

Testy nie potrzebują Dockera ani środowiska `mcp-platform-dev`: `TestGateway` z `mcp-platform-test` wystawia
JWKS z kluczy testowych kontraktów i podpisuje wewnętrzne JWT, a `StubBackend` (test) udaje API systemu.

## Moduły i miejsce każdej rzeczy

| Moduł | Co w nim jest | W tym serwerze |
| --- | --- | --- |
| `mcp-core` | domena i porty, czysta Java | `ExampleRecord`, `ExampleRecordHit`, `RecordComment`, `CommentPage`, `ServerInfo`, `ObservedVersions`, `ExampleResourceModel` (typy zasobów polityki, wzorce identyfikatorów z kontraktów), port wejściowy `ExampleUseCase`, port wyjściowy `ExampleResourcePort` |
| `mcp-application` | przypadki użycia (bez Springa) | `ExampleService` |
| `mcp-integration` | adaptery wyjściowe do systemu | `ExampleBackendClient` (REST, poświadczenie z C3 dowolnego typu, timeouty, bez przekierowań, `https` poza `dev`, mapowanie błędów), `ExampleResourceAdapter` (ścieżki i odpowiedzi API, 404 i 403 jako "nie widać"), `BoundedBodyInterceptor` (limit rozmiaru odpowiedzi), `BackendResponseException` (odpowiedź bez treści w komunikacie), `ExampleBackendProperties`, `ExampleRecordResolver` (`ResourceResolver`: rekord z systemu poświadczeniem wywołującego), `fetchAll` w resolverze (jedno wywołanie na hity wyszukiwania), `ExampleResourceDescription` (rekord jako zasób polityki: id, rodzic i klasyfikacja z odpowiedzi systemu), `ExampleCapabilityProbe` (sonda wersji systemu) |
| `mcp-tools` | narzędzia MCP, powierzchnia publiczna | `ExampleTool` (`get_record`, `search_records`, `list_comments`, `get_comment`, `update_record`), `ExampleWriteGate` (zapis tylko dla przetestowanej wersji), `ExampleToolErrors` (błędy domenowe jako C10), `ExampleSearchQuery` (zapytanie do systemu budowane przez serwer), `ExampleRecordView`, `ExampleSearchView`, `ExampleCommentItem` i `ExampleUpdateView` (wyniki z `UntrustedText`, hit z `@ResourceRef`); `@ArgumentLimits` i `SchemaLimitedToolCallbacks` pochodzą ze startera (limity argumentów w `inputSchema`) |
| `mcp-app` | bootstrap, wiring, jedyny jar, testy end-to-end | `McpServerApplication`, `McpToolsConfiguration` (lista narzędzi), `ApplicationWiringConfiguration` |

Zależności idą do środka: `mcp-tools` -> `mcp-application` -> `mcp-core` <- `mcp-integration`; `mcp-app` składa
całość. Resolver zasobów jest w `mcp-integration` obok adaptera, bo atrybuty zasobu (projekt, klasyfikacja) pochodzą z
systemu, nie z argumentu narzędzia.

## Narzędzia

| Narzędzie | Tytuł | Ryzyko | Akcja | Uprawnienie (C9) | Argumenty | Wynik i błędy |
| --- | --- | --- | --- | --- | --- | --- |
| `get_record` | Pobierz rekord | `READ_ONLY` | `read` | `mcp-example-read` | `recordId` z `@ResourceRef`, wzorzec i długość z kontraktu (`ExampleResourceModel`) | `{id, project, title, status}`; `not_found_or_denied` (brak rekordu, 404 i 403 z systemu, projekt `restricted`, rekord przeniesiony do takiego projektu, klasyfikacja nieznana), `access_denied` (brak grupy), `credential_required`, `invalid_argument`, `upstream_unavailable`, `internal_error` |
| `search_records` | Szukaj rekordów | `READ_ONLY` | `read` | `mcp-example-read` | `project` (zakres, wzorzec z kontraktu), `text` (1-200 znaków); zapytanie do systemu buduje serwer | `{hits: [{id, title}]}`, najwyżej 50; zapytanie (JQL) buduje `StructuredQuery` z żądanego projektu i wartości od modelu, hity są filtrowane polityką (`ResultFilter`) |
| `list_comments` | Lista komentarzy | `READ_ONLY` | `read` | `mcp-example-read` | `recordId` z `@ResourceRef`, `cursor` (opcjonalny, `CursorCodec`) | `{items: [{id, author, body}], nextCursor, truncated, omitted}`, 10 na stronę; komentarz zapisany pod innym rekordem odpada, a `PageFiller` czyta kolejne strony systemu, żeby krótka strona nie zdradzała, że coś odpadło; kursor jest szyfrowany i związany z użytkownikiem, narzędziem i rekordem |
| `get_comment` | Pobierz komentarz | `READ_ONLY` | `read` | `mcp-example-read` | `recordId` z `@ResourceRef`, `commentId` (cyfry, do 12) | `{id, author, body}`; komentarz, którego system nie przypisuje do tego rekordu, daje `not_found_or_denied` tak samo jak nieistniejący |
| `update_record` | Zmień tytuł rekordu | `UPDATE` | `update` | `mcp-example-write` | `recordId` z `@ResourceRef`, `title` (1-255 znaków, jedna linia) | `{id, title}`; ustawia wartość docelową, więc powtórzenie po `502` zostawia ten sam stan (CC-204); przy wersji systemu spoza przetestowanego zakresu `upstream_unavailable` i nic nie jest wysłane |

Nazwa widziana przez klienta to `example__get_record` (prefiks dokłada gateway, C5). Tytuły są częścią definicji
narzędzia, więc ich zmiana zmienia `definitionHash`.

Wybór zakresu: model podaje wartości (projekt, tekst), nigdy fragmentu języka zapytań. `ExampleSearchQuery` składa
zapytanie przez `scope.jql(pola).where(pole, operator, wartość).build()` (Confluence: `scope.cql`): starter dokleja
zawężenie do żądanego projektu (`project in (...)`) i cytuje każdą wartość, więc cudzysłów ani nawias w tekście nie zmienia
zapytania. Pola i operatory są normatywne (CC-160): warunki JQL to `text`, `status`, `assignee`, `summary`, CQL `text`,
`title`, `label`; operatory `EQUALS`, `CONTAINS`, `NOT_EQUALS` z jedną wartością; `ORDER BY` z `created`, `updated`,
`priority`, `key` (CQL: `created`, `lastmodified`, `title`). Projekt ani przestrzeń nie są polami warunku: żądany zakres wchodzi do części zakresowej zapytania przez
`scope.narrowedTo(List.of(projekt))` (puste przecięcie z grantami to `nothingToQuery()`: pusty wynik bez wywołania systemu), więc
zapytanie ma postać `(warunki) AND project in ("DEF")`. Argument nie jest `@ResourceRef` (to wymagałoby resolvera typu
kontenera, a projekt spoza grantów ma dać pusty wynik, nie błąd); hity i tak przechodzą przez `ResultFilter`. Systemy bez budowniczego w starterze (GitLab, service desk) składają zapytanie z żądanej grupy i tekstu (prawa użytkownika stosuje system), a gdzie system nie
stosuje praw użytkownika (service desk z kontem serwisowym) pytają tylko o kolejki z `grantedIds()`; `nothingToQuery()`
oznacza: zwróć pusty wynik bez wywołania systemu. OpenSearch nie wysyła wzorców `p-*` do wyszukiwania: wzorce zakresu
(`opensearchIndexExpression()`) idą wyłącznie do `GET _resolve/index/` z `OPENSEARCH_RESOLVE_PARAMETERS` (odpowiedź 404 to
`EMPTY_RESOLVE_RESPONSE`, wyniku nie cache'ujemy), `opensearchSearchTarget` zwraca nazwy indeksów w zakresie (bez aliasów, strumieni
i nazw niezgodnych z kontraktem; puste = nie pytaj), a wyszukiwanie dostaje je z `OPENSEARCH_SEARCH_PARAMETERS`. Indeks
podany jako argument narzędzia przechodzi przez `resolveIndexArgument` (alias, indeks poza zakresem i nieznany to ten
sam `not_found_or_denied`).

## Wersje systemu i zapis

`ExampleCapabilityProbe` czyta wersję (`GET /api/server-info`, bez poświadczenia: sonda działa poza żądaniem) i ocenia ją względem
zakresu z kodu (`MIN_MAJOR`, `MIN_MINOR`, `MAX_MAJOR`; nowy serwer ustawia tu zakres, który naprawdę przetestował).
Wersja w zakresie: `SUPPORTED`, odczyt i zapis. Starsza albo nowsza: `DEGRADED`, tylko odczyt. Bez odpowiedzi albo bez czytelnej
wersji: `UNAVAILABLE`. Serwer wstaje w każdym stanie. `ExampleWriteGate` stoi na początku każdego narzędzia `UPDATE` i
`DESTRUCTIVE` i zamyka zapis (fail closed): przed pierwszą odpowiedzią sondy i przy `UNAVAILABLE` zapis jest wyłączony do jawnego
`MCP_EXAMPLE_WRITES_ON_UNKNOWN_VERSION=true`, a dla `DEGRADED` zawsze. `ObservedVersions` pamięta, że sonda uznała wersję za
nieprzetestowaną, więc jedna nieudana sonda nie otwiera zapisu z powrotem. Stan widać w logu (`backend capability: probe=example ...`),
w metryce `mcp_backend_capability` i w szczególe `backendCapability` zdrowia (z wewnętrznym JWT).

## Jak przechodzi wywołanie

```
gateway --mTLS (CN mcp-gateway) + JWT C2 (aud mcp-example, tool get_record) + X-MCP-Credential-->
  starter: InternalJwtFilter (inaczej 401) -> CredentialHeaderFilter -> AuditingToolCallback (C8)
  -> starter: Cedar `call` na narzędziu (grupa), potem dla @ResourceRef:
     ExampleRecordResolver -> ExampleResourceAdapter -> ExampleBackendClient: CredentialContext + credential::applyTo
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
| `MCP_ACCEPTED_CREDENTIAL_TYPES` | typy poświadczeń, które serwer przyjmuje (manifest); musi zawierać `credential-type` | zależy od systemu (niżej) |
| `MCP_PAGING_CURSOR_KEY` | klucz kursorów (Base64, co najmniej 32 bajty, `openssl rand -base64 32`); ten sam na wszystkich instancjach, traktowany jak sekret; bez niego każdy proces losuje własny, więc kursor nie przeżywa restartu | zalecane |
| `MCP_EXAMPLE_WRITES_ON_UNKNOWN_VERSION` | `true` otwiera zapis, gdy sonda nie odczytała wersji; wersja uznana za nieprzetestowaną zostaje tylko do odczytu | `false` |
| `MCP_PORT`, `MCP_BIND` | port i adres nasłuchu | `8443`, `0.0.0.0` |

Typ domyślny poświadczenia (`context.credentialType` w każdym żądaniu Cedar) to pierwszy z `manifest.accepted-credential-types`
(osobnej właściwości `credential-type` od beta-7 nie ma). `new-server.sh` ustawia listę: Jira i Confluence `user-secret`,
GitLab `user-oauth,user-secret`, Service Desk `service-account`, OpenSearch `token-exchange`. Pierwszy typ musi być tym, który ma
konfiguracja systemu w gateway'u i brokerze (inaczej rejestracja kończy się `credential_type_mismatch`); lista nie miesza
`service-account` z typami użytkownika. Żądanie z poświadczeniem i nagłówkiem `X-MCP-Credential-Type` spoza listy (albo bez
nagłówka) kończy się `internal_error` przed polityką i systemem. Wartość w `application.yml` template'u (`user-secret`) jest przykładowa.

Serwer nie wstaje bez pierwszej podpisanej paczki polityk w 30 s (`policy.startup-timeout`), a wersja w manifeście to
wersja projektu. Pole `environment: dev` w manifeście dodaje tylko profil `devstack`; gateway w trybie produkcyjnym odrzuca taki
manifest (`environment_mismatch`). `GET /mcp-manifest` (wewnętrzny JWT serwisowy gateway'a) zwraca manifest C6 zbudowany z tego, co
serwer naprawdę publikuje; do rejestracji w gateway'u służy jednak plik z budowy (niżej), nie odpowiedź działającego serwera.

## Manifest do rejestracji (CI)

Zwykły build (`./mvnw package`, także z `-DskipTests`) zapisuje manifest przeglądanego commitu w `mcp-app/target/mcp-manifest.json`.
Robi to `ManifestExport` (uruchamiany w fazie `package` przez `exec-maven-plugin`, w `mcp-app/pom.xml`): czyta konfigurację
produkcyjną (`application.yml` bez profilu) i listę narzędzi z `McpToolsConfiguration`, nie startuje serwera (potrzebowałby
magazynów TLS, Redisa i paczki polityk) i używa tego samego `ManifestGenerator` z tymi samymi kontrolami co serwer przy starcie,
więc manifest, który gateway by odrzucił, zatrzymuje build. `internalUrl` jest informacyjny (z `MCP_INTERNAL_URL`, a gdy go
brak, `https://mcp-<system>.internal:8443/mcp`). `ManifestTest` pilnuje, że narzędzia tego pliku są identyczne z `tools/list`
działającego serwera (hash definicji obejmuje też typy poświadczeń, więc w teście porównywane są definicje bez hasha). Krok CI robi `PUT` tego pliku do rejestru gateway'a z certyfikatem
`mcp-registry-ci-<serverId>`; zatwierdzane są tylko narzędzia `READ_ONLY`, nowe i zmienione `UPDATE`/`DESTRUCTIVE` czekają na
`approve` administratora. Template i serwery próbne (`system` spoza `SystemId`) nie generują pliku (`manifest.export.skip=true`
w `mcp-app/pom.xml`); `new-server.sh` ustawia `false` dla systemu z listy. `ManifestExport` jest w jarze produkcyjnym jako
nieużywana klasa pomocnicza. Eksporter tworzy klasy narzędzi ręcznie (z `null` zamiast zależności, bo tylko czyta adnotacje), więc nowa klasa narzędzi trafia i do `McpToolsConfiguration`, i do `ManifestExport.manifest()`; `ManifestTest` wykryje brak (narzędzia pliku różne od `tools/list`).

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
`$JAVA_HOME/bin/java`, z `JAVA_HOME` ustawionym na JDK 25.

## CI

Cała logika jest w `ci/run.sh` (`make ci` woła `ci/run.sh verify`); `.gitlab-ci.yml`, `Jenkinsfile` i `.github/workflows/ci.yml`
to cienkie nakładki, więc to samo działa lokalnie, w obrazie `maven:3.9.12-eclipse-temurin-25` i w każdym z trzech systemów.

| Krok | Co robi |
| --- | --- |
| `ci/run.sh verify` (domyślny) | `hygiene`, `mvnw clean verify`, potem kontrola, że uruchomiono co najmniej jeden test (pominięte nie liczą się) i że serwer systemu z `SystemId` zapisał `mcp-app/target/mcp-manifest.json` |
| `ci/run.sh hygiene` | cudzysłowy, spacje i wielokropek spoza ASCII oraz ślady narzędzi w źródłach (perl czyta przez `<<>>`) |
| `ci/run.sh generator` | tylko template: `tests/new-server-test.sh --build` (każdy wygenerowany serwer się buduje i przechodzi testy); nakładki uruchamiają go tylko, gdy ten plik istnieje |
| `ci/run.sh image` | `docker build` jaru z `verify` jako `mcp-server-<system>:<prefiks>-<sha7>` (prefiks z `CI_IMAGE_TAG_PREFIX`, domyślnie `m6`); tylko gałąź `main`, wymaga demona Dockera |

Inny argument kończy się kodem 2. Merge request i pull request nie dostają sekretów ani gniazda Dockera: testy nie
potrzebują kontenerów, a rejestr Maven z artefaktami platformy (`MCP_MAVEN_REPOSITORY_URL`, zwykła zmienna) jest wtedy
czytany anonimowo (`.ci/settings-anonymous.xml`). Dane dostępu (`MCP_MAVEN_REPOSITORY_USER`, `_PASSWORD`; w GitLabie
zmienne Protected i Masked, w Jenkinsie poświadczenie `mcp-maven-registry`) widzi tylko gałąź `main`, na której działa też
krok `image` (runner z gniazdem Dockera; w GitLabie tag `docker-socket` musi należeć do runnera chronionego (protected), inaczej gniazdo trafiłoby w ręce merge requestu; w GitHubie sekrety są w Environment `ci-main`). Moduły z wymaganymi testami: `CI_TEST_MODULES` (domyślnie `mcp-application mcp-integration mcp-tools mcp-app`), dolna granica dla `mcp-app`: `CI_APP_MIN_TESTS` (40). Rejestracja manifestu w gateway'u (`PUT` z certyfikatem
`mcp-registry-ci-<serverId>`) jest osobnym krokiem pipeline'u serwera, bo wymaga sekretu, którego template nie zna.

## Uruchomienie lokalne bez środowiska

Profil `dev` (musi być jedynym aktywnym profilem; start odmawia, gdy `SPRING_PROFILES_ACTIVE=dev` trafi do `.env`, compose, jednostki systemd albo obrazu): zwykłe HTTP na 127.0.0.1, `jti` w pamięci, bez Redisa,
certyfikatów i sekretów. Gateway'a nie ma, więc każde żądanie z JWT kończy się 503 (JWKS niedostępny); ten tryb
służy do sprawdzenia, że serwer wstaje i odrzuca ruch bez JWT. Narzędzia sprawdzają testy (`./mvnw verify`).

```bash
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
export MCP_PLATFORM_DEV=/path/to/mcp-platform-dev           # katalog repozytorium, ścieżka bezwzględna
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

`dev` jest zabroniony w kontenerze, więc usługa `mcp-<system>` w compose `mcp-platform-dev` działa z konfiguracją bazową
(mTLS, Redis, https) i profilem `devstack`, który zdejmuje zakaz kluczy z prefiksem `test-` i `dev-` (klucze podpisu
paczek polityk i JWT gateway'a stosu; kontrakty 4.0.0 odrzucają `dev-` poza trybem dev: profil `devstack` z obiema zgodami
i `allow-test-keys=true`). Zgody ustawia konfiguracja stosu, nie obraz serwera; `MCP_DEVSTACK_ACKNOWLEDGED` musi być
zmienną środowiska procesu (`environment:` kontenera). Serwer w profilu `devstack` publikuje manifest z `"environment": "dev"`.

System serwera (`server-id` `mcp-<system>`) musi być jednym z `SystemId` kontraktów (`jira`, `confluence`, `gitlab`,
`servicedesk`, `opensearch`): inaczej serwer nie przejdzie rejestracji. Serwer `example` (template) i próbny działają
tylko w profilu `dev` na hoście; w stosie jako próbne nie nadają się. Przykład dla `mcp-jira`:

```yaml
environment:
  SPRING_PROFILES_ACTIVE: devstack              # jedyny aktywny profil
  MCP_DEVSTACK_ACKNOWLEDGED: "true"
  AIWCORPO_MCP_PLATFORM_DEVSTACK_ACKNOWLEDGED: "true"
  AIWCORPO_MCP_PLATFORM_POLICY_ALLOW_TEST_KEYS: "true"
  AIWCORPO_MCP_PLATFORM_POLICY_MAIN_KEY_ID: dev-policies-main      # identyfikator klucza paczek stosu (test-* lub dev-*)
  AIWCORPO_MCP_PLATFORM_INTERNAL_JWT_ALLOW_TEST_KEYS: "true"       # gdy gateway podpisuje kluczami testowymi lub dev-
  MCP_TLS_KEYSTORE: /run/tls/keystore.p12                           # certyfikat serwera (SAN mcp-jira.internal)
  MCP_TLS_TRUSTSTORE: /run/tls/truststore.p12                       # CA wewnętrzne, także dla JWKS
  MCP_TLS_STORE_PASSWORD: ${TLS_STORE_PASSWORD}                     # hasło obu magazynów (z sekretów stosu)
  MCP_GATEWAY_JWKS_URI: https://mcp-gateway.internal:8443/.well-known/jwks.json
  MCP_CONNECT_URL: https://portal.internal/connections?system={system}
  MCP_INTERNAL_URL: https://mcp-jira.internal:8443/mcp              # adres w manifeście
  MCP_JIRA_URL: https://jira.internal                               # API systemu (mock po http: MCP_JIRA_ALLOW_PLAIN_HTTP)
  MCP_POLICY_URL: https://policies.internal/policies/main/latest.json   # repozytorium paczek stosu (https)
  MCP_POLICY_PUBLIC_KEY_FILE: /run/policy/policies-main.public.pem
  MCP_POLICY_STATE_DIR: /var/lib/mcp-jira/policy-state
  MCP_REDIS_HOST: redis
  MCP_REDIS_USER: mcp-jira
  MCP_REDIS_PASSWORD: ${REDIS_MCP_JIRA_PASSWORD}
```

Adresy `*.internal` i ścieżki plików odpowiadają stosowi `mcp-platform-dev` (`docker-compose.yml`); sekrety pochodzą z jego `.secrets`. Reszta zostaje
jak na produkcji (TLS i mTLS, Redis na `jti`, `policy.state-dir`). Każdy start loguje `WARN event=devstack_profile`, a
metryka `mcp_server_devstack_active` ma wartość 1. Bez obu zgód albo z drugim profilem start jest odrzucany.

## Testy

| Klasa | Co sprawdza |
| --- | --- |
| `GetRecordToolTest` | poświadczenie z C3 dociera do systemu; typ poświadczenia spoza listy serwera daje `internal_error` bez wywołania systemu; jedno wywołanie na rekord; `readOnlyHint`, tytuł, zamknięty schemat, `maxLength` i `pattern`; brak poświadczenia i 401 systemu -> `credential_required`; 404 i 403 -> ten sam `not_found_or_denied`; zły `recordId` nie dociera do systemu i nie wraca w błędzie; nieczytelna odpowiedź 200 i rekord bez `id` -> `internal_error` bez treści w logu; przekierowanie nie jest wykonywane; timeout -> `upstream_unavailable`; za duża odpowiedź ucięta; poświadczenie nie trafia do wyniku ani logu |
| `ResourcePolicyTest` | rekord w projekcie `restricted` i rekord przeniesiony do takiego projektu dają `not_found_or_denied` identyczne jak nieistniejący; rekord z nieznaną klasyfikacją nie jest wydawany (`internal_error`, kontrakt; użytkownik bez grantu dostaje `not_found_or_denied` przed resolverem); każda odmowa ma stały tekst `ToolErrorCode.NOT_FOUND_OR_DENIED_MESSAGE`; `access_denied` bez grupy i bez wywołania systemu; awaria systemu to nie odmowa |
| `SearchRecordsToolTest` | wyniki bez rekordów zastrzeżonych i przeniesionych; tylko wiersze żądanego projektu; zapytanie zawężone do żądanego projektu; kolejka bez grantu nie jest pytana (Service Desk); cudzysłów w tekście od modelu zostaje literałem; hity rozwiązane jednym wywołaniem; `credential_required`, `access_denied`, błędne argumenty |
| `ManifestTest` | manifest przechodzi reguły gateway'a, definicje równe `tools/list` (zgodny `definitionHash`), tytuły i zamknięte schematy, `resourceRefs`, 401 bez JWT; narzędzia z `ManifestExport` (plik z budowy) równe `tools/list` serwera; bez `environment` poza `devstack`, `acceptedTypes` z typem systemu |
| `GatewayOnlyAccessTest` | bez JWT 401 (`tools/call`, `ping`, `initialize`), JWT dla innego serwera, innego narzędzia, wariant serwisowy i przeterminowany -> 401; token z claimem `vs` albo `vsc` (usunięte w kontraktach 5.x) -> 401; bez JWT tylko health; system zgodny z `SystemId` (albo jawnie próbny) |
| `ProductionConfigurationTest` | typ domyślny jest pierwszym z `accepted-credential-types` (baza i `dev`), brak `credential-type`; konfiguracja bazowa: `client-auth: need`, paczka polityk i manifest wymagane, bez kluczy testowych; klucze testowe i repozytorium polityk dev tylko w `application-dev.yml`; backend `http` poza `dev` odrzucony (chyba że jawnie dopuszczony); `dev` na 127.0.0.1 |
| `IndexResolutionTest` (tylko serwer OpenSearch) | wzorce zakresu idą tylko do `_resolve/index`, wyszukiwanie dostaje nazwy bez aliasu i parametry listy nazw, brak grantu nie woła systemu |
| `DevProfileLoopbackTest` | reguła adresu `dev` (`assertDevProfileLoopbackRule`) i obecność guardów w kontekście serwera (`assertServerIsGuarded`) |
| `CommentToolsTest` | strona komentarzy bez cudzych wpisów i dopełniona kolejnymi stronami systemu, kursor innego rekordu odrzucony, rekord spoza zakresu daje ten sam błąd co brakujący, komentarz zapisany pod innym rekordem wygląda jak nieistniejący, id spoza wzorca nie dociera do systemu |
| `UpdateRecordToolTest` | zmiana tytułu i wynik z systemu, powtórzenie daje ten sam stan, `UPDATE` bez `readOnlyHint` i `destructiveHint`, brak grupy zapisu, rekord `restricted`, zły tytuł, 401 systemu |
| `DegradedModeTest` | wersja nieprzetestowana (bramka zapisu rzuca): zmiana odmówiona jako `upstream_unavailable` i nic nie zapisane, odczyty działają |
| `VersionProbeTest` | sonda czyta wersję atrapy bez poświadczenia i serwer dostaje stan `SUPPORTED` z zapisem |
| `ExampleToolTest` | walidacja tekstu wyszukiwania (stały komunikat), limity i zamknięte schematy narzędzi, pełna lista narzędzi, `get_record` z kontekstem z `TestResourceContext` |
| `ExampleServiceTest` | limit wyników wyszukiwania, filtr przynależności komentarzy i sprawdzenie id komentarza, bez Springa |
| `ExampleCapabilityProbeTest`, `ExampleWriteGateTest` | ocena wersji (w zakresie, starsza, nowsza, nieczytelna) i bramka zapisu we wszystkich stanach, także po nieudanej sondzie |

Nowe narzędzie dostaje test w tym samym stylu: klasa dziedziczy po `ServerTestSupport`, odpowiedź systemu
dopisuje się w `StubBackend`. Polityka w testach to paczka z przykładów kontraktów (`TestGateway`; dla serwera spoza pięciu systemów
generowana: grupy `mcp-<system>-read|write|destructive`, odczyt, `restricted`). Serwer próbny nie ma poprawnego manifestu
(`serverId` jest enumem kontraktów), więc osiem testów `ManifestTest` jest w template'cie i w serwerze próbnym pomijanych
(`assumeFalse(TRIAL_SYSTEM)`: raportowane jako "skipped", nie jako błąd) i wykonywanych w każdym serwerze wygenerowanym dla
systemu z `SystemId`; `credential_required` jest w serwerze próbnym `internal_error`. 
Identyfikatory przykładowych rekordów muszą pasować do wzorca typu zasobu z kontraktów (starter sprawdza go przed
resolverem), dlatego `SampleScope` jest osobny dla każdego systemu.

## Instalacja

Wymagania, układ plików, kroki dla VM z systemd i typowe problemy: `docs/operations/deployment-vm-systemd.md`
(`deploy/scripts/install.sh`, `update.sh`, `healthcheck.sh`, szablon środowiska `deploy/systemd/mcp-example.env.template`).
Wariant kontenerowy: `Dockerfile` (po `./mvnw verify`; obraz `eclipse-temurin:25-jre`, glibc, bo `cedar-java` ładuje bibliotekę
natywną, użytkownik `mcp`, port 8443, mTLS) i `ci/run.sh image`; kontener dostaje tę samą konfigurację ze zmiennych
środowiska i plików TLS montowanych tylko do odczytu. Artefakty platformy (`mcp-platform-starter`, `mcp-contracts`) są w GitHub
Packages organizacji `aiwcorpo`; przy budowie ze źródeł wystarczy `~/.m2` z nimi albo rejestr w `MCP_MAVEN_REPOSITORY_URL`
(+ `_USER`, `_PASSWORD`), układ `settings.xml` opisuje README `mcp-starter-lib`, rozdział "Instalacja".

## Codzienna obsługa

- Zdrowie: `deploy/scripts/healthcheck.sh` (wymaga certyfikatu klienta); szczegół `backendCapability` pokazuje wersję systemu i stan sondy.
- Aktualizacja: `deploy/scripts/update.sh <nowy.jar>`, wycofanie: przywrócić kopię `.bak`; po aktualizacji zarejestrować w
  gateway'u świeży manifest z `mcp-app/target/mcp-manifest.json`.
- `MCP_PAGING_CURSOR_KEY` jest taki sam na wszystkich instancjach; zmiana klucza unieważnia kursory w locie (żyją 15 minut), nic poza tym.
- Wygasłe poświadczenia: użytkownik dostaje `credential_required` z odnośnikiem do portalu, po stronie serwera nic nie trzeba robić.
- Audyt C8 idzie na stdout (journald), logger `pl.aiwcorpo.mcp.audit`; linia `backend capability: ... state=degraded` oznacza
  zapis wyłączony do czasu przetestowania nowej wersji systemu.

## Dokumentacja

- `docs/jak-napisac-narzedzie.md`: zasady i kolejność kroków przy nowym narzędziu.
- `docs/architecture/overview.md`: architektura i decyzje.
- `docs/security/SECURITY.md`: niezmienniki bezpieczeństwa w tym serwerze.
- `docs/api/tools-catalog.md`: rejestr narzędzi.
- `docs/operations/deployment-vm-systemd.md`, `deploy/`, `Dockerfile`: wdrożenie.
