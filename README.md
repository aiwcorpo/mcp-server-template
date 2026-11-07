# mcp-example: MCP server template

Template for MCP servers of the aiwcorpo MCP platform, and a working server for the placeholder system `example`. It is the reference
for anyone who writes a new server: copy it, rename the system, write the resource types and the policies, and register the server at a
running platform. Nothing in the platform lists systems or servers, so a new one needs no change anywhere else and no restart.

The template is built on `mcp-platform-starter` 4.0.0 (Java 25, Spring Boot 4.1.0, Spring AI 2.0.0, `mcp-contracts` 7.0.0). A server made
from it runs only behind the platform gateway: it accepts traffic over mTLS with the gateway's internal JWT and answers 401 without it,
and it calls the backend system with the credential the gateway attaches to each request. The server does not log users in, decide who
may call a tool or confirm actions; the gateway does. The server owns its tools, the adapter to the system, error codes, and the
policies of its own resources: they are in its jar (`mcp/policies/`), and the starter evaluates them with the scope mapping the gateway
serves. The platform adds the rule that refuses `restricted` data and the one for the service account; the server does not write them.

Platform documentation lives in [aiwcorpo/mcp-docs](https://github.com/aiwcorpo/mcp-docs). For server authors the page to
read is [A new server](https://github.com/aiwcorpo/mcp-docs/blob/main/docs/developer/new-server.md)
(`docs/developer/new-server.md`), with the tool rules next to it in `docs/developer/tools.md`.

## Create a new MCP server

Example: a server for the system `notes`.

1. **Copy.** Build the template once (`./mvnw verify`, JDK 25; the generator reads the pattern of a system id from the contracts jar in
   `~/.m2`), then:

    ```bash
    ./new-server.sh notes                         # creates ../mcp-server-notes
    ./new-server.sh notes /path/to/mcp-server-notes
    cd ../mcp-server-notes && git init
    ./mvnw verify                                 # passes without manual changes
    ```

    The system id is lower case letters and digits, 2 to 31 characters, and not one the contracts reserve (`gateway`, `broker`,
    `portal`, `admin`, ...). The script copies the files git tracks, renames, resets the version to `0.1.0-SNAPSHOT`, writes a short
    README and CHANGELOG of its own and fails if the word `example` is left anywhere. The target path must not exist and must be outside
    the template. `new-server.sh`, its test, this README and the template's `CHANGELOG.md` are not copied.

2. **Rename.** Everything below is what the script changes, so a copy made by hand changes the same places (`grep -rIil example .` lists
   them; `LICENSE` stays as it is).

    | What | `example` becomes `notes` in |
    | --- | --- |
    | System id (`example` -> `notes`) | `spring.application.name`, `server-id: mcp-notes` and `manifest.internal-url` in `mcp-app/src/main/resources/application.yml` and `application-dev.yml`; the `notes.backend.*` and `notes.writes-on-unknown-version` properties and the variables `MCP_NOTES_URL`, `MCP_NOTES_SSL_BUNDLE`, `MCP_NOTES_ALLOW_PLAIN_HTTP`, `MCP_NOTES_WRITES_ON_UNKNOWN_VERSION`; the groups `mcp-notes-read`, `-write`, `-destructive` in the policies and the tests; the certificate names `mcp-notes` and `mcp-notes.internal` in `deploy/` |
    | Cedar namespace (`Example` -> `Notes`) | `namespace Notes` in `schema.cedarschema`; `Notes::Project` and `Notes::Record` in `resource-types.json` and `ExampleResourceModel` (`NotesResourceModel`); `Notes::Action::"read"` in the `*.cedar` files; the scope ids `Notes::Project/ABC` in `local/scope-mapping.json` and the tests; the ids of the policies (`notes-read`) |
    | Java | the package `pl.aiwcorpo.mcp.example` (directories included) -> `pl.aiwcorpo.mcp.notes`; the class prefix `Example` (`ExampleTool`, `ExampleRecordResolver`, `ExamplePolicyTest`, ...) -> `Notes`; the Maven groupId `pl.aiwcorpo.mcp.notes`, the parent artifact `mcp-notes-parent`, the jar `mcp-notes.jar` |
    | Deployment and CI | `deploy/systemd/mcp-notes.service` and `.env.template`, `deploy/compose.yaml` (service, image `mcp-server-notes`, `MCP_NOTES_VERSION`), `deploy/server.env`, `Dockerfile` (jar and image names); `.github/workflows/release.yml`; `NOTICE`; the `--server mcp-notes` of `.run/LocalGateway.run.xml` |

    The Cedar namespace is the system id with a capital letter. If it should read differently (`NotesApp`), change it in the files of the
    second row only; the starter takes the namespace from `resource-types.json` and the groups and the system id do not depend on it.

3. **Write the resource types and the policies** in `mcp-app/src/main/resources/mcp/policies/`. These files are the server's own and the
   starter refuses to start without them:

    | File | What it holds |
    | --- | --- |
    | `schema.cedarschema` | one namespace, the server's own: an entity type for every resource the policies judge, each with `scopes: Set<ResourceScope>`, and the actions `read`, `update`, `delete`, `admin` that apply to them |
    | `resource-types.json` | one declaration per entity type: `attributes` the resolver fills, `parent` and `parentAttribute`, `idPattern` (what an id argument and a resolved id must match), `scopePattern` (makes the type grantable in the scope mapping; omit it for a type nobody is granted), `earlyScopeCheck` (`true` only for a type whose scope is its exact id and that a write tool takes as `@ResourceRef`: the gateway then checks the argument before the call), `classification` (the attribute that carries `restricted`, `string` or `set`, required or not) |
    | `*.cedar` | the resource policies, each with an `@id` unique in the server; actions are `Notes::Action::"read"` and so on. A policy that names `Action::"call"` or `Tool` is refused: that decision is the gateway's |

    Write the rules for access. Do not write the rule for restricted data or for the service account: the platform generates them for
    every declared type. The resolver gives each resource its `scopes` (the scope of the resource and of every container it lies in), see
    `ExampleResourceDescription`. The starter validates the files at build time (the manifest is written in the `package` phase) and at
    start, and the message lists every reason. The files are part of the manifest and its `policiesHash`, so changing one puts the
    deployed server in `pending_approval` until an administrator approves it.

4. **Test the policies.** `ExamplePolicyTest` (`NotesPolicyTest` after the rename) asks `PolicyDecisions` of `mcp-platform-test` in plain
   JUnit, without Spring, a gateway or a backend:

    ```java
    PolicyDecisions policies = PolicyDecisions.fromResources()
            .scopeMapping(Map.of("mcp-notes-proj-ABC", List.of("Notes::Project/ABC")));

    policies.assertAllowed(PolicyDecisions.caller("mcp-notes-write", "mcp-notes-proj-ABC"), "update", record);
    policies.assertDenied(PolicyDecisions.caller("mcp-notes-write"), "update", record);
    ```

    The test builds the resources as the resolver would (declared attributes, parent, scopes) and covers allowed and denied calls, a scope
    that covers the record and one that does not, restricted data in any spelling, the service account outside the user's scopes, a client
    the gateway does not allow and an emergency override. Write the same kind of cases for each policy you add. `./mvnw verify` runs
    them, and the tool tests of `mcp-app` over HTTP against an in-process stub backend.

5. **Run it locally** with LocalGateway, from IntelliJ: open the project and start the run configurations `LocalGateway` (the gateway's
   stand-in: test JWKS and policy data from your policies and `local/scope-mapping.json`, port 18500 for the MCP client) and
   `Server (dev)` (127.0.0.1:8080, dev profile) from `.run/`. The server needs the system at `MCP_NOTES_URL` (default
   `http://localhost:18081`); a real one, or the stub backend of the `mcp-e2e` repository (`stub-backend/`, which accepts any token
   and answers 401 to one that starts with `expired`). Connect an MCP client (MCP Inspector, Claude Code) to
   `http://127.0.0.1:18500/mcp` with the header `Authorization: Bearer any-token`. The local user is in the groups `mcp-notes-read`,
   `-write` and `-destructive`, and `local/scope-mapping.json` maps the write group to the projects `ABC` and `DEF`. Without IntelliJ,
   see "Run locally".

6. **Add it to the platform.** Nothing restarts; the full calls are in [`deploy/README.md`](deploy/README.md).
    1. Broker: add the system `notes` through the admin API or the portal (`PUT /v1/admin/systems/notes`): adapter `generic-rest`,
       type `user-secret`, with `whoami` (`path` and `usernameField`, for example `/api/me` and `username`) so the broker checks a token
       when it is stored and records the backend user; without `whoami` it stores the token unverified.
    2. Gateway: add the server `mcp-notes` for it through the admin API or the portal (`POST /admin/v1/servers`).
    3. Deploy the server (`deploy/`). Its certificate is `CN=mcp-notes`, SAN `mcp-notes.internal`, `serverAuth` and `clientAuth`.
    4. CI registers the manifest of the build (`mcp-app/target/mcp-manifest.json`) at the gateway. Tools are invisible until approved.
    5. An administrator approves the policies and the tools that change data.
    6. In the identity provider, put users in the groups `mcp-notes-read`, `mcp-notes-write` and `mcp-notes-destructive`; the
       administrator maps the groups that may change resources to scopes in the scope mapping.

7. **Remove it.** Kill switch for the server (if it must stop at once), delete the server in the gateway, delete the system in the broker,
   stop the container. Nothing restarts.

The rest of the work is the usual one: replace the sample domain and the adapter, write the tools (`@ToolRisk`, `@ToolTitle`, limits on
every argument, `@ResourceRef` on every resource id), register the class in `McpToolsConfiguration` and in `ManifestExport.manifest()`
(`ManifestTest` fails when they differ), write the tests, and extend the generated README with the tools (title, risk, action, group).
Add a policy for every action a tool uses: `delete` for a `DESTRUCTIVE` tool (group `mcp-<system>-destructive`), `admin` where it applies.
Search goes through `QueryScope`, results through `@ResourceRef` on the item or a `ResultFilter`; do not accept a query language from
the model. `<System>ResourceDescription` must report the classification as the system says it; a resource without a required attribute
is refused, so never fill a gap with an empty value. Set `MIN_MAJOR`, `MIN_MINOR` and `MAX_MAJOR` of the version probe to what you
tested. The Dockerfile cannot use Alpine because `cedar-java` needs glibc.

## What the sample server contains

Five tools, shaped like a ticket system: a `record` lies in a `project`, and the project carries the classification. `get_record`,
`search_records`, `list_comments`, `get_comment` (all `READ_ONLY`) and `update_record` (`UPDATE`). Resource types: `Example::Project` (grantable,
with the required classification `category`) and `Example::Record` (its parent is the project). Policies: reading follows the user's rights
in the backend and needs the read group; a change needs the write group and a scope that covers the record. They also show the four
patterns the full servers use:

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
./mvnw -B verify     # jar: mcp-app/target/mcp-example.jar, manifest: mcp-app/target/mcp-manifest.json
```

Tests need no Docker and no platform environment. `TestGateway` from `mcp-platform-test` signs internal JWTs, serves the JWKS and the
policy data (with the scope mapping of `SampleScope`), and `StubBackend` (a test class) imitates the API of the system, including its
faults. The GitHub, GitLab and Jenkins files run `./mvnw -B verify`. The generator `new-server.sh` is tested in the `mcp-e2e`
repository (`tests/new-server-test.sh`, which expects this template in `../mcp-server-template`).

The starter version is the property `mcp-platform.version` in `pom.xml`; the BOM pins the contracts. `new-server.sh` reads the
version from there (the `.run/LocalGateway.run.xml` file names the jar of the same version). Platform artifacts come from Maven Central
or from `~/.m2` (see the README of `mcp-starter-lib`). Linux and macOS with bash 3.2 or newer; Windows only through WSL2.

The build writes `mcp-app/target/mcp-manifest.json`: the tools, the resource types, the policies and their hash. CI registers that file
with the gateway: read-only tools are approved automatically, and new or changed `UPDATE` and `DESTRUCTIVE` tools, and every change of
the policies, wait for an administrator.

Modules: `mcp-core` (domain and ports), `mcp-application` (use cases), `mcp-integration` (adapter, resolver, version probe),
`mcp-tools` (MCP tools and their errors), `mcp-app` (bootstrap, the policies, the single jar, end-to-end tests).

## Configuration

`application.yml` is the production configuration: mTLS, JWKS and policy data of the gateway over TLS, `jti` store in Redis, `https` to
the system. A server does not start without:

| Variable | Meaning |
| --- | --- |
| `MCP_TLS_KEYSTORE`, `MCP_TLS_TRUSTSTORE`, `MCP_TLS_STORE_PASSWORD` | PKCS12 files: server certificate (CN `mcp-example`, SAN `mcp-example.internal`, `serverAuth` and `clientAuth`) and the internal CA |
| `MCP_GATEWAY_JWKS_URI` | JWKS of the gateway's internal JWT (`https`) |
| `MCP_GATEWAY_POLICY_DATA_URL` | Policy data of the gateway (`https://<gateway>:8443/internal/v1/policy-data`): scope mapping, allowed AI clients, emergency overrides; fetched with the server's own certificate |
| `MCP_CONNECT_URL` | Portal page for connecting an account, with `{system}`; sent in `credential_required` |
| `MCP_EXAMPLE_URL` | API address of the system (`https` outside `dev`) |
| `MCP_INTERNAL_URL` | Address of the server in the manifest |
| `MCP_REDIS_HOST`, `MCP_REDIS_PORT`, `MCP_REDIS_USER`, `MCP_REDIS_PASSWORD` | Redis for single-use `jti` (ACL user `mcp-example`) |

Also available: `MCP_GATEWAY_TLS_NAME` (the name the gateway's certificate carries, when it is not the host of the policy data address),
`MCP_PAGING_CURSOR_KEY` (Base64, at least 32 bytes, the same on every instance, treated as a secret; without it each process makes its
own and cursors do not survive a restart), `MCP_EXAMPLE_SSL_BUNDLE`, `MCP_EXAMPLE_WRITES_ON_UNKNOWN_VERSION`, `MCP_GATEWAY_ISSUER`,
`MCP_PORT` (`8443`), `MCP_BIND`, and `MCP_EXAMPLE_ALLOW_PLAIN_HTTP`, which allows `http` outside `dev` and exists only for the mocks of a
test stack. `MCP_ACCEPTED_CREDENTIAL_TYPES` lists the credential types the server accepts; the first one is the default and must match
what the gateway and the broker have for the system, otherwise registration fails with `credential_type_mismatch`. The policies are in the jar and the data
comes from the gateway. The MCP endpoint is `/mcp`; without a JWT only
`/actuator/health` answers.

## Run locally

The `dev` profile must be the only active profile. It serves plain HTTP on 127.0.0.1, keeps `jti` in memory and needs no certificates.
The starter refuses it in a container or under systemd, so run it on the workstation. The IntelliJ configurations are in `.run/`; the
same from a shell, after `./mvnw verify`:

```bash
# the system on 127.0.0.1:18081 (or MCP_EXAMPLE_URL), for a first try the stub of mcp-e2e/stub-backend
java -jar ~/.m2/repository/pl/aiwcorpo/mcp/mcp-platform-test/4.0.0/mcp-platform-test-4.0.0-local-gateway.jar \
  --server mcp-example --jwks-port 18445 --policies-dir mcp-app/src/main/resources/mcp/policies \
  --scope-mapping local/scope-mapping.json --env-file local/local-gateway.env &           # MCP endpoint 127.0.0.1:18500
SPRING_PROFILES_ACTIVE=dev "$JAVA_HOME/bin/java" -jar mcp-app/target/mcp-example.jar      # 127.0.0.1:8080
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/actuator/health            # 200
curl -s -o /dev/null -w '%{http_code}\n' -X POST -H 'Content-Type: application/json' \
     -H 'Accept: application/json, text/event-stream' \
     -d '{"jsonrpc":"2.0","id":1,"method":"ping"}' http://127.0.0.1:8080/mcp               # 401, no JWT
```

LocalGateway forwards `http://127.0.0.1:18500/mcp` to the server with an internal JWT of a local user and your `Authorization` header as
the backend credential. It does none of the gateway's work (no login, no tool approval, no confirmation of destructive tools) and
signs with the published test keys, which only the `dev` profile accepts. It serves the approval of the policies in the jar, so the
server decides at once; `--scope-mapping` takes your own mapping, `--groups` the groups of the local user. The `dev` profile accepts JWTs
signed with the published test keys and needs no client certificate. For that reason it listens on loopback only; never put
`SPRING_PROFILES_ACTIVE=dev` into an `.env` file, a systemd unit or an image.

Against a real system: `MCP_EXAMPLE_URL=https://...` in the environment of the server and a real token in the
`Authorization` header of the client. A platform stack in containers (the `devstack` profile) is described in the README of
`mcp-starter-lib`.

## Install

A server is one jar or one image. `deploy/` has the Compose file with `server.env` and `certs/`, the systemd unit, the installation
scripts and the steps to add the server to the platform and to remove it; for a container run `docker build` (or
`docker compose build` in `deploy/`) after `./mvnw verify` (image `eclipse-temurin:25-jre`, user `mcp`, port 8443, mTLS). Operating notes
are in `mcp-docs`.

## Documentation

The guides for developers, operators and administrators are in [`mcp-docs`](https://github.com/aiwcorpo/mcp-docs): [A new server](https://github.com/aiwcorpo/mcp-docs/blob/main/docs/developer/new-server.md) and [Writing tools](https://github.com/aiwcorpo/mcp-docs/blob/main/docs/developer/tools.md). Deployment files: `deploy/`, `Dockerfile`.

Vulnerability reports: `SECURITY.md`. Contributions: `CONTRIBUTING.md`. Changes: `CHANGELOG.md`.

## License

Apache-2.0, see `LICENSE`.
