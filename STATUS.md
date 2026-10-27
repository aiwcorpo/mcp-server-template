# STATUS - mcp-server-template (ws-03)

M1: osiągnięty (2026-10-27) - template na starterze 3.0.0-alpha-5, `get_record` z poświadczeniem z C3, 401 bez JWT, `new-server.sh --trial demo` (i `new-server.sh jira`) buduje się i przechodzi testy bez zmian ręcznych.

Starter: BOM `pl.aiwcorpo.mcp:mcp-platform-bom:3.0.0-alpha-5` (kontrakty 2.0.2, Spring Boot 4.1.0, Spring AI
2.0.0, Java 25). M2 nie jest zaczęty. Commity: 7f65f8f (M1), c799e54 (uruchomienie lokalne, alpha-4), bieżący
(poprawki po recenzji M1, alpha-5).

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
cd ../mcp-server-jira && git init && ./mvnw -B verify   # 22 testy, bez zmian
```

Zamiany (`example`/`Example`/`EXAMPLE` -> `<system>`/`<System>`/`<SYSTEM>` w treści i ścieżkach) i lista tego,
co serwer uzupełnia: `docs/nowy-serwer.md`. W skrócie: domena, ścieżki API i DTO w adapterze (GitLab:
`PRIVATE-TOKEN` przez `credential.value()`), narzędzia z tabeli ws-04 z `@ArgumentLimits` i rejestracją przez
`SchemaLimitedToolCallbacks.of(...)`, `StubBackend` i testy dla każdego narzędzia, README i katalog narzędzi,
własny `STATUS.md`. `@ResourceRef`, `QueryScope`, `ResultFilter`, manifest C6 dopiero ze starterem beta (M2).

## Zmiany do przeniesienia do serwerów (od 7f65f8f)

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

- L5: błąd walidacji schematu wejścia (MCP SDK) nie jest w formacie C10 (`Tool (...) input validation failed`);
  po dodaniu `pattern`/`maxLength` dotyczy też złych identyfikatorów. Tekst nie zawiera wartości argumentu.
  Do starteru (ws-02).
- L7: Redis `jti` bez TLS (hasło ACL otwartym tekstem w sieci wewnętrznej). Do decyzji przy wdrożeniu; Spring
  ma `spring.data.redis.ssl.bundle`, template tego nie włącza, bo Redis w `mcp-platform-dev` nie ma TLS.
- L9: treść z systemu bez oznaczenia jako dane (wzorzec w M2 razem z `ResultFilter`).
- Serwer systemu spoza `SystemId` (template `example`, próbny `demo`) wstaje tylko w `dev`; `credential_required`
  jest wtedy `internal_error`.
- Zrobione z LOW: L1 (3xx), L2 (rekord bez `id`), L3 i L4 (dokumentacja), L6 (`new-server.sh`), L7 częściowo
  (`prometheus`), L8 (limit odpowiedzi), L10 (systemd, `install.sh`), L11 (ten plik).

## Czego potrzebują inni

- ws-01: usługa `mcp-jira` (i pozostałe serwery) w `docker-compose.yml` potrzebuje
  `MCP_JIRA_ALLOW_PLAIN_HTTP: "true"`, dopóki mock Jiry jest po `http` (serwer poza `dev` przyjmuje tylko `https`).
  Alternatywa: mock po TLS z CA wewnętrznym i `MCP_JIRA_SSL_BUNDLE: mcp-internal-ca`. `MCP_REDIS_KEY_PREFIX`
  nie jest czytany (prefiks wynika z `server-id`), nieszkodliwy. `MCP_CONNECT_URL` już jest.
- ws-02: L5 (błąd walidacji schematu w formacie C10). `SchemaLimitedToolCallbacks`/`@ArgumentLimits` lepiej
  pasują do startera (pięć serwerów kopiuje ten sam kod); template przejdzie na wersję ze startera, gdy będzie.
- ws-04: sekcja "Zmiany do przeniesienia do serwerów (od 7f65f8f)" wyżej; nowe serwery od bieżącego commitu.
- ws-05: bez zmian (`/mcp`, SAN `mcp-<system>.internal`, JWKS gateway'a).

## Propozycje zmian kontraktu

Brak.
