# Katalog narzędzi

Narzędzia to powierzchnia publiczna serwera. Każda zmiana nazwy, opisu, schematu albo ryzyka zmienia
`definitionHash` (C6) i wymaga ponownego zatwierdzenia w gateway'u, więc przechodzi przez code review razem z
tą tabelą.

| Narzędzie (klient widzi `example__<nazwa>`) | Tytuł | Ryzyko | Akcja (C6) | Argumenty | Uprawnienie (C9) | Poświadczenie |
| --- | --- | --- | --- | --- | --- | --- |
| `get_record` | Pobierz rekord | `READ_ONLY` | `read` | `recordId` (`@ResourceRef`, wzorzec z kontraktów) | `mcp-example-read` | z C3, `Authorization` |
| `search_records` | Szukaj rekordów | `READ_ONLY` | `read` | `project` (wzorzec z kontraktów), `text` (`maxLength` 200) | `mcp-example-read` | z C3, `Authorization` |

Konwencje: każde narzędzie ma polski tytuł (`@ToolTitle`, do 100 znaków); nazwy `snake_case`, czasownik i obiekt, bez prefiksu systemu (dokłada go gateway); opis mówi, co
narzędzie zwraca, bez instrukcji dla modelu.
