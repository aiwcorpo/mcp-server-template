# Katalog narzędzi MCP

Rejestr narzędzi wystawianych przez serwer. **Powierzchnia publiczna = powierzchnia ataku** —
utrzymuj ten katalog w code review i traktuj jak kontrakt.

## Konwencje
- Nazwy `snake_case`, czasownik + obiekt (np. `example_lookup`, `order_create`).
- Opis deterministyczny, bez ukrytych instrukcji dla modelu.
- Każde narzędzie ma flagę `@ToolRisk`: **read-only**, **update** (zmiana odwracalna) albo
  **destructive** (nieodwracalna; wymaga uprawnienia i potwierdzenia kodem przez użytkownika).
  Flagę egzekwuje platforma; ten katalog ma być z nią zgodny.
- Każde narzędzie: walidacja wejścia i limity; autoryzację egzekwuje backend (PAT użytkownika, `X-PAT-<system>`).

## Rejestr (przykład — uzupełnij)

| Narzędzie | Flaga | Backend (PAT) | Wejście (walidacja) | Opis |
|-----------|-------|---------------|---------------------|------|
| `example_lookup` | read-only | `X-PAT-Example` | `id` (`[A-Za-z0-9-]{1,64}`) | Zwraca rekord po id. |
| `example_rename` | update | `X-PAT-Example` | `id`, `name` (1–120 zn.) | Zmienia nazwę rekordu. |
| `example_purge` | destructive | `X-PAT-Example` | `dataset` (= `records`) | Usuwa wszystkie rekordy. Wymaga kodu potwierdzenia. |
| `platform_whoami` | read-only | — | brak | Wbudowane w platformę: kim jest wywołujący i co mu wolno. |

## Wersjonowanie
Zmiana definicji narzędzia (nazwa/parametry/semantyka) = zmiana w tym katalogu + weryfikacja hash/
wersji przez klientów (rug-pull detection). Wycofania oznaczaj jako deprecated z okresem przejściowym.
