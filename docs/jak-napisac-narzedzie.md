# Jak napisać narzędzie

Narzędzie to metoda `@Tool` wołana przez model przez gateway. Każdy argument jest niezaufany, a wynik trafia
do modelu, który może go potraktować jak instrukcję. Wzorcem jest `get_record`.

## Kroki, od środka

1. `mcp-core`: rekord domeny tylko z polami, które narzędzie ma oddać i które potrzebuje polityka (kontener rekordu i
   jego klasyfikacja); metoda portu wejściowego (`ExampleUseCase`) i wyjściowego (`ExampleResourcePort`);
   `ExampleResourceModel` z typami zasobów (`Jira::Issue`, `Jira::Project`, ...) i wzorcami identyfikatorów z kontraktów.
2. `mcp-application`: implementacja przypadku użycia w `ExampleService` (bez Springa, test JUnit obok).
3. `mcp-integration`: wywołanie systemu w adapterze. Poświadczenie zawsze z
   `CredentialContext.require(system)` i `credential::applyTo`; dla nagłówka innego niż `Authorization`
   (np. `PRIVATE-TOKEN` GitLaba) `credential.value()`. 404 i 403 systemu -> pusty wynik (jeden kod
   `not_found_or_denied`), 401 -> `CredentialRequiredException`, 3xx i odpowiedź, której nie da się
   użyć (nie parsuje się, brak `id`, za duża) -> `BackendResponseException` ze stałym tekstem i bez przyczyny
   (komunikat parsera cytuje treść odpowiedzi). Odpowiedź do prywatnego DTO z
   `@JsonIgnoreProperties(ignoreUnknown = true)`, potem do domeny; brak wymaganych pól to błąd systemu, nie
   `IllegalArgumentException` (to byłby `invalid_argument`, czyli wina modelu). Obok adaptera resolver zasobu
   (niżej).
4. `mcp-tools`: cienka metoda `@Tool` z `@ToolRisk`, `@ToolTitle` (polski tytuł, do 100 znaków, część
   `definitionHash`) i limitami argumentów przez `pl.aiwcorpo.mcp.platform.input.ArgumentLimits`: `maxLength` i
   `contractPattern` (wzorzec z `common.schema.json`, np. `JiraIssueKey`) albo własny `pattern` zakotwiczony
   `^...$` na tekście, `minimum` i `maximum` na liczbie, `maxItems` na liście, rekord z limitami na komponentach i
   `@Nullable` na opcjonalnych. Schemat musi być zamknięty i ograniczony (to reguły manifestu): bez `Map` i
   `Object`, bez `$ref` i typów łączonych, bez argumentu `confirmation_code`; start się zatrzymuje, gdy
   którejś brakuje. Starter egzekwuje te same limity w kodzie przed metodą. Wynik to rekord
   (`ExampleRecordView`): tekst z systemu jako `UntrustedText`, pole, które system może zostawić puste, jako
   `@Nullable`, bez adnotacji Jacksona na komponentach.
5. `mcp-app`: dopisanie beana narzędzia w `McpToolsConfiguration` przez `SchemaLimitedToolCallbacks.of(...)` ze startera
   (jawna lista; sam `@Tool` nie wystarczy, a `MethodToolCallbackProvider` pominąłby `@ArgumentLimits`),
   test w `mcp-app/src/test` przez `ServerTestSupport`, odpowiedź systemu w `StubBackend`.
6. `docs/api/tools-catalog.md` i tabela narzędzi w README (tytuł, ryzyko, akcja, uprawnienie).

## Zasób: `@ResourceRef` i resolver

Argument, który nazywa zasób (zgłoszenie, strona, projekt, indeks), dostaje `@ResourceRef(type = "...")`. Przed
metodą starter rozwiązuje każdą jego wartość resolverem i ocenia politykę Cedar (akcja `read`, `update`, `delete`
albo `admin` z `@ToolRisk`) na zasobie takim, jakim opisuje go **system**. Odmowa, brak zasobu i zasób, którego nie
da się opisać, kończą wywołanie tym samym `not_found_or_denied`.

Resolver (`ExampleRecordResolver`) ma trzy metody:

- `fetch(id)`: woła system poświadczeniem wywołującego. Pusty `Optional` dla 404 i 403, **wyjątek** dla awarii
  systemu (klient dostaje `upstream_unavailable`, nie odmowę).
- `describe(obiekt)`: typ, kanoniczny id, rodzic i atrybuty polityki (`ExampleResourceDescription`). Id i rodzic z
  odpowiedzi systemu, nigdy z argumentu: zgłoszenie `ABC-123` przeniesione do `HR` ma projekt `HR`, a jego klasyfikacja
  `restricted` zamyka dostęp mimo argumentu, który wygląda na `ABC`.
- Atrybuty wymagane przez schemat polityki: `Jira::Project.category` (`""` gdy brak), `Confluence::Space.labels`,
  `OpenSearch::Index.tags`; opcjonalne `classification` dla kolejki service desku i dla GitLaba. Atrybutu, którego
  system nie podał, **nie wypełniaj domyślną wartością**: pominięty atrybut sprawia, że zasób jest odrzucany.

Metoda narzędzia czyta rekord, który pobrał resolver (`ResourceContext.current().orElseThrow().fetched(typ, id,
Klasa.class)`), bez drugiego pytania systemu. Wszystkie wartości argumentu będącego listą są oceniane, a pierwsza
odrzucona kończy wywołanie. Argument, który może być nieobecny, nie jest `@ResourceRef`: pusty `resourceRefs`
kończy się odmową.

**GitLab: klasyfikacja przodków (CC-129).** Topics w GitLabie się nie dziedziczą, a `restricted` na grupie ma chronić
całe poddrzewo, więc resolver podaje w `attrs.ancestorClassifications` mapę `<ścieżka grupy-przodka> -> topics`
dokładnie dla grup nad zasobem. Dla projektu `hr/payroll/app` są to klucze `hr` i `hr/payroll` (grupa-rodzic też), dla
jego rodzica `hr/payroll` tylko `hr`, a dla grupy najwyższego poziomu jawne `{}`:

```java
// topics grupy hr = [Restricted], grupy hr/payroll = []
Map<String, List<String>> ancestors = Map.of("hr", List.of("Restricted"), "hr/payroll", List.of());
return ResolvedResource.of("GitLab::Project", "hr/payroll/app",
                Map.of("path", "hr/payroll/app", "classification", ownTopics, "ancestorClassifications", ancestors))
        .withParent(ResolvedResource.of("GitLab::Group", "hr/payroll",
                Map.of("path", "hr/payroll", "classification", groupTopics,
                        "ancestorClassifications", Map.of("hr", List.of("Restricted")))));   // bez samej grupy
```

Brak mapy, brakujący albo nadmiarowy klucz (także sama grupa) i topics niebędące listą napisów odrzucają zasób.
Resolver GitLaba pobiera topics każdej grupy na ścieżce, raz na wywołanie. Wzór dla grupy najwyższego poziomu: opis zasobu w serwerze GitLab wygenerowanym z
template'u (`ExampleResourceDescription`); wektory kontraktów: `server-gitlab-*` w `test-vectors/entity-builder`.

## Wyszukiwanie: `QueryScope` i `ResultFilter`

- Model podaje wartości (zakres: projekt, przestrzeń, grupa, prefiks indeksu; tekst; ograniczone filtry), serwer
  buduje zapytanie. Zakres jest argumentem obowiązkowym i nie ma globalnego wyszukiwania.
- `policy.queryScope(typ)` (`ResourcePolicy` wstrzyknięte w konstruktorze) daje zawężenie z linku wirtualnego
  serwera (`linkIds()`) i zakresy z grup (`grantedIds()`, `grantsNothing()`). Gdzie system sam stosuje prawa
  użytkownika (Jira, Confluence, GitLab z jego tokenem), wystarcza link; gdzie nie (OpenSearch, konto serwisowe),
  zapytanie ogranicza się do `grantedIds()`, a **puste `grantsNothing()` oznacza: nie wykonuj zapytania**. Wyszukiwanie
  poza linkiem to pusty wynik, nie błąd: błąd mówiłby, jakie zakresy istnieją. Wzór: `ExampleTool.searchRecords`.
  Wartości od modelu wchodzą do zapytania wyłącznie jako zmienne szablonu URI albo cytowane identyfikatory
  (`QueryScope.quote`), nigdy sklejane. Starter beta-2 dostanie API budowania zapytań z ustrukturyzowanych filtrów;
  miejsce na nie to ten sam fragment `searchRecords`.
- `ResultFilter`: rekord wyniku z komponentem `@ResourceRef` (`ExampleSearchView.Hit.id`) jest sprawdzany po
  wykonaniu narzędzia, a elementy listy, których polityka nie dopuszcza, znikają z wyniku. Usuwa to rekordy powiązane
  w wyszukiwaniu z innych projektów (link zawężony do `ABC` nie pokazuje zgłoszenia z `DEF`), zastrzeżone i
  przeniesione. Liczniki z systemu (`total`) nie są poprawiane po filtrowaniu: nie zwracaj ich. Wynik innego kształtu
  filtruje `policy.retainAllowedById(typ, elementy, Element::id)` albo `retainAllowed(...)`.

## Narzędzie `DESTRUCTIVE`

Nieodwracalna akcja (usunięcie zgłoszenia) to `@ToolRisk(RiskLevel.DESTRUCTIVE)`; w Cedar odpowiada jej akcja `delete`
(albo jawnie `ToolAction.ADMIN`: `@ToolRisk(value = RiskLevel.DESTRUCTIVE, action = ToolAction.ADMIN)`). Akcja sprzeczna z
ryzykiem (`READ_ONLY` z `delete`) zatrzymuje start, a nazwa z `delete`, `drop`, `purge`, `remove`, `wipe` nie przejdzie
jako `READ_ONLY` ani `UPDATE`. Zasób nadal jest `@ResourceRef`: polityka sprawdza `delete` na zasobie. Serwer nie pyta o
potwierdzenie ani nie ma parametru "czy na pewno": kod TOTP weryfikuje gateway (C11), a uprawnienie to grupa
`mcp-<system>-destructive`. Starter publikuje `destructiveHint: true`, a manifest `risk: DESTRUCTIVE`.

```java
@Tool(name = "delete_issue", description = "Deletes an issue.")
@ToolRisk(RiskLevel.DESTRUCTIVE)
@ToolTitle("Usuń zgłoszenie")
public DeleteResult deleteIssue(@ArgumentLimits(maxLength = 16, contractPattern = "JiraIssueKey")
                                @ResourceRef(type = "Jira::Issue") String issueKey) { ... }
```

Test takiego narzędzia ustawia w tokenie grupę `-destructive` i `mfaAgeSeconds(60)` (reguła
`destructive-requires-fresh-mfa` jest w paczkach testowych, nie w produkcyjnej) i sprawdza w `ManifestTest`, że `risk`
i `destructiveHint` się zgadzają.

## Zasady

- Zasób zawsze jawnym argumentem. Narzędzie dostaje identyfikator projektu, rekordu, przestrzeni,
  indeksu i działa tylko na nim. Nie ma narzędzi "moje ostatnie", "wszystko z ..." ani domyślnego zasobu z
  konfiguracji: gateway i polityka (C7, `refs`) widzą tylko to, co jest w argumentach.
- Bez globalnego wyszukiwania. Wyszukiwanie zawsze w zakresie wskazanym argumentem (projekt, przestrzeń,
  grupa, prefiks indeksu), zawężonym dalej przez `QueryScope` startera.
- Bez dowolnego języka zapytań. Nie przekazuj JQL, CQL, query DSL ani ścieżek API od modelu. Jeśli
  narzędzie potrzebuje filtrów, to są osobne, ograniczone pola (status, typ, przypisany) parsowane i
  składane przez serwer z escapowaniem.
- Walidacja przed systemem. Wzorzec i długość identyfikatora (`contractPattern`), limity wielkości,
  zamknięte listy wartości. Błąd walidacji to `InvalidToolArgumentException` ze stałym tekstem opisującym
  format (starter oddaje go jako `invalid_argument`): nigdy z wartością argumentu (C10: komunikat nie zawiera
  wartości argumentów) i nigdy z treścią odpowiedzi systemu. Starter sprawdza wzorzec identyfikatora zasobu przed
  resolverem; własna walidacja w kodzie zostaje tam, gdzie reguły nie da się wyrazić w schemacie (tekst niepusty po
  przycięciu).
- Segmenty ścieżki. Szablon URI `RestClient` koduje `/`, `?`, `#`, ale wartości `.` i `..` zostają segmentami
  ścieżki (`/api/records/..`). Wzorzec identyfikatora musi je wykluczać (wzorce z kontraktów to robią);
  argumenty będące ścieżką (GitLab `get_file`) wymagają osobnej normalizacji i odrzucenia `..`.
- Błędy tylko przez kody C10. Wyjątek domeny -> `ToolErrorMapping` (bean w `mcp-tools`) -> `SafeError` z kodem z
  `ToolErrorCode`; "nie ma albo nie wolno" załatwia resolver, więc narzędzia zasobowe zwykle nie potrzebują własnego mapowania. Gotowy `SanitizedToolException` wolno rzucić, gdy narzędzie samo zna kod. Inne wyjątki
  starter zamienia na `internal_error`; komunikat nie zawiera odpowiedzi systemu, nagłówków ani sekretów.
- "Nie istnieje" i "brak dostępu" to ten sam błąd `not_found_or_denied`, z tym samym tekstem.
- Ryzyko zgodne z działaniem. `READ_ONLY`, `UPDATE` (odwracalne), `DESTRUCTIVE` (nieodwracalne). Starter
  nie uruchomi serwera z narzędziem bez flagi ani z nazwą sprzeczną z flagą (`delete_*` jako `READ_ONLY`).
  Nie dodawaj parametru "czy na pewno": potwierdzenie robi gateway (C11).
- Opis deterministyczny. Co narzędzie robi i co zwraca, bez instrukcji dla modelu ("zawsze", "najpierw
  wywołaj"). Zmiana nazwy, opisu, schematu albo ryzyka zmienia `definitionHash` i wymaga ponownego
  zatwierdzenia w gateway'u (C6).
- Wynik minimalny. Tylko pola potrzebne do zadania, bez surowego HTML; tekst z systemu to dane, nie
  polecenia.
- Bez sekretów. Serwer nie ma własnego konta w systemie; nie loguj i nie zwracaj poświadczenia (starter
  usuwa je z komunikatów, ale nie licz na to w kodzie).
