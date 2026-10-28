# Bezpieczeństwo serwera

Niezmienniki platformy (`mcp-platform-plan/00-overview.md`) i to, gdzie serwer je spełnia.

| Niezmiennik | W serwerze |
| --- | --- |
| Ruch tylko od gateway'a: mTLS + wewnętrzny JWT, inaczej 401 | `server.ssl.client-auth: need` z CA wewnętrznym; `InternalJwtFilter` startera (CN `mcp-gateway`, ES256 z JWKS gateway'a, `iss`, `aud`, czas życia <= 60 s, `jti` jednorazowy w Redisie, `tool` == `params.name`, `rid` == `X-Request-Id`). Bez JWT tylko `/actuator/health*`. Test: `GatewayOnlyAccessTest` |
| Token IdP nigdy do systemu | serwer go nie dostaje; do systemu idzie wyłącznie poświadczenie z C3 (`CredentialContext.require`) |
| Fail-closed | brak JWKS, Redisa, konfiguracji albo poświadczenia -> 401/503/`credential_required`/brak startu, nigdy przepuszczenie; starter odrzuca relaksacje `dev` w innych profilach |
| Sekrety nigdy w logach, audycie, wynikach i błędach | nagłówki poświadczeń ukryte po odczycie, `BackendCredential.toString()` bez wartości, redakcja komunikatów w starterze; adapter zamienia błędy i nieczytelne odpowiedzi systemu na wyjątki bez treści; backend tylko po `https`. Testy: `theCredentialNeverAppearsInTheResultOrTheLog`, `anUnreadableBodyStaysOutOfTheResultAndTheLog`, `outsideDevTheBackendMustBeHttps` |
| "Nie istnieje" i "brak dostępu" -> `not_found_or_denied` | 404 i 403 systemu, projekt `restricted`, rekord przeniesiony do takiego projektu i klasyfikacja, której system nie podał, dają ten sam błąd i tekst. Testy: `missingAndForbiddenRecordsLookTheSame`, `ResourcePolicyTest` |
| Dane `restricted` nigdy do agentów, link wirtualnego serwera tylko zawęża | starter ocenia Cedar (`read`) na zasobie opisanym przez `ExampleResourceDescription` z odpowiedzi systemu, nie z argumentu; wyszukiwanie zawęża `QueryScope`, a hity filtruje po `@ResourceRef`. Testy: `SearchRecordsToolTest` |
| Paczka polityk podpisana, rewizje tylko w górę | klucz publiczny z konfiguracji, `policy.state-dir` poza `dev`, klucze `test-*` tylko w `dev`; bez pierwszej paczki serwer nie wstaje. Test: `ProductionConfigurationTest` |
| Audyt z `requestId` | jedno zdarzenie C8 `tool_call` na wywołanie (logger `pl.aiwcorpo.mcp.audit`, stdout, czysta linia JSON), `requestId` z JWT |

## Zasady dla kodu

- Każdy argument narzędzia to dane od modelu: wzorzec, długość, zamknięte listy, przed wywołaniem systemu.
  Szczegóły: `docs/jak-napisac-narzedzie.md`.
- Adresy: tylko `MCP_<SYSTEM>_URL` z konfiguracji (poza `dev` wyłącznie `https`; `http` tylko jawnym
  `MCP_<SYSTEM>_ALLOW_PLAIN_HTTP` dla mocków środowiska dev), ścieżki API z szablonu URI z argumentami jako
  zmiennymi, bez przekierowań; żadnych adresów z argumentów (SSRF). Kodowanie szablonu nie usuwa segmentów `.`
  i `..`: odrzuca je walidacja argumentu.
- Odpowiedź systemu: najwyżej `max-response-size` (1 MB), tylko pola domeny, nigdy w komunikacie ani logu
  (także odpowiedź 2xx, której nie da się sparsować).
- Profil `dev` (klucze testowe, bez mTLS) jest w jarze, ale wstaje tylko na adresie pętli zwrotnej (starter
  3.0.0-alpha-5, test `DevProfileLoopbackTest`). Serwer, do którego łączy się gateway, działa wyłącznie w
  konfiguracji bazowej.
- Serwer nie ma konta w systemie. Typ `service-account` (np. service desk, D10) przychodzi też przez C3, a
  wtedy serwer jest główną granicą autoryzacji: zapytanie ogranicza do `QueryScope.grantedIds()` (puste
  `grantsNothing()` = bez zapytania), a wynik filtruje polityka.
- Hinty MCP (`readOnlyHint`, `destructiveHint`) są informacją dla klienta, nie zabezpieczeniem.

## Wdrożenie

Konto bez uprawnień, sandbox systemd (`deploy/systemd/mcp-example.service`), sekrety tylko w pliku
środowiska (0640, root:mcp) albo z magazynu sekretów, certyfikat serwera z SAN `mcp-example.internal` z CA
wewnętrznego. Redis: użytkownik ACL `mcp-example` z kluczami `jti:mcp-example:*` i komendą `SET`.
