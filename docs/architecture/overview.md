# Architektura — przegląd

## Cel

Korporacyjny serwer **MCP (Model Context Protocol)** udostępniający wewnętrzne systemy organizacji
asystentom AI jako zestaw kontrolowanych, audytowanych **narzędzi (tools)**. Serwer działa po
**HTTP**; TLS jest terminowany na **nginx gateway**, a aplikacja nasłuchuje po zwykłym HTTP w sieci
wewnętrznej za gateway. Uruchamiany na **VM przez systemd**.

## Styl architektoniczny — heksagonalny (ports & adapters), multi-module

```
        AI client (Claude/IDE)                 systemy backendowe (REST/DB/kolejki)
                 │  HTTP + X-PAT-* (TLS na nginx)            ▲
                 ▼                                          │
        ┌──────────────────┐   inbound      ┌───────────────┴───────────────┐
        │   mcp-tools      │  port (in)     │        mcp-integration        │
        │  (@Tool adapter) │───────────────▶│      (outbound adapters)      │
        └──────────────────┘                └───────────────▲───────────────┘
                 │                                            │ outbound
                 ▼                                            │ port (out)
        ┌──────────────────┐        ┌────────────────────────┴───────────┐
        │  mcp-application │───────▶│              mcp-core               │
        │   (use cases)    │  uses  │   domena + porty (in/out), 0 Spring │
        └──────────────────┘        └─────────────────────────────────────┘
             wspólne (ze startera): mcp-platform-starter (security, tls, observability, error) · bootstrap: mcp-app
```

Zależności skierowane są **do środka**: `mcp-core` nie zna frameworka ani transportu. Dzięki temu
logika biznesowa jest testowalna i audytowalna niezależnie od MCP/HTTP.

## Moduły

| Moduł | Odpowiedzialność | Zależy od |
|-------|------------------|-----------|
| `mcp-core` | Domena, encje, porty (interfejsy in/out). Bez Spring. | — |
| `mcp-application` | Przypadki użycia; orkiestracja portów; transakcje. | core |
| `mcp-tools` | Adapter wejściowy: definicje `@Tool` (powierzchnia publiczna). | application + Spring AI MCP |
| `mcp-integration` | Adaptery wyjściowe do systemów backendowych (resilience, TLS out). | core + application |
| `mcp-platform-starter` | Cross-cutting ze startera platformowego: security, TLS, obserwowalność, błędy. | (zależność zewn.) |
| `mcp-app` | Bootstrap + wiring + jedyny wykonywalny artefakt (jar). | wszystkie |

## Transport

Streamable-HTTP (Spring AI MCP server, WebMVC), jeden endpoint `/mcp/<system>` po HTTP (TLS na nginx
gateway). Tryb uwierzytelniania jest jeden dla serwera (`MCP_AUTH_MODE`): `oauth` — token dostępu
OIDC w `Authorization: Bearer`, albo `pat` — własny token backendu w `X-PAT-<system>`. W obu
serwer forwarduje poświadczenie wywołującego do backendu.

## Kluczowe decyzje

- **Transport streamable-HTTP** — natywnie wspierany przez Spring AI MCP server (WebMVC).
- **TLS terminowany na nginx gateway** — aplikacja serwuje zwykły HTTP i ufa `X-Forwarded-*` (`forward-headers-strategy: framework`); brak SSL w aplikacji.
- **Poświadczenie użytkownika do backendu** — token dostępu (tryb `oauth`) albo PAT (`X-PAT-<system>`, tryb `pat`); bez konta serwisowego; szczegóły w `security/SECURITY.md`.
- **Tool guard w platformie** — każde narzędzie ma flagę ryzyka (`@ToolRisk`); platforma sprawdza tożsamość i uprawnienie wywołującego, a akcje destrukcyjne wykonuje dopiero po potwierdzeniu kodem jednorazowym. Reguły są w pliku polityki przeładowywanym bez restartu. Decyzje są deterministyczne, bez udziału modelu.
