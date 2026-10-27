# Nowy serwer z template'u

Ten plik, `new-server.sh`, `tests/new-server-test.sh` i `STATUS.md` nie są kopiowane do wygenerowanego
serwera.

## Generowanie

```bash
cd mcp-server-template
./new-server.sh jira                     # -> ../mcp-server-jira
./new-server.sh jira /sciezka/mcp-server-jira
./new-server.sh --trial demo /tmp/mcp-server-demo   # serwer próbny, tylko profil dev
cd ../mcp-server-jira && git init
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@25/25.0.4.1/libexec/openjdk.jdk/Contents/Home
./mvnw verify                            # przechodzi bez zmian ręcznych
```

Uruchomienie wygenerowanego serwera opisuje jego README (ta treść trafia do każdego serwera): "Uruchomienie
lokalne bez środowiska" (profil `dev`, bez sekretów i certyfikatów: health 200, `POST /mcp` bez JWT 401) i
"Uruchomienie z mcp-platform-dev (mTLS)".

`<system>` musi być na liście `SystemId` kontraktów (skrypt czyta ją z jara `mcp-contracts` w `~/.m2`, więc
template trzeba raz zbudować): `jira`, `confluence`, `gitlab`, `servicedesk`, `opensearch`. Tylko takie serwery
wstają poza profilem `dev`, i tylko dla nich `mcp-platform-dev` ma certyfikaty i użytkowników Redisa. Inna nazwa
wymaga `--trial` (małe litery i cyfry, nie `platform`, `audit`, słowa kluczowe Javy); serwer próbny buduje się,
przechodzi testy (oczekują `internal_error` zamiast `credential_required`) i działa w `dev` z ostrzeżeniem.
Dla nazwy z listy skrypt ustawia `TRIAL_SYSTEM = false` w `ServerTestSupport`, a test `contractSystemIsKnown`
pilnuje zgodności.

Ścieżka docelowa (także względna, liczona od bieżącego katalogu) nie może istnieć ani leżeć w template'ie.
Kopiowane są tylko pliki śledzone przez git; przy błędzie katalog docelowy jest usuwany. Test skryptu:
`tests/new-server-test.sh` (`--build` dodatkowo buduje wygenerowany `jira`).
Skrypt zamienia placeholder w treści i ścieżkach:

| W template | Po `new-server.sh jira` |
| --- | --- |
| groupId i pakiety `pl.aiwcorpo.mcp.example`, katalogi `pl/aiwcorpo/mcp/example` | `pl.aiwcorpo.mcp.jira` |
| `server-id`, `spring.application.name`, `spring.ai.mcp.server.name`: `mcp-example` | `mcp-jira` |
| artefakty `mcp-example-parent`, jar `mcp-example.jar`, unit `mcp-example.service` | `mcp-jira-...` |
| `CredentialContext.require("example")` (`ExampleResourceAdapter.SYSTEM`) | `"jira"` |
| `MCP_EXAMPLE_URL`, `MCP_EXAMPLE_SSL_BUNDLE`, właściwości `example.backend.*` | `MCP_JIRA_URL`, `jira.backend.*` |
| klasy `ExampleRecord`, `ExampleTool`, `ExampleService`, ... | `JiraRecord`, `JiraTool`, `JiraService`, ... |

Endpoint zostaje `/mcp` (tak go adresuje gateway: `internal-url: https://mcp-jira.internal:8443/mcp`).
Na końcu skrypt sprawdza, że w nowym projekcie nie zostało żadne `example`.

## Co serwer musi uzupełnić

1. Domena: zamienić `<System>Record` (`id`, `project`, `title`, `status`) na właściwe obiekty systemu (np.
   `JiraIssue`) i `RecordNotFoundException` na wyjątek domeny; tylko pola, które narzędzia oddają.
2. Adapter `<System>ResourceAdapter`: prawdziwe ścieżki API systemu zamiast `/api/records/{id}`, DTO
   odpowiedzi, sposób podania poświadczenia (`credential::applyTo` dla `Authorization`; GitLab z
   `PRIVATE-TOKEN` przez `credential.value()`), mapowanie 404/403 -> pusty wynik, 401 ->
   `CredentialRequiredException`. Timeouty są w `<system>.backend.*`.
3. Narzędzia z tabeli ws-04 w `<System>Tool` (albo kilku klasach), każde dopisane w `McpToolsConfiguration`;
   `get_record` usunąć albo przerobić na `get_issue`/`get_page`/... Zasady: `docs/jak-napisac-narzedzie.md`.
4. Testy: `StubBackend` odpowiada jak API systemu (albo test woła mock z `mcp-platform-dev`, np. Jira na
   `localhost:18081`); dla każdego narzędzia test pozytywny, `credential_required` (brak poświadczenia i 401
   systemu), `not_found_or_denied`, `invalid_argument`. `GatewayOnlyAccessTest` zostaje bez zmian (po
   zmianie nazwy narzędzia w `GET_RECORD`).
5. Dokumentacja: tabela narzędzi w README (ryzyko, uprawnienie C9 `mcp-<system>-read|write|destructive`, typ
   poświadczenia) i `docs/api/tools-catalog.md`; własny `STATUS.md`.
6. Wdrożenie w `mcp-platform-dev`: obraz `docker build -t mcp-<system>:dev .` dla usługi `mcp-<system>` w
   profilu `full`. Compose ustawia dziś `MCP_JIRA_URL`, `MCP_GATEWAY_JWKS_URI`, `MCP_REDIS_*`, `MCP_TLS_*`,
   i `MCP_CONNECT_URL`. Mock Jiry jest po `http`, więc usługa potrzebuje też `MCP_JIRA_ALLOW_PLAIN_HTTP=true`
   (STATUS.md template'u, "Czego potrzebują inni"). Profilu `dev` w kontenerze nie używać.

Po starterze beta (M2 template'u): `@ResourceRef` i resolver zasobów, `QueryScope`, `ResultFilter`,
manifest C6, polityka zasobów z paczki (`MCP_POLICY_BUNDLE_URL`, `MCP_POLICY_PUBLIC_KEY`). Template dostanie
wzorce `search_records`, `update_record`, `purge_records`; serwery przeniosą je ręcznie (skrypt działa tylko
przy generowaniu).
