# Nowy serwer z template'u

Ten plik, `new-server.sh`, `tests/new-server-test.sh`, `STATUS.md` i katalog `profiles/` nie są kopiowane do
wygenerowanego serwera.

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
pilnuje zgodności. Przykładowa paczka polityk kontraktów, na której działają testy, zna tylko te pięć systemów, więc
serwer próbny uruchamia w testach tożsamość `mcp-jira` (`ServerTestSupport.POLICY_SYSTEM`); kod serwera tego nie widzi,
bo system bierze z `server-id`.

### Profile systemów

Template jest serwerem w kształcie Jiry (`Jira::Issue` w `Jira::Project`). Dla pozostałych systemów skrypt podmienia
trzy pliki plikami z `profiles/<system>/` (ścieżki te same co w template'cie):

| Plik | Co zmienia |
| --- | --- |
| `mcp-core/.../domain/ExampleResourceModel.java` | typ zasobu i kontenera, wzorce identyfikatorów z kontraktów (`contractPattern`), reguła zawężenia linkiem |
| `mcp-integration/.../ExampleResourceDescription.java` | jak rekord jest opisany polityce: atrybuty wymagane przez schemat Cedar danego systemu (GitLab: `ancestorClassifications`) |
| `mcp-app/src/test/.../SampleScope.java` | kształt identyfikatorów w testach, klucz `vsc` linku, grupy odczytu (OpenSearch) |

Profile: `gitlab` (`GitLab::Project` w `GitLab::Group`), `confluence` (`Confluence::Page` w `Confluence::Space`),
`servicedesk` (`ServiceDesk::Ticket` w `ServiceDesk::Queue`, klasyfikacja kolejki opcjonalna), `opensearch`
(`OpenSearch::Index` bez kontenera, tagi indeksu). `jira` i serwery próbne używają template'u bez podmiany. Każdy
profil jest budowany i przechodzi `verify` (`tests/new-server-test.sh --build`).

Ścieżka docelowa (także względna, liczona od bieżącego katalogu) nie może istnieć ani leżeć w template'ie.
Kopiowane są tylko pliki śledzone przez git; przy błędzie katalog docelowy jest usuwany. Test skryptu:
`tests/new-server-test.sh` (`--build` dodatkowo buduje wygenerowany `jira`).
Skrypt zamienia placeholder w treści i ścieżkach:

| W template | Po `new-server.sh jira` |
| --- | --- |
| groupId i pakiety `pl.aiwcorpo.mcp.example`, katalogi `pl/aiwcorpo/mcp/example` | `pl.aiwcorpo.mcp.jira` |
| `server-id`, `spring.application.name`, `spring.ai.mcp.server.name`: `mcp-example` | `mcp-jira` |
| artefakty `mcp-example-parent`, jar `mcp-example.jar`, unit `mcp-example.service` | `mcp-jira-...` |
| system z `server-id` (adapter czyta go z konfiguracji) | `jira` |
| `MCP_EXAMPLE_URL`, `MCP_EXAMPLE_SSL_BUNDLE`, właściwości `example.backend.*` | `MCP_JIRA_URL`, `jira.backend.*` |
| klasy `ExampleRecord`, `ExampleTool`, `ExampleService`, ... | `JiraRecord`, `JiraTool`, `JiraService`, ... |

Endpoint zostaje `/mcp` (tak go adresuje gateway: `internal-url: https://mcp-jira.internal:8443/mcp`).
Na końcu skrypt sprawdza, że w nowym projekcie nie zostało żadne `example`.

## Co serwer musi uzupełnić

1. Domena: zamienić `<System>Record` (`id`, `project`, `projectClassification`, `title`, `status`) na właściwe
   obiekty systemu (np. `JiraIssue`); tylko pola, które narzędzia oddają i których potrzebuje polityka.
2. Adapter `<System>ResourceAdapter`: prawdziwe ścieżki API systemu zamiast `/api/records/...`, DTO odpowiedzi,
   sposób podania poświadczenia (`credential::applyTo` dla `Authorization`; GitLab z `PRIVATE-TOKEN` przez
   `credential.value()`), mapowanie 404/403 -> pusty wynik, 401 -> `CredentialRequiredException`. Timeouty są w
   `<system>.backend.*`.
3. Resolver i opis zasobu: `<System>ResourceDescription` ma podawać atrybuty **z systemu** (kategoria projektu Jiry,
   etykiety przestrzeni, topics grup GitLaba wszystkich przodków, tagi indeksu). Wzór dla Jiry i GitLaba:
   `docs/jak-napisac-narzedzie.md`. Zasób bez wymaganego atrybutu jest odrzucany, więc nie wypełniaj brakujących
   pustymi wartościami.
4. Narzędzia z tabeli ws-04 w `<System>Tool` (albo kilku klasach), każde z `@ToolRisk`, `@ToolTitle`, limitami
   argumentów i `@ResourceRef` na każdym identyfikatorze zasobu, dopisane w `McpToolsConfiguration`;
   `get_record` i `search_records` usunąć albo przerobić. Wyszukiwanie przez `QueryScope`, wynik przez
   `@ResourceRef` w rekordzie albo `ResultFilter`. Narzędzie `DESTRUCTIVE` (Jira `delete_issue`) z akcją `delete` i
   uprawnieniem `mcp-<system>-destructive`.
5. Testy: `StubBackend` odpowiada jak API systemu (albo test woła mock z `mcp-platform-dev`); dla każdego narzędzia
   z zasobem: zasób `restricted` daje `not_found_or_denied` identyczne jak nieistniejący, wyszukiwanie z linkiem
   zawężonym nie zwraca zasobów spoza zakresu, brak grupy daje `access_denied`, a `ManifestTest` pilnuje zgodności
   manifestu z `tools/list`. Do tego pozytywny, `credential_required` (brak poświadczenia i 401 systemu),
   `invalid_argument`. `GatewayOnlyAccessTest` zostaje (po zmianie nazwy narzędzia w `GET_RECORD`).
6. Konfiguracja wdrożenia: `MCP_POLICY_URL`, `MCP_POLICY_KEY_ID`, `MCP_POLICY_PUBLIC_KEY_FILE`, `MCP_POLICY_STATE_DIR`,
   `MCP_INTERNAL_URL` (szablon: `deploy/systemd/mcp-<system>.env.template`). Wersja w manifeście to wersja projektu.
   Rejestracja manifestu w gateway'u: `GET /mcp-manifest` albo plik z `manifest.output-file`.
7. Dokumentacja: tabela narzędzi w README (tytuł, ryzyko, akcja, uprawnienie C9 `mcp-<system>-read|write|destructive`,
   typ poświadczenia) i `docs/api/tools-catalog.md`; własny `STATUS.md` z linią `M3`.
8. Wdrożenie w `mcp-platform-dev`: obraz `docker build -t mcp-<system>:dev .` (Dockerfile nie może być oparty o
   Alpine: `cedar-java` jest zbudowany pod glibc) dla usługi `mcp-<system>` w profilu `full`. Compose ustawia dziś
   `MCP_JIRA_URL`, `MCP_GATEWAY_JWKS_URI`, `MCP_REDIS_*`, `MCP_TLS_*` i `MCP_CONNECT_URL`; dochodzą zmienne polityk
   z punktu 6. Mock Jiry jest po `http`, więc usługa potrzebuje też `MCP_JIRA_ALLOW_PLAIN_HTTP=true`. Profilu `dev` w
   kontenerze nie używać.
