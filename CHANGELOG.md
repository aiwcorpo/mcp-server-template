# Changelog

Versions follow SemVer, newest first. Until the first public release the working version is `0.1.0-SNAPSHOT`.

## Unreleased

- Starter 3.0.0-beta-14, contracts 6.3.2.
- The sample server carries the patterns of the full servers: a version probe (`ExampleCapabilityProbe`), a write gate that fails
  closed on an unknown version (`ExampleWriteGate`, `update_record`), a list paged by cursor (`list_comments`: `CursorCodec`,
  `PageFiller`, `PagedResult`) and child ids checked against the judged record (`get_comment`). Description: `docs/nowy-serwer.md`.
- `ci/run.sh` finds JDK 25 on its own, CI images are pinned by digest, GitHub actions are pinned to SHAs, and a separate
  `release.yml` workflow (ghcr.io image, manifest and CycloneDX SBOM) is copied into generated servers.
- Apache-2.0: `LICENSE`, `NOTICE` (also in `META-INF` of every jar), `SECURITY.md`, `CONTRIBUTING.md`.
