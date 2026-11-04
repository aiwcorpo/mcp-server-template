# mcp-example: MCP server template

Template for MCP servers of the aiwcorpo MCP platform, and a working server for the placeholder system `example`. The
repository is a tool for people who write servers: `new-server.sh <system>` turns it into a new server such as `mcp-jira`.
The generator writes a short README of its own for every new server; this one stays in the template.

The template is built on `mcp-platform-starter` 3.0.0 (Java 25, Spring Boot 4.1.0, Spring AI 2.0.0, `mcp-contracts`
6.3.2). A server made from it runs only behind the platform gateway: it accepts traffic over mTLS with the gateway's internal
JWT and answers 401 without it, and it calls the backend system with the credential the gateway attaches to each request. The
server does not log users in, decide who may call a tool or confirm actions; the gateway does. The server owns its tools, the
adapter to the system, error codes, and the description of its resources for the policy: the starter evaluates the same signed
Cedar bundle as the gateway and refuses resources marked `restricted`.

Platform documentation lives in [aiwcorpo/mcp-docs](https://github.com/aiwcorpo/mcp-docs). For server authors the page to
read is [A new server](https://github.com/aiwcorpo/mcp-docs/blob/main/docs/developer/new-server.md)
(`docs/developer/new-server.md`), with the tool rules next to it in `docs/developer/tools.md`.

## Make a new server

1. Build the template once (`./mvnw verify`, JDK 25). The generator reads the list of valid systems from the contracts jar in
   `~/.m2`.
2. Generate the server. `<system>` must be one of the `SystemId` values of the contracts: `jira`, `confluence`, `gitlab`,
   `servicedesk`, `opensearch`. Only such servers start outside the `dev` profile, and only for them the dev stack has
   certificates and Redis users.

   ```bash
   ./new-server.sh jira                          # creates ../mcp-server-jira
   ./new-server.sh jira /path/to/mcp-server-jira
   ./new-server.sh --trial demo ../mcp-server-demo   # throwaway server, dev profile only
   cd ../mcp-server-jira && git init
   ./mvnw verify                                 # passes without manual changes
   ```

   A `--trial` server may have any lower case name. It builds and passes its tests, but its manifest is invalid, so eight
   `ManifestTest` cases are skipped and `credential_required` shows up as `internal_error`. The target path must not exist
   and must be outside the template. If generation fails, the target directory is removed.
3. Check what the script did. It renames the placeholder in contents and paths (`pl.aiwcorpo.mcp.example` to
   `pl.aiwcorpo.mcp.jira`, `mcp-example` to `mcp-jira`, `MCP_EXAMPLE_URL` to `MCP_JIRA_URL`, `ExampleTool` to `JiraTool`, and so
   on), copies only files tracked by git, and fails if the word `example` is left anywhere in the result.
   Not copied: `new-server.sh`, its test, this README, `profiles/`, `STATUS.md` and the template's `CHANGELOG.md` (the
   new server gets its own `CHANGELOG.md` and a short `README.md` with the build commands and a link to the developer guide). `LICENSE`, `NOTICE`, `SECURITY.md`, `CONTRIBUTING.md` and the release workflow are copied.
4. Profiles. The template has the shape of Jira (`Jira::Issue` in `Jira::Project`). For the other systems the script replaces
   four files with the ones in `profiles/<system>/`, and sets `accepted-credential-types` for the system (Jira and Confluence
   `user-secret`, GitLab `user-oauth`, service desk `service-account`, OpenSearch `token-exchange`).

   | File | What changes |
   | --- | --- |
   | `ExampleResourceModel` (mcp-core) | resource type and container, identifier patterns of the contracts, scope rule |
   | `ExampleResourceDescription` (mcp-integration) | attributes the Cedar schema of the system requires |
   | `ExampleSearchQuery` (mcp-tools) | search query: JQL (Jira), CQL (Confluence), or built from the granted groups, queues or indices |
   | `SampleScope` (test) | identifier shapes and read groups used by the tests |

   Profiles: `gitlab`, `confluence`, `servicedesk`, `opensearch` (the last also replaces the port, use case, service, adapter,
   tool and its test, because search works on index names). `jira` and trial servers use the template unchanged.
5. Replace the domain and the adapter. Swap `<System>Record` for the objects of the system (only fields that tools return and
   the policy needs). In `<System>ResourceAdapter` put the real API paths, response DTOs, the way the credential is attached
   (`credential::applyTo` for `Authorization`, `credential.value()` for a header such as `PRIVATE-TOKEN`), 404 and 403 as
   "not visible", 401 as `CredentialRequiredException`. `<System>ResourceDescription` must report attributes taken from the
   system (project category, space labels, group topics, index tags). A resource without a required attribute is refused, so
   never fill a gap with an empty value.
6. Write the tools. Each tool in `<System>Tool` (or several classes) needs `@ToolRisk`, `@ToolTitle`, limits on every argument
   and a `@ResourceRef` on every resource id. Register the class in `McpToolsConfiguration` and in `ManifestExport.manifest()`;
   `ManifestTest` fails when the two differ. Search goes through `QueryScope`, results through `@ResourceRef` on the item or a
   `ResultFilter`. A destructive tool uses action `delete` and the group `mcp-<system>-destructive`. Remove or rework the five
   sample tools. Do not accept a query language from the model.
7. Write the tests. `StubBackend` answers like the API of the system. For every tool that touches a resource: a `restricted`
   resource gives the same `not_found_or_denied` as a missing one, a search never returns resources from outside its scope,
   a missing group gives `access_denied`, plus `credential_required` (no credential, and 401 from the system) and
   `invalid_argument`. `GatewayOnlyAccessTest` stays. Identifiers used in tests must match the resource patterns of the
   contracts, which is why `SampleScope` differs per system.
8. Finish the documentation and the deployment. Extend the generated README with the tools of the system (title, risk, action,
   group `mcp-<system>-read|write|destructive` and credential type), write your own `STATUS.md`,
   and set the deployment variables from `deploy/systemd/mcp-<system>.env.template`. For the dev stack build the image
   with `docker build -t mcp-<system>:dev .`; the Dockerfile cannot use Alpine because `cedar-java` needs glibc.

## What the sample server contains

Five tools, in the shape of Jira: `get_record`, `search_records`, `list_comments`, `get_comment` (all `READ_ONLY`) and
`update_record` (`UPDATE`). They also show the four patterns the full servers use:

| Pattern | Where |
| --- | --- |
| Version probe of the backend, state `SUPPORTED`, `DEGRADED` or `UNAVAILABLE` | `ExampleCapabilityProbe`: reads `GET /api/server-info` without a credential; set `MIN_MAJOR`, `MIN_MINOR`, `MAX_MAJOR` to the range you tested |
| Write gate that fails closed on an unknown version | `ExampleWriteGate.requireWritable()` is the first statement of every `UPDATE` and `DESTRUCTIVE` tool; `MCP_EXAMPLE_WRITES_ON_UNKNOWN_VERSION=true` opens writes when the probe could not read a version |
| Paged results | `list_comments`: `PagedResult`, `CursorCodec`, `PageFiller`; the cursor is encrypted and bound to user, tool and arguments |
| Child ids checked against the judged parent | `get_comment`: the policy judges the record, and the service keeps only comments the system attributes to that record |

Rules behind these patterns:

- The probe has no credential, so it can read only what the system tells everyone. A system that hides its version needs another
  source or a broker decision; the server never holds a secret of its own.
- A version outside the tested range is `DEGRADED` (read only). Add `fixtures/<system>-<version>/` with a real response and a test.
- `UPDATE` sets a target value instead of incrementing, so a client may repeat the call after a `502` and the state stays the same.
- A page never carries a total from the system, because a total counts what the caller cannot see.
- A child id is never a resource by itself. Ask the system for the child under the judged parent, check the ids in the response,
  and treat a mismatch as missing.

## Build and test

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 25)"   # macOS; on Linux the JDK 25 directory
./mvnw verify        # jar: mcp-app/target/mcp-example.jar
make ci              # ci/run.sh verify; the same script runs in CI
tests/new-server-test.sh --build    # generates every profile and builds each result
```

Tests need no Docker and no platform environment. `TestGateway` from `mcp-platform-test` signs internal JWTs and serves JWKS,
and `StubBackend` imitates the API of the system. `ci/run.sh generator` runs the generator test in CI.

The starter version is the property `mcp-platform.version` in `pom.xml`; the BOM pins the contracts. `new-server.sh` reads the
version from there. Platform artifacts come from Maven Central or from `~/.m2` (see the README of
`mcp-starter-lib`). Linux and macOS with bash 3.2 or newer; Windows only through WSL2.

A normal build also writes `mcp-app/target/mcp-manifest.json` for servers of a real system (`manifest.export.skip=false`; the
template and trial servers skip it). CI registers that file with the gateway: read-only tools are approved automatically, and
new or changed `UPDATE` and `DESTRUCTIVE` tools wait for an administrator.

Modules: `mcp-core` (domain and ports), `mcp-application` (use cases), `mcp-integration` (adapter, resolver, version probe),
`mcp-tools` (MCP tools and their errors), `mcp-app` (bootstrap, the single jar, end-to-end tests).

## Configuration

`application.yml` is the production configuration: mTLS, JWKS of the gateway over TLS, `jti` store in Redis, `https` to the
system. The template itself and trial servers run only in the `dev` profile. A server of a real system does not start without:

| Variable | Meaning |
| --- | --- |
| `MCP_TLS_KEYSTORE`, `MCP_TLS_TRUSTSTORE`, `MCP_TLS_STORE_PASSWORD` | PKCS12 files: server certificate (SAN `mcp-example.internal`) and the internal CA |
| `MCP_GATEWAY_JWKS_URI` | JWKS of the gateway's internal JWT (`https`) |
| `MCP_CONNECT_URL` | Portal page for connecting an account, with `{system}`; sent in `credential_required` |
| `MCP_EXAMPLE_URL` | API address of the system (`https` outside `dev`) |
| `MCP_POLICY_URL`, `MCP_POLICY_KEY_ID`, `MCP_POLICY_PUBLIC_KEY_FILE`, `MCP_POLICY_STATE_DIR` | Signed policy bundle (Ed25519); the state directory prevents a rollback after a restart; without a bundle within 30 s the server does not start |
| `MCP_INTERNAL_URL` | Address of the server in the manifest |
| `MCP_REDIS_HOST`, `MCP_REDIS_PORT`, `MCP_REDIS_USER`, `MCP_REDIS_PASSWORD` | Redis for single-use `jti` (ACL user `mcp-example`) |

Also available: `MCP_PAGING_CURSOR_KEY` (Base64, at least 32 bytes, the same on every instance, treated as a secret;
without it each process makes its own and cursors do not survive a restart), `MCP_EXAMPLE_SSL_BUNDLE`,
`MCP_EXAMPLE_WRITES_ON_UNKNOWN_VERSION`, `MCP_GATEWAY_ISSUER`, `MCP_PORT` (`8443`), `MCP_BIND`, and
`MCP_EXAMPLE_ALLOW_PLAIN_HTTP`, which allows `http` outside `dev` and exists only for the mocks of the dev stack.
`MCP_ACCEPTED_CREDENTIAL_TYPES` lists the credential types the server accepts; the first one is the default and must match
what the gateway and broker have for the system, otherwise registration fails with `credential_type_mismatch`. Defaults are in
the [server environment reference](https://github.com/aiwcorpo/mcp-docs/blob/main/docs/reference/env-servers.md)
(`docs/reference/env-servers.md` in `mcp-docs`). The MCP endpoint is `/mcp`; without a JWT only `/actuator/health` answers.

## Run locally without the platform

The `dev` profile must be the only active profile. It serves plain HTTP on 127.0.0.1, keeps `jti` in memory and needs no
certificates. There is no gateway, so it only shows that the server starts and rejects requests without a JWT:

```bash
SPRING_PROFILES_ACTIVE=dev MCP_PORT=18491 "$JAVA_HOME/bin/java" -jar mcp-app/target/mcp-example.jar
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:18491/actuator/health    # 200
curl -s -o /dev/null -w '%{http_code}\n' -X POST -H 'Content-Type: application/json' \
     -H 'Accept: application/json, text/event-stream' \
     -d '{"jsonrpc":"2.0","id":1,"method":"ping"}' http://127.0.0.1:18491/mcp       # 401
```

The `dev` profile accepts JWTs signed with the published test keys of the contracts and needs no client certificate. For that
reason it listens on loopback only, and the starter refuses to start `dev` on another address. Never enable it on a host or in a
container that the gateway connects to, and never put `SPRING_PROFILES_ACTIVE=dev` into an `.env` file, a systemd unit or an
image. Running against the dev stack (mTLS from the host, or the `devstack` profile in containers) needs a local stack that is not public yet;
to run the platform use the Compose install in `mcp-docs` (`examples/compose`). It works only for the five systems of the contracts.

## Install

A generated server is one jar or one image. For a VM with systemd use `deploy/scripts/install.sh` and
`deploy/systemd/mcp-example.env.template`; the [VM installation guide](https://github.com/aiwcorpo/mcp-docs/blob/main/docs/operator/install-vm.md) in `mcp-docs` has the steps. For a container run
`docker build` after `./mvnw verify` (image `eclipse-temurin:25-jre`, user `mcp`, port 8443, mTLS). Operating notes are in `mcp-docs`.

## Documentation

The guides for developers, operators and administrators are in [`mcp-docs`](https://github.com/aiwcorpo/mcp-docs): [A new server](https://github.com/aiwcorpo/mcp-docs/blob/main/docs/developer/new-server.md) and [Writing tools](https://github.com/aiwcorpo/mcp-docs/blob/main/docs/developer/tools.md). Deployment files: `deploy/`, `Dockerfile`.

Vulnerability reports: `SECURITY.md`. Contributions: `CONTRIBUTING.md`. Changes: `CHANGELOG.md`.

## License

Apache-2.0, see `LICENSE`.
