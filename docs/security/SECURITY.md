# Bezpieczeństwo — polityka i praktyki (MCP server)

Dokument zbiera praktyki bezpieczeństwa dla serwera MCP na poziomie organizacji. Oparty na
MCP Security Best Practices (modelcontextprotocol.io) oraz OWASP MCP Security Cheat Sheet.

## 0. Tool guard (kto, co wolno, potwierdzenie)
Przed każdym narzędziem platforma (`mcp-platform-starter` ≥ 2.0.0) odpowiada deterministycznie,
bez udziału modelu, na trzy pytania:
1. **Czy narzędzie jest włączone?** Wyłącznik per narzędzie w pliku polityki, działa od następnego
   wywołania.
2. **Czy wywołujący ma uprawnienie?** Tożsamość z tokenu dostępu JWT dostawcy OIDC
   (`Authorization: Bearer`; walidacja podpisu, `iss`, `exp`, `aud`). Role z tokenu są mapowane
   w pliku polityki na `max-risk` i porównywane z flagą `@ToolRisk` narzędzia. Bez tokenu:
   anonimowy, read-only. Nieprawidłowy token: HTTP 401.
3. **Czy człowiek potwierdził akcję destrukcyjną?** Jednorazowy kod, związany z wywołującym,
   narzędziem i dokładnymi argumentami, dostarczany kanałem poza modelem.

Zasady:
- Plik polityki nadaje uprawnienia rolom — w produkcji poza jarem, własność root, tylko do odczytu
  dla konta usługi (`MCP_GUARD_POLICY`). Nie zawiera użytkowników ani sekretów.
- Zawsze ustaw `MCP_OIDC_AUDIENCE`: bez sprawdzania `aud` serwer przyjąłby token wydany przez tego
  samego dostawcę dla dowolnej innej aplikacji.
- Serwer nie prowadzi logowania; wskazuje dostawcę w `/.well-known/oauth-protected-resource`.
  Tokeny powinny mieć krótki czas życia — unieważnienie przed `exp` nie jest sprawdzane.
- **Kod potwierdzenia w logu to zaślepka do demo.** Produkcyjnie: własny `ConfirmationCodeSender`.
- Hinty MCP (`readOnlyHint`, `destructiveHint`) informują klienta; bezpieczeństwo na nich nie polega.
- Guard ogranicza, co wywołujący może zrobić **na tym serwerze**. Uprawnienia w backendzie dalej
  egzekwuje backend na podstawie PAT użytkownika — to dwie niezależne warstwy.

## 1. Poświadczenia do backendów (zależnie od `MCP_AUTH_MODE`)
- Serwer nie ma własnego konta w backendach — przekazuje poświadczenie wywołującego: token dostępu
  (tryb `oauth`) albo **PAT dostarczany per żądanie przez samego użytkownika** w nagłówku
  `X-PAT-<system>` (tryb `pat`).
- Tryb `pat` nie daje serwerowi tożsamości: PAT weryfikuje dopiero backend, a każdy posiadacz
  dostaje jeden poziom `pat` z polityki. Gdy serwer sam ma wiedzieć, kto woła, używaj `oauth`.
- Każdy użytkownik działa **jako on sam** w backendzie (jego PAT), więc backend egzekwuje uprawnienia
  per-user. Brak współdzielonego konta serwisowego → brak confused-deputy przez wspólny token.
- **PAT-y przechodzą przez serwer** (nagłówki → `CredentialContext`, request-scoped): nigdy ich nie
  loguj/audytuj, wyłącznie po TLS, czyść po żądaniu. Serwer jest punktem tranzytu sekretów — traktuj
  jego kompromitację jak wyciek PAT-ów.
- **Ograniczenia (świadomy dług):** w trybie `oauth` do backendu idzie ten sam token, który dostał
  serwer (backend musi akceptować jego `aud`). Docelowo: token exchange / broker sekretów.
- Minimalizuj zakres i TTL PAT (użytkownik nadaje możliwie wąski), jeśli backend na to pozwala.

## 2. Transport (TLS na nginx gateway)
- TLS terminowany na **nginx reverse-proxy gateway**; aplikacja nasłuchuje po **zwykłym HTTP**
  na porcie `${MCP_PORT:8080}` w sieci wewnętrznej za gateway. Preferuj TLS 1.3 na gateway.
- Nagłówki bezpieczeństwa (HSTS, `X-Frame-Options: DENY`/CSP `frame-ancestors`) i certyfikat —
  na nginx. Aplikacja ufa `X-Forwarded-*` (`forward-headers-strategy: framework`).
- Ruch wychodzący (app → backend) pozostaje po HTTPS (wewnętrzny CA w truststore adaptera).
- Szczegóły konfiguracji: `docs/security/tls.md`.

## 3. Walidacja wejścia (traktuj argumenty narzędzi jako niezaufane)
- Ścisła walidacja (JSON Schema `additionalProperties:false`, wzorce, limity długości) przed użyciem.
- Nigdy nie buduj poleceń shell / SQL / ścieżek plików z surowych argumentów (injection, path traversal).
- Zapobiegaj **SSRF**: allow-lista docelowych URL/hostów; blokuj zakresy prywatne i
  `169.254.169.254` (metadata cloud); ostrożnie z przekierowaniami i rebindingiem DNS.

## 4. Projekt narzędzi i „tool poisoning”
- Opisy narzędzi deterministyczne, bez ukrytych instrukcji dla modelu.
- Wersjonuj definicje narzędzi; wykrywaj zmiany po wdrożeniu (rug-pull detection).
- Oczyszczaj wyniki narzędzi z treści wyglądających jak instrukcje, zanim trafią do modelu.
- Każde narzędzie ma flagę `@ToolRisk`; zapisy i akcje destrukcyjne są dostępne tylko dla
  uprawnionych wywołujących (sekcja 0).

## 5. Limity i odporność
- Rate limiting / kwoty / timeouty per użytkownik/tenant (ochrona przed DoS).
- Timeouty, retry z backoff, circuit breaker na integracjach wyjściowych.

## 6. Logowanie i audyt
- Audytowane jest każde wywołanie narzędzia, także odrzucone: kto (`subject`), co, poziom ryzyka,
  correlation id, wynik (`ok` / `denied` / `confirmation_pending` / `error`) z kodem powodu, latencja.
- Redakcja sekretów/PII w logach. Wysyłka do SIEM; alerty na anomalie (nowe narzędzia, nietypowa
  częstotliwość, operacje administracyjne).

## 7. Zarządzanie sekretami
- Zero sekretów w repo i w plikach YAML. Wstrzykiwanie przez env (systemd `EnvironmentFile`) lub vault.
- Poświadczenia per-integracja, o wąskim zakresie; rotacja i data ważności.

## 8. Hardening wdrożenia (VM/systemd)
- Usługa jako konto niesystemowe, non-root; izolacja procesu (patrz unit systemd).
- Dostęp do FS ograniczony do wymaganych katalogów; brak zbędnego dostępu sieciowego.
- Aktualizacje zależności; skan podatności (OWASP Dependency-Check) w CI.

## Zgłaszanie podatności
Opisz proces (kontakt, SLA, poufność) zgodnie z polityką organizacji.
