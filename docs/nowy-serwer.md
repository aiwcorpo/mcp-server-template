# Nowy serwer z template'u

Ten plik, `new-server.sh`, `tests/new-server-test.sh`, `STATUS.md`, `CHANGELOG.md` szablonu i katalog `profiles/` nie są
kopiowane do wygenerowanego serwera (ten dostaje własny `CHANGELOG.md`). `LICENSE`, `NOTICE`, `SECURITY.md`, `CONTRIBUTING.md` i
`.github/workflows/release.yml` są kopiowane: serwer wychodzi z szablonu gotowy do wydania na zasadach platformy (Apache-2.0,
obraz na ghcr.io i SBOM z GitHub Actions, akcje przypięte do SHA).

## Generowanie

```bash
cd mcp-server-template
./new-server.sh jira                     # -> ../mcp-server-jira
./new-server.sh jira /sciezka/mcp-server-jira
./new-server.sh --trial demo ../mcp-server-demo   # serwer próbny, tylko profil dev
cd ../mcp-server-jira && git init
./mvnw verify                            # JDK 25 w JAVA_HOME; przechodzi bez zmian ręcznych (ci/run.sh verify sam szuka JDK)
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
pilnuje zgodności. Testy serwera próbnego i template'u działają na `mcp-<nazwa>` z paczką generowaną przez `TestGateway`;
osiem testów `ManifestTest` jest tam pomijanych (`assumeFalse`), bo manifest serwera spoza `SystemId` jest odrzucany (patrz `docs/jak-napisac-narzedzie.md`).

### Profile systemów

Template jest serwerem w kształcie Jiry (`Jira::Issue` w `Jira::Project`). Dla pozostałych systemów skrypt podmienia
pliki plikami z `profiles/<system>/` (ścieżki te same co w template'cie):

| Plik | Co zmienia |
| --- | --- |
| `mcp-core/.../domain/ExampleResourceModel.java` | typ zasobu i kontenera, wzorce identyfikatorów z kontraktów (`contractPattern`), reguła zawężenia do zakresu (`within`) |
| `mcp-integration/.../ExampleResourceDescription.java` | jak rekord jest opisany polityce: atrybuty wymagane przez schemat Cedar danego systemu (GitLab: `ancestorClassifications`) |
| `mcp-tools/.../ExampleSearchQuery.java` | zapytanie wyszukiwania: JQL (Jira, template) i CQL (Confluence) przez `StructuredQuery`; GitLab składa zapytanie z grupy i tekstu, Service Desk pyta tylko o granty (`nothingToQuery`), OpenSearch rozwija wzorce do nazw indeksów |
| `mcp-app/src/test/.../SampleScope.java` | kształt identyfikatorów w testach, grupy odczytu (OpenSearch) |

Profile: `gitlab` (`GitLab::Project` w `GitLab::Group`), `confluence` (`Confluence::Page` w `Confluence::Space`),
`servicedesk` (`ServiceDesk::Ticket` w `ServiceDesk::Queue`, klasyfikacja kolejki opcjonalna), `opensearch`
(`OpenSearch::Index` bez kontenera, tagi indeksu). `jira` i serwery próbne używają template'u bez podmiany. Każdy
profil jest budowany i przechodzi `verify` (`tests/new-server-test.sh --build`).

Pliki profilu bez odpowiednika w template'cie są dodawane (OpenSearch: `IndexResolutionTest`). OpenSearch podmienia
ponadto port, przypadek użycia, serwis, adapter, narzędzie i test narzędzia (wyszukiwanie po nazwach indeksów). Skrypt
ustawia też `accepted-credential-types` (typ domyślny pierwszy) w `application*.yml` (Jira i Confluence `user-secret`, GitLab
`user-oauth`, Service Desk `service-account`, OpenSearch `token-exchange`).

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

## Wzorce pełnych serwerów w serwerze przykładowym

Serwer przykładowy ma te same cztery wzorce, które mają pełne serwery `mcp-server-jira` (19 narzędzi) i `mcp-server-confluence`
(17 narzędzi), w najmniejszej postaci. Nowy serwer przenosi je razem z szablonem; zostaje dopasować zakres wersji, ścieżki API
i nazwy.

| Wzorzec | Przykład w szablonie | W pełnych serwerach |
| --- | --- | --- |
| Sonda wersji systemu (`CapabilityProbe`) | `ExampleCapabilityProbe` (mcp-integration): czyta `GET /api/server-info` bez poświadczenia, `classify` zamienia wersję na `SUPPORTED`, `DEGRADED` albo `UNAVAILABLE`; test `ExampleCapabilityProbeTest` | `JiraCapabilityProbe` (`/rest/api/2/serverInfo`, DC 9.12 do 11.x, Cloud jako `DEGRADED`), `ConfluenceVersionProbe` (`/rest/api/server-information`, a gdy za logowaniem manifest aplikacji, 8.5 do 10.x); zestawy atrap odpowiedzi per wersja w `mcp-app/src/test/resources/fixtures/` i `VersionFixturesTest` |
| Bramka zapisu zamknięta przy nieznanej wersji | `ExampleWriteGate.requireWritable()` jako pierwsza instrukcja `update_record`; `ObservedVersions` (mcp-core) pamięta wersję uznaną za nieprzetestowaną; flaga `MCP_EXAMPLE_WRITES_ON_UNKNOWN_VERSION`; testy `ExampleWriteGateTest`, `DegradedModeTest` (`@MockitoBean` bramki) | `JiraWriteGate` (flaga `MCP_JIRA_WRITES_ON_UNKNOWN_VERSION`), w Confluence `MCP_CONFLUENCE_WRITES_ON_UNKNOWN_VERSION`; `delete_*` i każde `UPDATE` wołają bramkę przed czymkolwiek innym |
| Wynik stronicowany: `PagedResult`, `CursorCodec`, `PageFiller` | `list_comments`: kursor niesie pozycję (`cursors.issue/open` z odciskiem narzędzia i argumentów), `PageFiller.fill` czyta do `FILL_READS` stron systemu, gdy filtr coś odrzucił, wynik to `PagedResult.of(items, nextCursor)`; klucz `MCP_PAGING_CURSOR_KEY` | `search_issues`, `list_comments`, `list_projects`, `list_worklogs` w Jirze; `search_pages`, `list_pages`, `list_child_pages`, `list_spaces` w Confluence (po filtrze polityki `PageFiller` + `ResultFilter.retainAllowedById`) |
| Id podrzędne sprawdzane względem ocenionego rodzica | `get_comment(recordId, commentId)`: rekord ocenia polityka (`@ResourceRef`), a `ExampleService.getComment` i `listComments` zostawiają tylko komentarze, które system przypisuje temu rekordowi (`RecordComment.belongsTo`); inaczej `NotFoundOrDeniedException` z tym samym tekstem co brak; testy `CommentToolsTest`, `ExampleServiceTest` | Confluence: lista stron podrzędnych zostawia strony, których ostatni przodek to oceniona strona, a wersja strony musi mieć `id` ze ścieżki (`ConfluenceResourceAdapter.pageSlice`, commit "Check that child ids belong to the judged page"); Jira: komentarz i worklog pod kluczem zgłoszenia, który zwrócił resolver |

Jak to działa i czego pilnować:

1. **Sonda nie ma poświadczenia.** Działa poza żądaniem, więc czyta tylko to, co system mówi każdemu (`ExampleBackendClient.getWithoutCredential`).
   System, którego wersja jest za logowaniem, potrzebuje innego źródła wersji (publiczny endpoint, manifest aplikacji) albo
   decyzji o poświadczeniu serwisowym w brokerze; sekretu w serwerze nie trzymamy. `detail` w `Capabilities` jest stałym tekstem serwera, nigdy
   komunikatem wyjątku ani treścią odpowiedzi.
2. **Zakres wersji ustawia ten, kto go przetestował.** `MIN_MAJOR`, `MIN_MINOR`, `MAX_MAJOR` w sondzie to zakres, na którym są
   atrapy i testy; wersja poza nim to `DEGRADED` (tylko odczyt), nie zgadywanie. Dołóż katalog `fixtures/<system>-<wersja>/` z prawdziwą
   odpowiedzią endpointu wersji i test, który przepuszcza je przez adapter i `classify`.
3. **Bramka jest pierwsza.** Narzędzie `UPDATE` i `DESTRUCTIVE` woła `gate.requireWritable()` przed walidacją i przed jakimkolwiek
   wywołaniem systemu; odmowa to `BackendDegradedException` mapowana przez `ExampleToolErrors` na `upstream_unavailable` z tekstem
   "Nothing was changed". Stany: `SUPPORTED` z cechą zapisu otwiera; `DEGRADED` zamyka zawsze; `UNAVAILABLE` i czas przed pierwszą
   odpowiedzią sondy zamykają do jawnej flagi, a wersja raz uznana za nieprzetestowaną zamyka bez względu na flagę.
4. **`UPDATE` to wartość docelowa.** Klient może powtórzyć wywołanie po `502` (CC-204), więc narzędzie ustawia stan (tytuł, etykiety,
   `expectedVersion` w Confluence), zamiast go zwiększać. Gdy system nie pozwala, opisz to w README.
5. **Strona to `PagedResult`, bez sumy.** Suma z systemu zawiera to, czego wywołujący nie widzi. Kursor jest szyfrowany (AES-256-GCM),
   związany z użytkownikiem, narzędziem i odciskiem argumentów (`CursorCodec.fingerprint`), więc kursor z innego zapytania to
   `invalid_argument`; pozycja w kursorze nie zawiera poświadczeń. Argument `cursor` ma `@ArgumentLimits(maxLength = CursorCodec.MAX_CURSOR_LENGTH, pattern = CursorCodec.ARGUMENT_PATTERN)`.
   Wyszukiwanie z filtrem po `@ResourceRef` stronicuje tak samo, z `PageFiller.fill(...)` i `policy.retainAllowedById(...)` jako filtrem
   (`JiraTool.searchIssues`); `search_records` w szablonie zostaje bez kursora, bo jego testy opisują sam filtr zakresu.
6. **Id podrzędne nigdy nie są zasobami same z siebie.** Polityka ocenia rodzica (`@ResourceRef` na `recordId`), a dziecko jest podawane
   tylko pod nim: zapytaj system o dziecko pod rodzicem, sprawdź w odpowiedzi `id` dziecka i identyfikator rodzica, a niezgodność
   potraktuj jak brak (jeden błąd `not_found_or_denied`). Bez tego wywołujący z prawem do rekordu A czyta dowolny komentarz,
   podając A i zgadnięte id. Wzorzec argumentu dziecka jest zamknięty (`COMMENT_ID_PATTERN`), więc nic poza cyframi nie trafia do
   ścieżki zapytania.
7. **Profile.** Trzy profile (Confluence, GitLab, Service Desk) mają te same trzy narzędzia; w profilu OpenSearch (system tylko do
   odczytu) zostają sonda i wyszukiwanie po nazwach indeksów, a testy narzędzi zapisu i komentarzy są pomijane przez
   `SampleScope.FULL_TOOL_SET = false`. Zapis w testach wymaga grantu zakresu z paczki przykładowej (`SampleScope.WRITE_GRANTS`:
   grupa projektu, przestrzeni albo grupy GitLaba, w Service Desku kolejka z `EXTRA_GROUPS`).

## Co serwer musi uzupełnić

1. Domena: zamienić `<System>Record` (`id`, `project`, `projectClassification`, `title`, `status`) na właściwe
   obiekty systemu (np. `JiraIssue`); tylko pola, które narzędzia oddają i których potrzebuje polityka.
2. Adapter `<System>ResourceAdapter` (poświadczenie, timeouty i błędy są w `<System>BackendClient`): prawdziwe ścieżki API systemu zamiast `/api/records/...`, DTO odpowiedzi,
   sposób podania poświadczenia (`credential::applyTo` dla `Authorization`; GitLab z `PRIVATE-TOKEN` przez
   `credential.value()`), mapowanie 404/403 -> pusty wynik, 401 -> `CredentialRequiredException`. Timeouty są w
   `<system>.backend.*`.
3. Resolver i opis zasobu: `<System>ResourceDescription` ma podawać atrybuty **z systemu** (kategoria projektu Jiry,
   etykiety przestrzeni, topics grup GitLaba wszystkich przodków, tagi indeksu). Wzór dla Jiry i GitLaba:
   `docs/jak-napisac-narzedzie.md`. Zasób bez wymaganego atrybutu jest odrzucany, więc nie wypełniaj brakujących
   pustymi wartościami.
4. Narzędzia z tabeli ws-04 w `<System>Tool` (albo kilku klasach; pełne serwery dzielą tak, że `JiraTool` ma narzędzia, a `JiraToolViews`
   widoki wyników), każde z `@ToolRisk`, `@ToolTitle`, limitami
   argumentów i `@ResourceRef` na każdym identyfikatorze zasobu, dopisane w `McpToolsConfiguration`;
   `get_record`, `search_records`, `list_comments`, `get_comment` i `update_record` usunąć albo przerobić (wzorce w sekcji wyżej zostają). Wyszukiwanie przez `QueryScope`, wynik przez
   `@ResourceRef` w rekordzie albo `ResultFilter`. Narzędzie `DESTRUCTIVE` (Jira `delete_issue`) z akcją `delete` i
   uprawnieniem `mcp-<system>-destructive`.
5. Testy: `StubBackend` odpowiada jak API systemu (albo test woła mock z `mcp-platform-dev`); dla każdego narzędzia
   z zasobem: zasób `restricted` daje `not_found_or_denied` identyczne jak nieistniejący, wyszukiwanie w projekcie nie zwraca zasobów
   spoza niego, brak grupy daje `access_denied`, a `ManifestTest` pilnuje zgodności
   manifestu z `tools/list`. Do tego pozytywny, `credential_required` (brak poświadczenia i 401 systemu),
   `invalid_argument`. `GatewayOnlyAccessTest` zostaje (po zmianie nazwy narzędzia w `GET_RECORD`).
6. Konfiguracja wdrożenia: `MCP_POLICY_URL`, `MCP_POLICY_KEY_ID`, `MCP_POLICY_PUBLIC_KEY_FILE`, `MCP_POLICY_STATE_DIR`,
   `MCP_INTERNAL_URL` (szablon: `deploy/systemd/mcp-<system>.env.template`). Wersja w manifeście to wersja projektu.
   Rejestracja manifestu w gateway'u: plik `mcp-app/target/mcp-manifest.json` z fazy `package` (README, "Manifest do rejestracji"); nową klasę narzędzi dopisujesz w `McpToolsConfiguration` i w `ManifestExport.manifest()`.
7. Dokumentacja: tabela narzędzi w README (tytuł, ryzyko, akcja, uprawnienie C9 `mcp-<system>-read|write|destructive`,
   typ poświadczenia) i `docs/api/tools-catalog.md`; własny `STATUS.md` z linią `M3`.
8. Wdrożenie w `mcp-platform-dev`: obraz `docker build -t mcp-<system>:dev .` (Dockerfile nie może być oparty o
   Alpine: `cedar-java` jest zbudowany pod glibc) dla usługi `mcp-<system>` w profilu `full`. Compose ustawia dziś
   `MCP_JIRA_URL`, `MCP_GATEWAY_JWKS_URI`, `MCP_REDIS_*`, `MCP_TLS_*` i `MCP_CONNECT_URL`; dochodzą zmienne polityk
   z punktu 6. Mock Jiry jest po `http`, więc usługa potrzebuje też `MCP_JIRA_ALLOW_PLAIN_HTTP=true`. Profilu `dev` w
   kontenerze nie używać.
