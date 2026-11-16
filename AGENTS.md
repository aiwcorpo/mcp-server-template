# AGENTS.md

The template for MCP servers of [MCP Platform](https://github.com/aiwcorpo/mcp-platform). It is itself a working server for the
placeholder system `example`, and `new-server.sh <system>` copies it into a new server (`mcp-server-<system>`). The server runs
only behind `mcp-gateway`: the library `mcp-starter-lib` (property `mcp-platform.version` in `pom.xml`) verifies the gateway's
internal JWT, the credential, the audit and the errors. Its interfaces are contracts of `mcp-contracts` (C2, C3, C6 manifest, C10
tool errors, C14 well-known paths). Tool authoring: CONTRIBUTING.md and the [developer guide](https://mcp.aiwcorpo.pl/2.0/developers/guides/server-from-template/).

## Commands

JDK 25 is required; the default `java` on PATH may be older. On macOS with Homebrew:
`export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home`.

```bash
./mvnw -B verify      # full CI run, about 50 s; no Docker, no backend (in-process StubBackend)
./mvnw -B -pl mcp-app -am test -Dtest=ExamplePolicyTest -Dsurefire.failIfNoSpecifiedTests=false   # one test class
```

`./mvnw -B verify` also writes `mcp-app/target/mcp-example.jar` and `mcp-app/target/mcp-manifest.json`, and puts the
`mcp-contracts` jar into `~/.m2`, which `new-server.sh` reads for the system id pattern. Run it before the generator.

After any change, check the generator. Its test lives in `mcp-e2e`, not here:

```bash
MCP_TEMPLATE="$PWD" ../mcp-e2e/tests/new-server-test.sh --build   # generates servers into a temp dir and builds them
dir="$(mktemp -d)" && ./new-server.sh notes "$dir/mcp-server-notes" && (cd "$dir/mcp-server-notes" && git init -q && ./mvnw -B verify)
```

There is no separate lint or formatter step.

## Layout

- `mcp-core` (domain, ports), `mcp-application` (use cases), `mcp-integration` (adapter, resolver, version probe), `mcp-tools`
  (tools and their errors), `mcp-app` (bootstrap, policies, single jar, end-to-end tests against `StubBackend`).
- `mcp-app/src/main/resources/mcp/policies/`: Cedar schema, `resource-types.json` and policies. They go into the jar and the
  manifest; any change changes `policiesHash`, which an administrator must approve.
- `deploy/`, `Dockerfile`, `.run/`, `.github/`, `.gitlab-ci.yml`, `Jenkinsfile` are copied into every generated server.
- `local-gateway.env` and `STATUS.md` are local and git-ignored; do not commit them.

## Rules

- Every file git tracks is copied into every new server, with `example`/`Example`/`EXAMPLE` replaced in contents and paths. Only
  `new-server.sh`, `README.md`, `CHANGELOG.md`, `AGENTS.md` and `CLAUDE.md` are skipped (the `case` in `new-server.sh`). So:
  - write `example` only where the renamed word is right for the new server; the generator fails if `example` is left anywhere
    but `LICENSE`, and words such as "for example" are renamed too;
  - add a new template-only file to that `case` and to "What the script renames" in README.md;
  - test material that should not ship to new servers belongs in `mcp-e2e`.
- The project version is replaced by `0.1.0-SNAPSHOT` in the poms and in the image tag of `deploy/compose.yaml`
  (`_VERSION:-<version>`). Keep both in that exact form, or `new-server.sh` leaves the template version behind.
- The generated server must build and pass its tests without edits.
- Contract first: a new resource type field, query field, credential type or error code starts as a `proposed` CC entry in
  `CONTRACT_CHANGES.md` of `mcp-contracts` and waits for a person's approval. Cite the CC id in the commit.
- Each tool needs `@ToolRisk`, `@ToolTitle`, `@ResourceRef` on every id argument, a closed argument schema, a negative test and a
  row in the README table. Policies get allowed and denied cases in `ExamplePolicyTest`.
- Never enable the `dev` profile in an `.env` file, a systemd unit or the image: it accepts the published test keys and plain HTTP.
- Test methods are sentences in camelCase. Actions in workflows stay pinned to a commit SHA, images to a digest.
- CHANGELOG.md: newest first, CC ids in the bullets. The version heading is written by the person who releases.

## Boundaries

Ask a person before you change a version in `pom.xml` or `deploy/compose.yaml`, create or push a tag, change
`.github/workflows/release.yml` (it pushes images to ghcr.io only), push to `main`, or use real systems or accounts. Tests use
dummy data only. Never commit secrets, `.env` or `mcp-*.env` files (only `*.env.template`), TLS material, internal host names or
IP addresses.

## Pull requests

One change per PR. Commit subject in English, imperative, up to 72 characters; the body says what changed and why. Tests go in
the same commit as the code. `./mvnw -B verify` and the generator check must pass. Update README.md, `deploy/README.md` and
CHANGELOG.md when a server author or operator would notice the change. See CONTRIBUTING.md.
