# Changelog

Versions follow SemVer, newest first.

## 1.0.0 - 2026-11-04

First public release. Built on `mcp-platform-starter` 3.0.0 (contracts 6.3.2).

- Template of a native MCP server: a working server for the placeholder system `example` (Jira resource model) with sample tools
  (`get_record`, `search_records`, `list_comments`, `get_comment`, `update_record`), tests against a stub backend and the
  CI entry point `ci/run.sh`. `new-server.sh [--trial] <system> [target-dir]` generates a server for a system known to the
  contracts (jira, confluence, gitlab, servicedesk, opensearch) or a throwaway one with `--trial`.
- Generated servers start at version `0.1.0-SNAPSHOT` regardless of the template version, and get their own English `README.md`
  and `CHANGELOG.md`.
- The sample server carries the patterns of the full servers: a version probe (`ExampleCapabilityProbe`), a write gate that fails
  closed on an unknown version (`ExampleWriteGate`), a list paged by cursor (`CursorCodec`, `PageFiller`, `PagedResult`) and child
  ids checked against the judged record (`get_comment`).
- CI images are pinned by digest, GitHub actions by SHA; a separate `release.yml` workflow (ghcr.io image, manifest and CycloneDX
  SBOM) is copied into generated servers. `ci/run.sh generator` builds and tests a server for every system.
- Apache-2.0 licence, NOTICE, SECURITY.md, CONTRIBUTING.md.
