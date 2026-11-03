# Katalog narzędzi

Narzędzia to powierzchnia publiczna serwera. Każda zmiana nazwy, opisu, schematu albo ryzyka zmienia
`definitionHash` (C6) i wymaga ponownego zatwierdzenia w gateway'u, więc przechodzi przez code review razem z
tą tabelą.

| Narzędzie (klient widzi `example__<nazwa>`) | Tytuł | Ryzyko | Akcja (C6) | Argumenty | Uprawnienie (C9) | Poświadczenie |
| --- | --- | --- | --- | --- | --- | --- |
| `get_record` | Pobierz rekord | `READ_ONLY` | `read` | `recordId` (`@ResourceRef`, wzorzec z kontraktów) | `mcp-example-read` | z C3, `Authorization` |
| `search_records` | Szukaj rekordów | `READ_ONLY` | `read` | `project` (wzorzec z kontraktów), `text` (`maxLength` 200) | `mcp-example-read` | z C3, `Authorization` |
| `list_comments` | Lista komentarzy | `READ_ONLY` | `read` | `recordId` (`@ResourceRef`), `cursor` (opcjonalny, `CursorCodec`) | `mcp-example-read` | z C3, `Authorization` |
| `get_comment` | Pobierz komentarz | `READ_ONLY` | `read` | `recordId` (`@ResourceRef`), `commentId` (cyfry, `maxLength` 12) | `mcp-example-read` | z C3, `Authorization` |
| `update_record` | Zmień tytuł rekordu | `UPDATE` | `update` | `recordId` (`@ResourceRef`), `title` (1-255 znaków) | `mcp-example-write` | z C3, `Authorization` |

Konwencje: każde narzędzie ma polski tytuł (`@ToolTitle`, do 100 znaków); nazwy `snake_case`, czasownik i obiekt, bez prefiksu systemu (dokłada go gateway); opis mówi, co
narzędzie zwraca, bez instrukcji dla modelu.
