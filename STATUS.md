# STATUS - mcp-server-template (ws-03)

M1: osiągnięty (2026-10-27) - template na starterze 3.0.0-alpha-10, `get_record` z poświadczeniem z C3, 401 bez JWT, `new-server.sh --trial demo` (i `new-server.sh jira`) buduje się i przechodzi testy bez zmian ręcznych.

M3: osiągnięty (2026-10-28) - template na starterze 3.0.0-beta-4 (kontrakty 3.1.0; przestawiony z beta-1, beta-2 i beta-3, patrz niżej): `@ToolTitle`, zamknięte schematy z `contractPattern`, `get_record` z `@ResourceRef` i `ResourceResolver`, `search_records` z `QueryScope` i `ResultFilter`, `policy.*` i `manifest.*` (dev i prod), `GET /mcp-manifest` zgodny z `tools/list`, `DevProfileLoopbackTest` z `assertServerIsGuarded`; `new-server.sh` generuje serwery jira, gitlab, confluence, servicedesk, opensearch i `--trial demo`, każdy buduje się i przechodzi `verify` (62 testy).

M4 (2026-10-29): template i generator na starterze 3.0.0-beta-6 (kontrakty 4.0.0). Kamienie: (1) pom, `credential-type`, nowe API `StructuredQuery`, stały
tekst `not_found_or_denied`; (2) profil Service Desk bez trybu `serverConfiguredQueues`, grant kolejek tylko z paczki polityk; (3) profil OpenSearch
z rozwijaniem wzorców do nazw indeksów; (4) dokumentacja stosu dev i luki z smoke beta-5. Wszystkie cztery zrobione (sekcja "M4" niżej).

Starter (stan sprzed M4): BOM `pl.aiwcorpo.mcp:mcp-platform-bom:3.0.0-beta-4` (kontrakty 3.1.0, Spring Boot 4.1.0, Spring AI
2.0.0, Java 25). Commity: 7f65f8f (M1), c799e54, c1258c4, 83b9f3c (alpha-10), 00f87f4, 80603f4 (alpha-11), następnie M3
(patrz `git log`). Wyszukiwanie nie przyjmuje od modelu JQL/CQL: `ExampleSearchQuery` buduje zapytanie przez `StructuredQuery`.

## M4d: beta-8 -> beta-9 (2026-10-29)

Podbicie na 3.0.0-beta-9 (link tylko zawęża). Confluence w testach wraca na przestrzeń `ABC` bez grup zakresu (obejście `mcp-confluence-space-ENG` z beta-8 usunięte);
nowy test `aReaderWithoutAScopeGroupReadsInsideALink` (czytelnik bez grupy czyta przez link; dla Service Desku i OpenSearch, gdzie `READ_REQUIRES_SCOPE`, oczekiwane
`not_found_or_denied`). Rekord w nieznanej przestrzeni bez grupy zakresu dalej jest `not_found_or_denied` (`UNCLASSIFIED_OUTSIDE_SCOPE` zostaje dla Confluence).

Manifest w zwykłym buildzie (decyzja koordynatora): `mcp-app/.../ManifestExport` zapisuje `mcp-app/target/mcp-manifest.json` w fazie `package` (`exec-maven-plugin`, także
`-DskipTests`), z konfiguracji produkcyjnej (`application.yml` bez profilu) i `McpToolsConfiguration`, bez startu serwera; używa `ManifestGenerator` i `ToolHintsPostProcessor`
ze startera, więc manifest odrzucany przez gateway zatrzymuje build. `manifest.export.skip=true` w template'cie i serwerach próbnych (`system` spoza `SystemId`);
`new-server.sh` ustawia `false` dla systemu z listy. `ManifestTest` porównuje definicje narzędzi z pliku z `tools/list` (bez `definitionHash`, który obejmuje typy poświadczeń;
test ustawia jeden typ, produkcja pełną listę). `tests/new-server-test.sh --build` sprawdza, że pięć serwerów ma plik, a `demo` nie. Z testów usunięte `manifest.output-file`.
`McpToolsConfiguration.mcpTools` jest publiczne (czyta je eksporter). Wzorzec dla ws-04: skopiować `ManifestExport`, blok `exec-maven-plugin` i właściwość `manifest.export.skip=false`
z wygenerowanego serwera (albo przenieść różnicą z `new-server.sh jira ../ref`); nową klasę narzędzi dopisać i w `McpToolsConfiguration`, i w `ManifestExport.manifest()` (eksporter tworzy je z `null` zamiast zależności).
`./mvnw clean verify` w template'cie: 70 testów, 9 pominiętych; `--build`: 0 błędów.

## M4c: beta-7 -> beta-8 (2026-10-29)

Podbicie na 3.0.0-beta-8; commit osobny. Template: `ExampleSearchQuery` (Jira, Confluence) używa `scope.narrowedTo(List.of(projekt))` zamiast filtrowania wierszy po
odpowiedzi; cofnięte `ExampleRecordHit.project` i zawężanie w `ExampleService`. Argument `project` zostaje bez `@ResourceRef` (starter wymaga wtedy resolvera typu
kontenera `Jira::Project`/`Confluence::Space` i start się nie udaje; link na inne projekty ma dawać pusty wynik). Odstępstwo od zalecenia z MIGRATION ("ids z argumentu
`@ResourceRef`"): `narrowedTo` waliduje wzorzec identyfikatora i tylko zawęża, a hity filtruje `ResultFilter`; jeśli serwer doda resolver kontenera, może dodać `@ResourceRef`.

Testy: zasób spoza linku to `not_found_or_denied` (nie `access_denied`; asercja `doesNotContain("access_denied")`); nowy test, że użytkownik bez grupy zakresu czyta
rekord prawami systemu (`READ_REQUIRES_SCOPE` false: Jira, Confluence, GitLab; true dla konta serwisowego Service Desku i OpenSearch). Konto serwisowe: błąd resolvera i
awaria systemu to `not_found_or_denied` (`resolverFault`). Confluence: przestrzeń `ENG` z grupą `mcp-confluence-space-ENG` zostaje, ale tylko dlatego, że reguła
`vs-narrow-confluence-page` (link) porównuje z `resource.scopes`; czytanie bez linku grupy nie wymaga. OpenSearch: `OPENSEARCH_RESOLVE_PARAMETERS` w całości.
`./mvnw clean verify`: 69 testów, 9 pominiętych (jak wyżej); `tests/new-server-test.sh --build`: 0 błędów.

Dla ws-04: Jira/Confluence `narrowedTo` zamiast filtra wierszy; testy zasobu spoza linku na `not_found_or_denied`; Service Desk: awaria resolvera to `not_found_or_denied`;
Confluence z linkiem w testach nadal potrzebuje grupy zakresu przestrzeni.

## M4b: beta-6 -> beta-7, kontrakty 4.1.0 (2026-10-29)

Wg MIGRATION "Z beta-6 na beta-7"; commit osobny od M4. `./mvnw clean verify`: 69 testów, 9 pominiętych (jak niżej: 8 `ManifestTest` + 1 `QUERY_CARRIES_LINK`);
`tests/new-server-test.sh --build`: 0 błędów (jira, confluence, gitlab, servicedesk, opensearch, demo).

- `credential-type` usunięte: `accepted-credential-types` z typem domyślnym na pierwszym miejscu (`new-server.sh`: gitlab `user-oauth,user-secret`,
  servicedesk `service-account`, opensearch `token-exchange`, reszta `user-secret`). Test: żądanie z typem spoza listy to `internal_error` przed backendem
  (`GetRecordToolTest`), `ServerTestSupport.clientWithToken(..., typ)`; `TestGateway` nadal dostaje listę z jednym typem systemu.
- OpenSearch: `opensearchSearchTarget` (puste = nie pytaj), `OPENSEARCH_RESOLVE_PARAMETERS` (`expand_wildcards=open`), 404 z `_resolve/index` jako
  `EMPTY_RESOLVE_RESPONSE`; testy `IndexResolutionTest` o 404 i parametrach.
- `ResourcePolicy` z listą typów: szablon nie buduje go sam, bez zmian.
- Zakres w paczce na kontener: testy Confluence używają przestrzeni `ENG` z grupą `mcp-confluence-space-ENG`, Service Desk kolejki `42`
  (`mcp-servicedesk-queue-42`); rekord w kontenerze poza zakresami wywołującego to `not_found_or_denied`, także gdy brakuje klasyfikacji
  (`UNCLASSIFIED_OUTSIDE_SCOPE`). Paczka serwera Service Desku musi nadawać zakresy `mcp-servicedesk-queue-*`.
- Dla ws-04 (ponad listę M4): usunąć `credential-type`, ustawić kolejność `accepted-credential-types`, w testach grupy zakresu (Confluence `mcp-confluence-space-<KEY>`),
  OpenSearch przez `opensearchSearchTarget` i `OPENSEARCH_RESOLVE_PARAMETERS`, 404 resolve jako pusty; dla wszystkich beta-7 w pom.

## M4: beta-5 -> beta-6, kontrakty 4.0.0 (2026-10-29)

Wg MIGRATION "Z beta-5 na beta-6". Testy: template 68 (`./mvnw clean verify`: mcp-app 62, mcp-application 3, mcp-tools 3), z czego 9 pominiętych
(8 `ManifestTest` oraz `theLinkTravelsInsideTheQueryWhereTheSystemHasAQueryLanguage`, które wymaga `QUERY_CARRIES_LINK`), czyli 59 wykonanych.
Pominięcie `ManifestTest` jest zamierzone: `serverId` manifestu to enum kontraktów, a template (`example`) i serwer próbny (`demo`) nim nie są;
testy wykonują się w każdym serwerze wygenerowanym dla systemu z `SystemId`. `tests/new-server-test.sh --build`: 0 błędów, `verify` zielone dla
jira, confluence, gitlab, servicedesk, opensearch i `--trial demo`. Serwer OpenSearch ma dodatkowo `IndexResolutionTest` (5 testów).

Zmiany w template'cie i generatorze:

1. `pom.xml`: `mcp-platform.version` 3.0.0-beta-6 (właściwość; `new-server.sh` czyta wersję z niej).
2. `credential-type` w `application.yml`; `new-server.sh` ustawia je i `accepted-credential-types` (baza i `dev`) per system: jira, confluence
   `user-secret`; gitlab `user-oauth` (przyjmuje też `user-secret`); servicedesk `service-account`; opensearch `token-exchange`. Testy:
   `SampleScope.CREDENTIAL_TYPE` jest typem w `serverProperties` (nadpisanie `credential-type` i `accepted-credential-types`) i w
   `asGateway(..., typ)`; `ProductionConfigurationTest` pilnuje zgodności yml z tym typem.
3. `StructuredQuery`: Jira `scope.jql(Set.of("text"))` z `text CONTAINS`, Confluence `scope.cql(Set.of("title"))`. `project` i `space` nie są polami warunku
   (lista normatywna), więc `ExampleService.searchRecords(query, project)` zawęża wiersze do żądanego zakresu po odpowiedzi; `ExampleRecordHit` ma `project`.
   Wiersz bez projektu jest odrzucany. Kolejność w zapytaniu: `(warunki) AND project in (...)`.
4. `not_found_or_denied`: żadnego własnego tekstu w serwerze nie było; `ResourcePolicyTest` sprawdza stałą `ToolErrorCode.NOT_FOUND_OR_DENIED_MESSAGE`.
5. Service Desk: w template'cie nie było `serverConfiguredQueues` ani `MCP_SERVICEDESK_ALLOWED_QUEUES`. Profil pyta o kolejkę tylko wtedy, gdy
   `nothingToQuery()` jest fałszem, a kolejka jest w `grantedIds()` i w linku. Testy servicedesk używają kolejki `42` (jedyna, którą przykładowa paczka
   kontraktów przyznaje grupą `mcp-servicedesk-queue-42`) i `ONLY_GRANTED_PROJECTS`: rekord w kolejce bez grantu to `not_found_or_denied`, wyszukiwanie
   w niej nie woła systemu.
6. OpenSearch: `ExampleSearchQuery.indices` (nothingToQuery, `_resolve/index` z `opensearchIndexExpression`, `resolveIndexNames`, zawężenie do żądanego
   prefiksu); port `resolveIndices` i `search(indices, text, limit)`; wyszukiwanie dostaje listę nazw i `OPENSEARCH_SEARCH_PARAMETERS`, tekst jako
   `QueryScope.quote`. `resolveIndexArgument` jest opisane (narzędzia z indeksem jako argumentem), template nie ma takiego narzędzia.
7. Adapter rozdzielony: `ExampleBackendClient` (poświadczenie, timeouty, przekierowania, limit odpowiedzi, mapowanie błędów) i `ExampleResourceAdapter`
   (ścieżki i DTO). Dzięki temu profil OpenSearch podmienia tylko adapter.
8. Manifest: testy zapisują `mcp-app/target/mcp-manifest.json` (`manifest.output-file`, artefakt dla rejestracji), `ManifestTest` sprawdza zgodność pliku
   z odpowiedzią i brak `environment` poza `devstack` (`environment: dev` dodaje starter w profilu `devstack`).
9. `new-server.sh` dodaje pliki profilu bez odpowiednika w template'cie (`IndexResolutionTest`).
10. README: wersje, właściwość `mcp-platform.version`, `credential-type`, lista zmiennych w "Uruchomienie w stosie dev" (`MCP_TLS_KEYSTORE`,
    `MCP_TLS_TRUSTSTORE`, `MCP_TLS_STORE_PASSWORD`, `MCP_INTERNAL_URL`, `MCP_GATEWAY_JWKS_URI`, `MCP_CONNECT_URL`), klucze `dev-`, system musi być
    `SystemId` (`example` działa tylko jako próbny), wyjaśnienie pominiętych `ManifestTest`.

Do przeniesienia przez ws-04 (każdy serwer; pozostałe pliki serwera zostają):

- Wszystkie: `pom.xml` na 3.0.0-beta-6 (`mcp-platform.version` albo wersja w imporcie BOM); `credential-type` w `application.yml` (jeden typ w
  `accepted-credential-types` wystarczy bez niego, kilka typów wymaga go jawnie); testy z `asGateway(user, credential, typ)` i nadpisaniem
  `credential-type`; zapytania z nowego API (poniżej); testy odmowy ze stałą `NOT_FOUND_OR_DENIED_MESSAGE`; test, który oczekiwał
  `internal_error` dla zasobu bez klasyfikacji, woła użytkownikiem z grantem (bez grantu: `not_found_or_denied` przed resolverem); licznik
  `mcp_audit_authentication_suppressed_total` zastępuje `mcp_server_audit_authentication_suppressed_total` w alertach; `manifest.output-file`
  w teście lub w kroku budowy jako artefakt do rejestracji (certyfikat CI `mcp-registry-ci-<serverId>`); `README`: starter beta-6, kontrakty 4.0.0.
- Jira: `scope.jql(Set.of("text", "status", "assignee", "summary" - tylko użyte))`, bez `project` jako warunku; `IN`/`NOT_IN`/zakresy dat/pola
  `labels`, `created`, `cf[...]` znikają (jedna wartość, `EQUALS`/`CONTAINS`/`NOT_EQUALS`); `ORDER BY` z `created`, `updated`, `priority`, `key`;
  zawężenie do żądanego projektu po odpowiedzi (`hit.project`), bo JQL nie ma `project` w polach. `credential-type: user-secret`.
- Confluence: `scope.cql(Set.of("text", "title", "label" - tylko użyte))`, `ORDER BY` z `created`, `lastmodified`, `title`; przestrzeń jako filtr wierszy.
  `credential-type: user-secret` (broker stosu ma tylko `user-secret`).
- GitLab: `credential-type: user-oauth`, `accepted-credential-types: [user-oauth, user-secret]`; adapter dla obu typów (OAuth `Authorization: Bearer`
  przez `applyTo`, PAT `PRIVATE-TOKEN` przez `value()` zależnie od `credential.type()`); brak budowniczego zapytań w starterze, więc zmiana tylko w
  testach i typie.
- Service Desk: `credential-type: service-account`; usunąć `MCP_SERVICEDESK_ALLOWED_QUEUES`, `serverConfiguredQueues` i wszelką ścieżkę, w której
  konfiguracja serwera otwiera kolejkę (dostęp tylko z grantów w paczce: `grantedIds()`, `nothingToQuery()`); kolejka poza zakresem linku w
  argumencie to `not_found_or_denied`, nie `access_denied` (adapter i test); wyszukiwanie i listy pytają tylko o kolejki z `grantedIds()` i z linku;
  testy z grupą `mcp-servicedesk-queue-<id>` przyznającą kolejkę (w paczce kontraktów jest tylko `42`).
- OpenSearch: `credential-type: token-exchange` i `accepted-credential-types: [token-exchange]` (rejestracja z samym `user-secret` jest odrzucana);
  nigdzie wzorzec `p-*` w ścieżce wyszukiwania, `_count` ani agregacji: `GET _resolve/index/<opensearchIndexExpression()>`, `resolveIndexNames`
  (bez aliasów i strumieni), pytanie do `String.join(",", nazwy)` z `OPENSEARCH_SEARCH_PARAMETERS`; indeks z argumentu przez `resolveIndexArgument`;
  `requireNoAliases` usunąć (przestarzałe), `aggregate_logs` może wrócić po wdrożeniu tego (ws-09 zdejmuje wtedy regułę wyłączającą);
  `@ArgumentLimits(contractPattern = "OpenSearchIndexPrefix")` bez obejścia; `expiresAt` tokenu zawsze podany, więc nie cache'ować dłużej.
- Stos dev: `SPRING_PROFILES_ACTIVE=devstack` z dwiema zgodami i `allow-test-keys` dla kluczy `dev-` (manifest ma wtedy `environment: dev`).

Propozycje CC: (a) `project`/`space` jako pole warunku JQL/CQL (dziś `search_records(project, text)` musi filtrować wiersze po odpowiedzi systemu, a limit
wyników liczy wiersze wszystkich projektów użytkownika); (b) w pakiecie testowym `TestGateway` więcej grup kolejek Service Desku niż `42`, żeby testy
wielu kolejek konta serwisowego nie wymagały własnej paczki. Poza tym brak.

## Z beta-1 na beta-2 (2026-10-28)

Wg MIGRATION "Z beta-1 na beta-2": `search_records` buduje JQL przez `scope.jql(...).where(...).build()` (profil confluence:
`scope.cql`; gitlab, servicedesk i opensearch nie mają budowniczego w starterze i sprawdzają zakres linku ręcznie,
składnia zapytania w stubie jest przykładowa); port wyszukiwania przyjmuje gotowe zapytanie (`search(query, limit)`);
resolver ma `fetchAll` (jedno wołanie `?ids=` na wszystkie hity); test jednostkowy `get_record` przez
`TestResourceContext.open(...).withFetched(...)`; `@ResultWithoutResources` nie jest potrzebne (oba wyniki to rekordy;
opisane w docs). Zmiana zachowania: rekord z nieznaną klasyfikacją kontenera to `internal_error` (kontrakt), nie
`not_found_or_denied`; w wyniku listy taki element jest usuwany.

## Z beta-3 na beta-4 (2026-10-28)

Podbicie wersji; README ma przykład uruchomienia w stosie dev z profilem `devstack` (zgody w konfiguracji stosu, nie w obrazie).

## Z beta-2 na beta-3 (2026-10-28)

Podbicie wersji w `pom.xml`. `TestGateway.start("mcp-example")` ma w beta-3 reguły zasobów, więc testy template'u (i serwera
próbnego) działają na `mcp-example`, bez udawania `mcp-jira`: `ServerTestSupport.POLICY_SYSTEM` usunięte, `ManifestTest`
pomija serwer próbny (manifest `serverId` poza enumem kontraktów jest odrzucany, w `dev` to ostrzeżenie; opis w
`docs/jak-napisac-narzedzie.md`), a `credential_required` w serwerze próbnym jest `internal_error`. Nowe narzędzie z wynikiem
tekstowym wymaga `@ResultWithoutResources` (template zwraca rekordy, więc nie dotyczy). Testy: 62 (6 z `ManifestTest`
pominięte w template'cie, wykonywane w 5 wygenerowanych serwerach).

## M3: co zrobiono i co serwery muszą zrobić ponad MIGRATION startera

Template: punkty 1-9 sekcji "Z alpha-14 na beta-1" (MIGRATION-3.0.md) plus alpha-12..14 (pierwszy test
`DevProfileLoopbackTest` z `assertServerIsGuarded`, brak wykluczeń platformy). Czego MIGRATION nie mówi, a serwery
na to trafią:

1. Starter sprawdza wartość każdego `@ResourceRef` wzorcem kontraktu **swojego typu** przed resolverem
   (`Jira::Issue` wymaga `^[A-Z][A-Z0-9_]{1,9}-[1-9][0-9]{0,9}$`, `ConfluencePageId` liczby itd.). Identyfikatory w
   stubach i testach muszą pasować (`ABC-PARTIAL` daje `invalid_argument` zamiast zamierzonego błędu). Wzorzec argumentu:
   `@ArgumentLimits(contractPattern = ...)`, nie własny regex.
2. Paczka polityk `TestGateway` zna typy zasobów Jira/Confluence/GitLab/...; adapter bierze system z `server-id`, nie ze stałej. OpenSearch czyta tylko w zakresie grup `mcp-opensearch-idx-*` (potrzebne w tokenie
   testowym), Jira `destructive` wymaga `mfaAgeSeconds(60)`. Klucz `vsc` linku zależy od systemu (`jiraProjects`,
   `confluenceSpaces`, `gitlabGroups`, `servicedeskQueues`, `opensearchIndexPrefixes`).
3. `@ResourceRef` na wyniku listy to jedno pytanie resolvera (czyli systemu) na każdy hit: wyszukiwanie ze 50 wynikami
   robi do 50 wywołań `fetch`. Do pomiaru w serwerach z dużymi listami; cache jest na czas wywołania.
4. Narzędzie czyta rekord z `ResourceContext.current().orElseThrow().fetched(typ, id, Klasa.class)`; własne
   `RecordNotFoundException` i `ToolErrorMapping` dla "nie ma" przestają być potrzebne (resolver zwraca pusty
   `Optional`, a starter odpowiada `not_found_or_denied`). Awaria systemu w resolverze to `internal_error` dla 5xx
   (`upstream_unavailable` dla 502-504 i błędów I/O), nie odmowa.
5. Atrybut klasyfikacji, którego system nie podał, trzeba pominąć, nie wypełniać `""`: pominięcie odrzuca zasób (beta-2: `internal_error`)
   (Jira `category`, Confluence `labels`, OpenSearch `tags`, GitLab `ancestorClassifications`). Wyjątek: kolejka service
   desku (klasyfikacja opcjonalna).
6. Konfiguracja: `manifest.internal-url` musi być `https` także w `dev`; `policy.main.public-key-file` w `dev` jest
   względem katalogu, z którego startuje proces (repozytorium serwera: `../mcp-policies/keys/dev/...`); wersja
   manifestu to `@project.version@` (`0.1.0-SNAPSHOT` jest poprawnym SemVer). Do plików środowiska i compose dochodzą
   `MCP_POLICY_URL`, `MCP_POLICY_KEY_ID`, `MCP_POLICY_PUBLIC_KEY_FILE`, `MCP_POLICY_STATE_DIR`, `MCP_INTERNAL_URL`.
7. Dockerfile nie może być oparty o Alpine (cedar-java, glibc): `eclipse-temurin:25-jre` z przypiętym digestem.
8. Tekst z systemu po `normalize`: U+2028, U+2029 i U+0085 stają się spacją, brzegowe spacje zostają; testy
   porównujące dokładny tekst trzeba poprawić (sekcja 4 MIGRATION).
9. Wyszukiwanie: zakres jest argumentem (nie `@ResourceRef`), bo link zawężony do innych zakresów ma dać pusty wynik, nie
   błąd; `total` z systemu nie jest zwracany. `DESTRUCTIVE` (Jira `delete_issue`): wzór w
   `docs/jak-napisac-narzedzie.md`, template go nie zawiera (powstałby w każdym serwerze).

Czego potrzebuje ws-02: `TestGateway` nie ma paczki dla systemu spoza `SystemId`, stąd tożsamość `mcp-jira` w testach
serwera próbnego; paczka testowa z własną grupą dla `mcp-example` uprościłaby `ServerTestSupport`. Propozycje CC: brak.

## Co działa

- Migracja z 2.0.0: usunięte OIDC, `guard-policy-local.yml`, `GuardedToolsTest`, `TestIdentityProvider`, tryb
  PAT, mock w pamięci i narzędzia `example_*`. Pakiety `pl.aiwcorpo.mcp.example.*`, artefakty `mcp-example-*`.
- `get_record(recordId)`, `READ_ONLY`:
  - limity w `inputSchema` (`maxLength` 64, `pattern`) przez `@ArgumentLimits` i `SchemaLimitedToolCallbacks`,
    te same stałe w walidacji w kodzie (`InvalidToolArgumentException`, stały tekst bez wartości argumentu);
  - adapter: `CredentialContext.require("example")` + `credential::applyTo`, timeouty 2 s / 10 s, bez
    przekierowań (3xx -> `internal_error`), limit odpowiedzi 1 MB, `https` poza `dev` (jawny wyjątek
    `MCP_<SYSTEM>_ALLOW_PLAIN_HTTP` dla mocków środowiska dev, z ostrzeżeniem w logu);
  - błędy: 404 i 403 -> `not_found_or_denied` (ten sam tekst), 401 -> `credential_required`, błąd HTTP ->
    wyjątek z samym statusem, nieczytelna odpowiedź 2xx i rekord bez `id` -> `BackendResponseException` bez
    treści i bez przyczyny (`internal_error`).
- Konfiguracja bazowa produkcyjna: mTLS (`client-auth: need`, bundle z `MCP_TLS_*`), JWKS po TLS z CA
  wewnętrznym, `jti` w Redisie. Profil `dev` (testy, uruchomienie lokalne): HTTP na 127.0.0.1, bez mTLS, `jti` w
  pamięci, klucze testowe; starter alpha-5 odmawia startu `dev` na adresie innym niż pętla zwrotna.
  `/actuator/prometheus` nie jest wystawiony (był nieosiągalny: wymaga JWT gateway'a).
- README: moduły i miejsce każdej rzeczy, "Uruchomienie lokalne bez środowiska" (profil `dev`, bez sekretów) i
  "Uruchomienie z mcp-platform-dev (mTLS)", ryzyko profilu `dev` w jarze.
- `new-server.sh [--trial] <system> [katalog]`: nazwa z `SystemId` kontraktów (czytana z jara `mcp-contracts` w
  `~/.m2`), inna tylko z `--trial`; odrzuca nazwy kolidujące z pakietami startera i słowa kluczowe Javy; ścieżka
  celu rozwiązywana do bezwzględnej przed sprawdzeniem, nie może istnieć ani leżeć w template'ie; tylko pliki
  śledzone przez git; przy błędzie cel jest usuwany; dla nazwy z listy ustawia `TRIAL_SYSTEM = false`.
- Dockerfile, unit systemd (dodane `CapabilityBoundingSet=`, `SystemCallFilter=@system-service`, `ProtectProc`,
  `UMask`), `install.sh` tworzy plik środowiska 0640 root:mcp i działa z dowolnego katalogu.

## Dowód (2026-10-28, alpha-10)

`./mvnw -B clean verify`: 28 testów, 0 błędów (24 w mcp-app, w tym `GetRecordToolTest` 16 z wywołaniem z `vs`/`vsc` i rekordem częściowym; 2 w mcp-application, 2 w mcp-tools).
Poniżej dowód z alpha-5 (2026-10-27), skrypty `new-server.sh` bez zmian.

## Dowód (2026-10-27, alpha-5)

- `./mvnw -B verify` w template: 22 testy, 0 błędów (`GetRecordToolTest` 14, `GatewayOnlyAccessTest` 4,
  `ProductionConfigurationTest` 3, `DevProfileLoopbackTest` 1 w mcp-app; `ExampleToolTest` 2;
  `ExampleServiceTest` 2).
- `tests/new-server-test.sh`: 17/17 (odmowy: nazwa spoza `SystemId`, `x;rm`, `../evil`, wielkie litery,
  `platform`, `new`, `example`, istniejący cel, cel w template'ie; cel względny ląduje w katalogu wywołującego i
  drugi raz jest odrzucony; `TRIAL_SYSTEM`; brak plików template'u i `example`; template bez zmian).
- W katalogu tymczasowym poza repo: `new-server.sh --trial demo`, `new-server.sh jira`, `new-server.sh gitlab`,
  każdy `git init` + `./mvnw -B verify` od zera: 22/22. `jira` i `gitlab` dostają pełny `credential_required`,
  `demo` `internal_error` (system spoza listy). Katalog usunięty.
- Uruchomienie wg README: `demo` w `dev` (health 200, `POST /mcp` bez JWT 401); `jira` w konfiguracji bazowej z
  certyfikatami `mcp-platform-dev` (health po mTLS 200, `POST /mcp` bez JWT 401); `jira` z `MCP_JIRA_URL=http://...`
  bez `ALLOW_PLAIN_HTTP` nie wstaje.
- Mutacje z recenzji pokrywają teraz testy: `Redirect.ALWAYS` -> `aRedirectIsNotFollowedAndNotReadAsMissing`,
  brak timeoutów -> `aSlowBackendTimesOut`, `client-auth` inny niż `need` ->
  `outsideDevTheServerDemandsAClientCertificate`.

Pełnego przebiegu z prawdziwym gateway'em nie robiłem (test integracyjny z ws-01 z DoD briefu; plan na M2).

## Jak wygenerować serwer (dla ws-04)

```bash
cd mcp-server-template
./mvnw -B verify                           # raz: kontrakty w ~/.m2 (skrypt czyta z nich SystemId)
./new-server.sh jira                       # -> ../mcp-server-jira (nie może istnieć)
cd ../mcp-server-jira && git init && ./mvnw -B verify   # 62 testy, bez zmian
```

Zamiany (`example`/`Example`/`EXAMPLE` -> `<system>`/`<System>`/`<SYSTEM>` w treści i ścieżkach) i lista tego,
co serwer uzupełnia: `docs/nowy-serwer.md`. W skrócie: domena, ścieżki API i DTO w adapterze (GitLab:
`PRIVATE-TOKEN` przez `credential.value()`), narzędzia z tabeli ws-04 z `@ArgumentLimits` i rejestracją przez
`SchemaLimitedToolCallbacks.of(...)`, `StubBackend` i testy dla każdego narzędzia, README i katalog narzędzi,
własny `STATUS.md`. Od M3 template ma też resolver, `QueryScope`, `ResultFilter` i manifest; profile systemów w `profiles/`.

## Zmiany do przeniesienia do serwerów (od alpha-10; stan sprzed M3)

Zrobione w template'cie przy przejściu na 3.0.0-alpha-10 (z alpha-5); serwery jira i confluence mają to samo
przeniesione (patrz ich STATUS.md). Serwer wygenerowany z tego commitu nie wymaga niczego z listy.

1. `pom.xml`: `<mcp-platform.version>3.0.0-alpha-11</mcp-platform.version>`.
2. `mcp-tools`: usunięte lokalne `ArgumentLimits.java` i `SchemaLimitedToolCallbacks.java`; importy
   `pl.aiwcorpo.mcp.platform.input.ArgumentLimits` i `...input.SchemaLimitedToolCallbacks` (też w
   `McpToolsConfiguration` i testach). Adnotacja ze startera ma `minLength`, `maxLength`, `pattern`,
   `contractPattern`, `maxItems`, `minItems`, `minimum`, `maximum` i działa na rekordach; liczby, listy i
   komponenty rekordów bez limitów zatrzymują start. `StrictArgumentsInterceptor` nigdy nie było w template'cie.
3. Wynik narzędzia: `ExampleRecordView` (`title` jako `UntrustedText`, `status` `@Nullable`) zamiast domeny.
   Nowe serwery mapują domenę na rekord wyniku w `mcp-tools`; kolejność dla tekstu z systemu: konwersja,
   `TextHygiene.normalize`, `UntrustedText.cut`, filtr serwera, `plainCut|markdownCut` (bez filtra wystarczy
   `UntrustedText.plain`). Test `GetRecordToolTest` sprawdza `text` i `kind` zamiast gołego `title`.
4. `application-dev.yml`: usunięte `replay-store: memory` i `mtls.required: false` (starter w profilu `dev` robi
   to sam, razem z `server.ssl.enabled=false` i pustym `jwks-ssl-bundle`). `allow-test-keys` zostaje.
5. Testy: `ServerTestSupport.clientWithToken(...)` i `GetRecordToolTest.acceptsTheVirtualServerClaimsOfACallThroughALink`
   (JWT z `vs` i `vsc:{}`, jak od gateway'a M2 przy wywołaniu przez link) przechodzi.
6. `SPRING_PROFILES_ACTIVE=dev` nie może być w `.env`, compose, jednostce systemd ani obrazie (start odmawia);
   template tego nie ustawia, `dev` tylko w komendzie z README.

## Zmiany do przeniesienia do serwerów (od 7f65f8f, już zrobione w jira i confluence do alpha-6)

Serwery ws-04 wygenerowane z 7f65f8f. Najprościej: wygenerować świeżą kopię z tą samą nazwą obok i przenieść
pliki z listy (nazwy po zamianie, przykład dla `jira`; `<S>` = `Jira`, `<s>` = `jira`):

```bash
cd mcp-server-template && ./new-server.sh jira ../mcp-server-jira-ref
diff -ru ../mcp-server-jira-ref <repo serwera> -x .git -x target
```

1. `pom.xml`: `<mcp-platform.version>3.0.0-alpha-5</mcp-platform.version>`.
2. `mcp-app/src/main/resources/application.yml`: w `<s>.backend` dodać `max-response-size: 1MB` i
   `allow-plain-http: ${MCP_<S>_ALLOW_PLAIN_HTTP:false}`; `management.endpoints.web.exposure.include: health`
   (bez `prometheus`). `spring.data.redis.timeout` niepotrzebny (domyślny startera 2 s).
3. `mcp-app/src/main/resources/application-dev.yml`: cały plik (relaksacje `dev`: JWKS, klucze testowe, `jti` w
   pamięci, bez mTLS, domyślny portal i backend).
4. `mcp-integration`: nowe `BackendResponseException.java`, `BoundedBodyInterceptor.java`;
   `<S>BackendProperties.java` (pola `maxResponseSize`, `allowPlainHttp`); w `<S>ResourceAdapter.java`:
   konstruktor z `Environment`, `requestInterceptor(new BoundedBodyInterceptor(...))`, `defaultStatusHandler`
   także dla 3xx, bloki `catch` w kolejności NotFound/Forbidden -> pusty wynik, Unauthorized ->
   `CredentialRequiredException`, `RestClientResponseException | ResourceAccessException` -> dalej,
   `RestClientException` -> `BackendResponseException`, pusta odpowiedź -> `BackendResponseException`;
   `requireBaseUrl` z regułą `https`; `toDomain()` z kontrolą `id`. Te wzorce przenieść do każdego adaptera
   serwera, nie tylko do przykładu.
5. `mcp-tools`: nowe `ArgumentLimits.java`, `SchemaLimitedToolCallbacks.java`; `<S>Tool.java`: stałe wzorca i
   długości, `@ArgumentLimits` na argumentach tekstowych, stały komunikat walidacji; `pom.xml` modułu:
   `spring-boot-starter-test` w `test`; test `src/test/.../tools/<S>ToolTest.java`.
6. `mcp-app/.../config/McpToolsConfiguration.java`: `SchemaLimitedToolCallbacks.of(...)` zamiast
   `MethodToolCallbackProvider`.
7. Testy `mcp-app/src/test/.../app/`:
   - `ServerTestSupport.java`: `TRIAL_SYSTEM` (dla serwerów z ws-04 `false`), nowe `assertCredentialRequired`
     (bez akceptowania `internal_error` dla prawdziwego systemu), test `contractSystemIsKnown`, krótkie limity
     `read-timeout`/`max-response-size` w `@DynamicPropertySource`;
   - `StubBackend.java`: drugi serwer (cel przekierowania), pula wątków, rekordy `ABC-BADJSON`, `ABC-NOID`,
     `ABC-302`, `ABC-SLOW`, `ABC-HUGE`;
   - `GetRecordToolTest.java`: testy schematu (`maxLength`, `pattern`), nieczytelnej odpowiedzi, rekordu bez
     `id`, przekierowania, timeoutu, za dużej odpowiedzi, złego argumentu (bez echa wartości); odpowiedniki dla
     narzędzi serwera;
   - nowe `ProductionConfigurationTest.java`, `DevProfileLoopbackTest.java`.
8. `deploy/systemd/mcp-<s>.service` (nowe dyrektywy sandboxu), `deploy/scripts/install.sh`.
9. Dokumentacja: README (sekcje uruchomienia, ryzyko `dev`, tabela testów), `docs/jak-napisac-narzedzie.md`,
   `docs/security/SECURITY.md`, `docs/api/tools-catalog.md`.

## Odstępstwa od briefu

- Zmienne środowiska jak w `mcp-platform-dev/docker-compose.yml`, nie jak w briefie: `MCP_GATEWAY_JWKS_URI`,
  `MCP_REDIS_HOST/PORT/USER/PASSWORD`, `MCP_<SYSTEM>_URL`, `MCP_TLS_*`. `server-id` stały w `application.yml`
  (ustawia go `new-server.sh`). `MCP_POLICY_*` dopiero z paczką polityk (beta).
- Endpoint `/mcp` zamiast `/mcp/<system>` (tak adresuje gateway, domyślne ustawienie startera).
- DoD "new-server.sh demo": `demo` nie jest systemem z kontraktu, więc od poprawek po recenzji wymaga `--trial`.
- `update_record`, `purge_records`, `search_records`, retry i circuit breaker, CI z manifestem: M2.

## Znane ograniczenia i odłożone znaleziska recenzji (LOW)

- L5: zamknięte (starter alpha-6: błąd walidacji schematu wejścia jest w formacie C10 `invalid_argument`).
- L7: Redis `jti` bez TLS (hasło ACL otwartym tekstem w sieci wewnętrznej). Do decyzji przy wdrożeniu; Spring
  ma `spring.data.redis.ssl.bundle`, template tego nie włącza, bo Redis w `mcp-platform-dev` nie ma TLS.
- L9: zamknięte (`ExampleRecordView.title` to `UntrustedText`; `ResultFilter` dopiero w M3).
- Serwer systemu spoza `SystemId` (template `example`, próbny `demo`) wstaje tylko w `dev`; `credential_required`
  jest wtedy `internal_error`.
- Zrobione z LOW: L1 (3xx), L2 (rekord bez `id`), L3 i L4 (dokumentacja), L6 (`new-server.sh`), L7 częściowo
  (`prometheus`), L8 (limit odpowiedzi), L10 (systemd, `install.sh`), L11 (ten plik).

## Czego potrzebują inni

- ws-01: usługa `mcp-jira` (i pozostałe serwery) w `docker-compose.yml` potrzebuje
  `MCP_JIRA_ALLOW_PLAIN_HTTP: "true"`, dopóki mock Jiry jest po `http` (serwer poza `dev` przyjmuje tylko `https`).
  Alternatywa: mock po TLS z CA wewnętrznym i `MCP_JIRA_SSL_BUNDLE: mcp-internal-ca`. `MCP_REDIS_KEY_PREFIX`
  nie jest czytany (prefiks wynika z `server-id`), nieszkodliwy. `MCP_CONNECT_URL` już jest.
- ws-02: L5 zamknięte w starterze, template używa już `ArgumentLimits` i `SchemaLimitedToolCallbacks` ze startera.
- ws-04: sekcja "Zmiany do przeniesienia do serwerów (od 7f65f8f)" wyżej; nowe serwery od bieżącego commitu.
- ws-05: bez zmian (`/mcp`, SAN `mcp-<system>.internal`, JWKS gateway'a).

## Propozycje zmian kontraktu

Brak.

## Runda 4: poprawki przeglądu alpha-10 (2026-10-28)

| Znalezisko | Status | Dowód |
| --- | --- | --- |
| T3-L1 brak testu rekordu z pustymi polami opcjonalnymi; `project` wymagane w wyniku | poprawione: `project` jest `@Nullable` (adapter wymaga tylko `id`), stub ma rekord `ABC-PARTIAL` bez `project` i `status` | `GetRecordToolTest.aRecordWithoutTheOptionalFieldsIsAnOkResultWithNulls` |
| T3-L2 STATUS nieaktualny | poprawione: liczby testów (28 w template i w wygenerowanym serwerze), L5 i L9 zamknięte | |
| A10-M1 `DevProfileLoopbackTest` | bez zmian, rozwiąże starter alpha-11 | |

## Runda 5: starter alpha-11 (2026-10-28)

BOM alpha-10 -> alpha-11 (MIGRATION-3.0.md, sekcja "Zmiany w 3.0.0-alpha-11"). `DevProfileLoopbackTest` sprawdza regułę guarda przez `DevProfileGuardAssertions.assertDevProfileLoopbackRule()` (A10-M1), więc przechodzi także w kontenerze CI. `./mvnw clean verify`: 28 testów, 0 błędów; `INVOCATION_ID=ci ./mvnw -B verify`: bez różnic. Start lokalny w profilu `dev`: health 200, POST /mcp bez JWT 401.

## Dowód M3 (2026-10-28)

`./mvnw -B clean verify` w template: 62 testy, 0 błędów (mcp-app 55: `GetRecordToolTest` 17, `SearchRecordsToolTest` 14,
`ResourcePolicyTest` 7, `ManifestTest` 6, `ProductionConfigurationTest` 5, `GatewayOnlyAccessTest` 4,
`DevProfileLoopbackTest` 1 + `contractSystemIsKnown` w każdej klasie dziedziczącej; mcp-application 2, mcp-tools 2).
`tests/new-server-test.sh --build`: wszystkie kontrole, w tym `verify` na wygenerowanych serwerach jira, gitlab,
confluence, servicedesk, opensearch i `--trial demo` (generowane w scratchpadzie, nie w `mcp-server-*`). Mutacje:
usunięcie `@ResourceRef` z hitu wyszukiwania oraz wyłączenie kontroli linku łamią `SearchRecordsToolTest`.
Uruchomienia procesu hosta nie robiłem (testy idą przez `TestGateway` i stub backendu).

## M5: starter 3.0.0-beta-10, kontrakty 5.1.0 (2026-10-31)

- Wersja `mcp-platform.version` 3.0.0-beta-10 (BOM przypina kontrakty 5.1.0); README poprawione.
- Usunięte z kodu i testów: `virtualServer(...)` w testach, `LINK_KEY`, `linkIds()` (martwe gałęzie w `ExampleSearchQuery` Jiry,
  GitLaba, Service Desku, Confluence), `withinLink` -> `within(scopeIds, id)`; testy zawężenia linkiem usunięte, test akceptacji
  `vs` zastąpiony `GatewayOnlyAccessTest.aTokenWithTheClaimsOfTheRemovedVirtualServersIsRejected` (`vs` i `vsc` -> 401).
- CI: `ci/run.sh` (verify, hygiene, image), `Makefile`, `.gitlab-ci.yml`, `Jenkinsfile`, `.github/workflows/ci.yml`, `.ci/settings*.xml`.
  MR/PR bez sekretów i bez gniazda Dockera, `image` tylko na `main`, perl `<<>>`, nieznany krok = 2, 0 uruchomionych testów = błąd.
- Dowód: `ci/run.sh verify` 57 przypadków uruchomionych (9 pominięte: `ManifestTest` w template'cie, jeden warunkowy);
  `tests/new-server-test.sh --build` 0 błędów (jira, gitlab, confluence, servicedesk, opensearch, demo, w tym hygiene wygenerowanych).
M5: osiągnięty (2026-10-31) - template na beta-10 i kontraktach 5.1.0, CI ci/run.sh

Poprawki po przeglądzie serwerów (2026-10-31): hygiene nie przechodzi przy błędzie git/xargs/perl; liczniki testów per moduł
(`CI_TEST_MODULES`, `CI_APP_MIN_TESTS`); krok `generator` (`tests/new-server-test.sh --build`) w CI szablonu; tag obrazu
`mcp-server-<system>:<prefiks>-<sha7>`; `ManifestTest` porównuje hash (gdy listy typów są równe) i wersję z pom; GitHub: Environment `ci-main`, checkout przypięty do SHA.

## M6: starter 3.0.0-beta-14, wzorce pełnych serwerów i porządki do wydania (2026-11-03)

- `mcp-platform.version` 3.0.0-beta-14, kontrakty 6.3.2 (MIGRATION-3.0.md, "Z beta-10" do "Z beta-14"): hashe i manifest bez zmian dla
  serwera natywnego, test zliczający pliki wektorów nie istnieje w szablonie.
- Serwer przykładowy dostał wzorce `mcp-server-jira` i `mcp-server-confluence`: `ExampleCapabilityProbe` i `ObservedVersions`,
  `ExampleWriteGate` (fail closed przy nieznanej wersji, flaga `MCP_EXAMPLE_WRITES_ON_UNKNOWN_VERSION`), `update_record` (`UPDATE`, wartość
  docelowa), `list_comments` (`PagedResult`, `CursorCodec`, `PageFiller`), `get_comment` (id podrzędne sprawdzane względem ocenionego
  rekordu), `ExampleToolErrors`. Opis: `docs/nowy-serwer.md`, "Wzorce pełnych serwerów".
- Profile: confluence, gitlab, servicedesk dostają te same narzędzia; opensearch (tylko odczyt) tylko sondę (`serverInfo` w porcie i adapterze),
  testy zapisu i komentarzy pomijane przez `SampleScope.FULL_TOOL_SET`. W profilach confluence i gitlab `OPEN_PROJECT` to `ENG` i `team-a`,
  bo zapis wymaga grantu z przykładowej paczki kontraktów (`SampleScope.WRITE_GRANTS`).
- Nie zrobione: `search_records` nie ma kursora (wzorzec stronicowania z filtrem polityki jest w `JiraTool.searchIssues`, opisany w dokumencie);
  `delete_*` (`DESTRUCTIVE`) bez przykładu poza opisem w `docs/jak-napisac-narzedzie.md`.
- Testy: `ci/run.sh verify` 87 uruchomionych (application 4, integration 3, tools 8, app 72; 9 pominiętych: `ManifestTest` w szablonie i jeden warunkowy);
  `tests/new-server-test.sh --build` 0 błędów (jira, gitlab, confluence, servicedesk, opensearch, demo); nowe kontrole: pliki wydania w
  wygenerowanym serwerze, przypięcie akcji do SHA i obrazów do digestów, licencja w pom.
- Porządki: LICENSE, NOTICE (też w jarach), SECURITY, CONTRIBUTING, CHANGELOG (szablon ma własny, generator pisze nowy), `release.yml` kopiowany
  do serwerów (w szablonie wyłączony warunkiem na repozytorium), `ci/run.sh` szuka JDK 25, obrazy CI po digeście, README i dokumenty bez ścieżek z katalogu użytkownika.
  Workflow nie był uruchomiony; SHA akcji i digesty skopiowane z `mcp-server-confluence` i `mcp-starter-lib` (weryfikacja `git ls-remote` i
  `docker buildx imagetools inspect` nie powiodła się: brak połączenia z GitHubem i Docker Hub w chwili pracy).
