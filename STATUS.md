# STATUS - mcp-server-template (ws-03)

M1: osiągnięty (2026-10-27) - template na starterze 3.0.0-alpha-10, `get_record` z poświadczeniem z C3, 401 bez JWT, `new-server.sh --trial demo` (i `new-server.sh jira`) buduje się i przechodzi testy bez zmian ręcznych.

M3: osiągnięty (2026-10-28) - template na starterze 3.0.0-beta-4 (kontrakty 3.1.0; przestawiony z beta-1, beta-2 i beta-3, patrz niżej): `@ToolTitle`, zamknięte schematy z `contractPattern`, `get_record` z `@ResourceRef` i `ResourceResolver`, `search_records` z `QueryScope` i `ResultFilter`, `policy.*` i `manifest.*` (dev i prod), `GET /mcp-manifest` zgodny z `tools/list`, `DevProfileLoopbackTest` z `assertServerIsGuarded`; `new-server.sh` generuje serwery jira, gitlab, confluence, servicedesk, opensearch i `--trial demo`, każdy buduje się i przechodzi `verify` (62 testy).

Starter: BOM `pl.aiwcorpo.mcp:mcp-platform-bom:3.0.0-beta-4` (kontrakty 3.1.0, Spring Boot 4.1.0, Spring AI
2.0.0, Java 25). Commity: 7f65f8f (M1), c799e54, c1258c4, 83b9f3c (alpha-10), 00f87f4, 80603f4 (alpha-11), następnie M3
(patrz `git log`). Wyszukiwanie nie przyjmuje od modelu JQL/CQL: `ExampleSearchQuery` buduje zapytanie przez `StructuredQuery`.

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
cd <workspace>/mcp-server-template
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@25/25.0.4.1/libexec/openjdk.jdk/Contents/Home
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
cd <workspace>/mcp-server-template && ./new-server.sh jira /tmp/mcp-server-jira-ref
diff -ru /tmp/mcp-server-jira-ref <repo serwera> -x .git -x target
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
