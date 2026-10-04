# mcp-server-seed — szablon serwera MCP (Spring Boot)

> Punkt startowy dla serwera **MCP (Model Context Protocol)** w Javie/Spring Boot. Architektura
> heksagonalna (multi-module), transport streamable-HTTP po HTTP za nginx (TLS na gateway).
> Cross-cutting (tool guard, przekazywanie PAT, nagłówki bezpieczeństwa, obserwowalność, mapowanie
> błędów) oraz transport MCP dostarcza starter `mcp-platform-starter` — serwer skupia się na domenie
> i narzędziach.
>
> Kod jest **w pełni zaimplementowany** (Java 25, Spring Boot 4.1.0, Spring AI 2.0.0): trzy
> przykładowe narzędzia, po jednym na poziom ryzyka, działające na statycznym mocku backendu.

## Szybki start

```bash
./mvnw test          # zbuduj i uruchom testy (kontekst się ładuje, narzędzie się rejestruje)
./mvnw -pl mcp-app -am package   # zbuduj wykonywalny jar: mcp-app/target/mcp-server.jar
```

Uruchomienie lokalne (`MCP_PROFILE=local`) działa po zwykłym HTTP (bez TLS w aplikacji; w realnych
wdrożeniach TLS terminuje nginx gateway) i nie wymaga backendu: profil `local` używa statycznego
mocka w pamięci (`example.backend.mode: mock`).

## Demo: flagi, uprawnienia, potwierdzenie

```bash
java -jar mcp-app/target/mcp-server.jar        # endpoint: http://127.0.0.1:8080/mcp/example
```

Trzy narzędzia, po jednym na poziom ryzyka. Uprawnienia wynikają z ról w tokenie dostępu,
zmapowanych w `mcp-app/src/main/resources/guard-policy-local.yml`:

| Narzędzie | Flaga | bez tokenu | token bez roli | rola `mcp-operator` | rola `mcp-admin` |
|-----------|-------|-----------|----------------|---------------------|------------------|
| `example_lookup` | read-only | tak | tak | tak | tak |
| `example_rename` | update | odmowa | odmowa | tak | tak |
| `example_purge` | destructive | odmowa | odmowa | odmowa | tak, po podaniu kodu |
| `platform_whoami` | read-only | tak | tak | tak | tak |

Tryb uwierzytelniania wybierasz zmienną `MCP_AUTH_MODE`:

- `oauth` (domyślny): token dostępu z dostawcy OIDC; role z tokenu decydują o uprawnieniach,
  a ten sam token idzie do backendu.
- `pat`: użytkownik wysyła własny token backendu w `X-PAT-Example`. Bez dostawcy tożsamości;
  każdy z PAT-em dostaje poziom `pat` z polityki (lokalnie: update), więc `example_purge` jest
  w tym trybie niedostępne. Serwer nie weryfikuje PAT-a, robi to backend.

W trybie `oauth` tożsamość pochodzi od dostawcy OIDC (Keycloak, Entra ID, Okta, mock). Serwer waliduje token JWT
z nagłówka `Authorization: Bearer`; logowania sam nie prowadzi. Wskaż dostawcę zmiennymi:

```bash
export MCP_OIDC_ISSUER=http://localhost:8081/realms/demo   # issuer dostawcy
export MCP_OIDC_AUDIENCE=mcp-example                       # identyfikator tego serwera u dostawcy
```

Bez `MCP_OIDC_ISSUER` serwer nie ma dostawcy tożsamości: każdy jest anonimowy (read-only),
a tokeny Bearer są odrzucane. Dostawca musi wydawać tokeny z rolami `mcp-operator` / `mcp-admin`
w jednym z claimów: `roles`, `groups`, `realm_access.roles`, `permissions`, `scope`, `scp`.

Przebieg destrukcyjny (`example_purge` z rolą `mcp-admin`):
1. Serwer generuje jednorazowy kod `XXX-XXX` i dostarcza go poza modelem. W demo kod pojawia się
   w logu serwera: `event=confirmation_code_issued ... code=123-456`.
2. Klient z MCP elicitation pokazuje pole na kod i wywołanie czeka. Inny klient dostaje
   `confirmation_required`; agent prosi użytkownika o kod i ponawia wywołanie z `confirmation_code`.
3. Kod działa raz, tylko dla tych samych argumentów, przez 120 s.

Zmiana polityki na żywo (bez restartu):

```bash
export MCP_GUARD_POLICY=file:$PWD/mcp-app/src/main/resources/guard-policy-local.yml
java -jar mcp-app/target/mcp-server.jar
# w trakcie działania zmień w pliku np.  tools: { example_rename: { enabled: false } }
# następne wywołanie example_rename wraca jako tool_disabled
```

Każda decyzja trafia do logu audytowego: `subject`, `risk`, `outcome` (`ok` / `denied` /
`confirmation_pending` / `error`) i `reason`.

## Struktura (moduły)

| Moduł | Odpowiedzialność |
|-------|------------------|
| `mcp-core` | Domena + porty (in/out). Czysta Java, bez Spring. |
| `mcp-application` | Use-case'y (POJO); orkiestracja portów. Zależy tylko od `mcp-core`. |
| `mcp-integration` | Adaptery wyjściowe do systemów backendowych (resilience, TLS out). |
| `mcp-tools` | Adapter wejściowy: definicje `@Tool` (powierzchnia publiczna). |
| `mcp-app` | Bootstrap + wiring + jedyny wykonywalny artefakt (jar). |

Tool guard (tożsamość, uprawnienia, potwierdzanie), przekazywanie PAT-ów (`X-PAT-*` →
`CredentialContext` → backend), nagłówki bezpieczeństwa (HSTS), audyt, korelacja, mapowanie błędów
i transport MCP pochodzą z `mcp-platform-starter` (auto-konfiguracja) — nie piszesz ich w tym repo.

## Wymagania

- **JDK 25**.
- Artefakt `pl.aiwcorpo.mcp:mcp-platform-starter` (+ `mcp-platform-bom`) dostępny w Twoim repozytorium
  Maven. Wersja Spring Boot w `<parent>` musi być z nim zgodna.

## Tożsamość i publikacja (per serwer)

Każdy serwer musi mieć **unikalny `groupId`**, żeby moduły (`mcp-core`, `mcp-app`, …) różnych serwerów
nie kolidowały w repozytorium artefaktów. Seed używa placeholdera `pl.aiwcorpo.mcp.example`; zmień go
jednym poleceniem na swoją domenę:

```bash
find . -name pom.xml -exec sed -i '' 's/pl.aiwcorpo.mcp.example/pl.aiwcorpo.mcp.jira/g' {} +
```

Zależności wewnątrz-reaktorowe używają `${project.groupId}`, a zależność do platformy pozostaje
`pl.aiwcorpo.mcp` — dlatego to polecenie nie rusza platformy. (Pakiet Javy pozostaje `pl.aiwcorpo.mcp.*`;
zmieniasz tylko współrzędne Maven.)

Serwer to **aplikacja**, nie biblioteka współdzielona: wdrażaj tylko fat-jar `mcp-app`
(`finalName` → np. `mcp-jira`). Nie publikuj (`mvn deploy`) modułów serwera do repo Maven —
publikowana jest jedynie biblioteka platformowa (i ewentualne celowo współdzielone artefakty).

## Implementacja narzędzia

Dla każdego narzędzia dotykasz czterech modułów, od środka na zewnątrz:

1. `mcp-core` — port wejściowy (use-case) i wyjściowy (np. `JiraPort`).
2. `mcp-application` — implementacja use-case'u (reguły, orkiestracja).
3. `mcp-integration` — implementacja portu wyjściowego (klient REST/DB: timeouty, retry, sekrety z env).
4. `mcp-tools` — cienki `@Tool`: walidacja wejścia + wywołanie use-case'u; rejestracja w
   `mcp-app/.../config/McpToolsConfiguration.java` (jawna allow-lista narzędzi).
5. **Flaga ryzyka** — `@ToolRisk(RiskLevel.READ_ONLY | UPDATE | DESTRUCTIVE)` na metodzie `@Tool`.
   Serwer nie wstanie z narzędziem bez flagi ani z nazwą sprzeczną z flagą (np. `*_delete`
   oznaczone inaczej niż `DESTRUCTIVE`). Nie dopisuj własnego parametru „czy na pewno” —
   potwierdzenie robi platforma i model nie może na nie odpowiedzieć sam.

Wzorcem są `example_lookup`, `example_rename` i `example_purge`
(`mcp-tools` → `mcp-application` → `mcp-integration`).

## Dokumentacja

Architektura, bezpieczeństwo, TLS (na nginx gateway) i wdrożenie: katalog `docs/`.
