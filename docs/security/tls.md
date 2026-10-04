# TLS na nginx gateway (poza aplikacją)

Aplikacja **nie terminuje TLS**. TLS jest terminowany na **nginx reverse-proxy gateway**, a serwer
MCP nasłuchuje na **zwykłym HTTP** na `${MCP_PORT:8080}` w sieci wewnętrznej za gateway.

## Konfiguracja aplikacji

Patrz `mcp-app/src/main/resources/application.yml`:

```yaml
server:
  port: ${MCP_PORT:8080}
  forward-headers-strategy: framework   # ufaj X-Forwarded-* od nginx
```

`forward-headers-strategy: framework` sprawia, że Spring odtwarza oryginalny schemat/host/port
z nagłówków `X-Forwarded-*` ustawianych przez nginx (poprawne linki, redirecty, `https` w logach).

## Co dzieje się na nginx

- Terminacja TLS: certyfikat serwera + łańcuch CA są na gateway (nie w aplikacji).
- Wersje i szyfry: preferuj **TLS 1.3** (1.2 tylko jeśli klienci wymagają); pinuj zatwierdzoną listę
  cipher-suite — konfiguracja po stronie nginx.
- Certyfikat: z **wewnętrznego CA** organizacji (lub publicznego CA, gdy endpoint jest wystawiony na
  zewnątrz). Wydawanie i pakowanie leży po stronie gateway.
- Rotacja/odnowa (np. ACME/Let's Encrypt) oraz alertowanie o wygaśnięciu — po stronie nginx.
- Nagłówki bezpieczeństwa (HSTS, `X-Frame-Options`/CSP `frame-ancestors`) — na gateway.
- nginx musi ustawiać `X-Forwarded-Proto`, `X-Forwarded-Host`, `X-Forwarded-For`.

## TLS wychodzący (app → backend) — bez zmian

Ruch **wychodzący** do backendów nadal idzie po HTTPS (`example.backend.base-url`), z wewnętrznym CA
w truststore adaptera wyjściowego. Ta zmiana dotyczy wyłącznie kierunku **wejściowego**.
Nie ustawiaj `ssl-verify=false` po stronie integracji wyjściowych w prod — zaimportuj wewnętrzny CA
do truststore.
