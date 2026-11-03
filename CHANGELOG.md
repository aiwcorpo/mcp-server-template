# Changelog

Format: wersje SemVer, od najnowszej. Do pierwszego wydania publicznego obowiazuje `0.1.0-SNAPSHOT`.

## Niewydane

- Starter 3.0.0-beta-14, kontrakty 6.3.2.
- Serwer przykladowy ma wzorce pelnych serwerow: sonde wersji (`ExampleCapabilityProbe`) i bramke zapisu zamknieta przy
  nieznanej wersji (`ExampleWriteGate`, `update_record`), liste stronicowana kursorem (`list_comments`: `CursorCodec`,
  `PageFiller`, `PagedResult`) i id podrzedne sprawdzane wzgledem ocenionego rekordu (`get_comment`). Opis: `docs/nowy-serwer.md`.
- `ci/run.sh` sam szuka JDK 25, obrazy CI po digescie, akcje GitHub przypiete do SHA, osobny workflow `release.yml`
  (obraz ghcr.io, manifest i SBOM CycloneDX) kopiowany do wygenerowanych serwerow.
- Apache-2.0: `LICENSE`, `NOTICE` (tez w `META-INF` kazdego jara), `SECURITY.md`, `CONTRIBUTING.md`.
