# mcp-example: MCP server template

Template for MCP servers of the aiwcorpo MCP platform, and a working server for the placeholder system `example`. It is the reference
for anyone who writes a new server: copy it, rename the system, write the resource types and the policies, run it and give the
operator of the platform its address. Nothing in the platform lists systems or servers, so a new one needs no change anywhere else and no restart.

The template is built on `mcp-platform-starter` 4.0.0 (Java 25, Spring Boot 4.1.0, Spring AI 2.0.0, `mcp-contracts` 7.0.0). A server made
from it runs only behind the platform gateway: the gateway connects to its address and pulls its manifest, it accepts only calls with the
gateway's internal JWT and answers 401 without it, and it calls the backend system with the credential the gateway attaches to each request. The server does not log users in, decide who
may call a tool or confirm actions; the gateway does. The server owns its tools, the adapter to the system, error codes, and the
policies of its own resources: they are in its jar (`mcp/policies/`), and the starter evaluates them with the scope mapping the gateway
pushes to it. The platform adds the rule that refuses `restricted` data and the one for the service account; the server does not write them.

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
    | System id (`example` -> `notes`) | `spring.application.name` and `server-id: mcp-notes` in `mcp-app/src/main/resources/application.yml`; the `notes.backend.*` and `notes.writes-on-unknown-version` properties and the variables `MCP_NOTES_URL`, `MCP_NOTES_SSL_BUNDLE`, `MCP_NOTES_ALLOW_PLAIN_HTTP`, `MCP_NOTES_WRITES_ON_UNKNOWN_VERSION`; the groups `mcp-notes-read`, `-write`, `-destructive` in the policies and the tests |
    | Cedar namespace (`Example` -> `Notes`) | `namespace Notes` in `schema.cedarschema`; `Notes::Project` and `Notes::Record` in `resource-types.json` and `ExampleResourceModel` (`NotesResourceModel`); `Notes::Action::"read"` in the `*.cedar` files; the scope ids `Notes::Project/ABC` in `.run/scope-mapping.json` and the tests; the ids of the policies (`notes-read`) |
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
   stand-in: test JWKS, policy data pushed to the server from your policies and `.run/scope-mapping.json`, port 18500 for the MCP client) and
   `Server (dev)` (127.0.0.1:8080, dev profile) from `.run/`. The server needs the system at `MCP_NOTES_URL` (default
   `http://localhost:18081`); a real one, or the stub backend of the `mcp-e2e` repository (`stub-backend/`, which accepts any token
   and answers 401 to one that starts with `expired`). Connect an MCP client (MCP Inspector, Claude Code) to
   `http://127.0.0.1:18500/mcp` with the header `Authorization: Bearer any-token`. The local user is in the groups `mcp-notes-read`,
   `-write` and `-destructive`, and `.run/scope-mapping.json` maps the write group to the projects `ABC` and `DEF`. Without IntelliJ,
   see "Run locally".

6. **Run it for the platform and connect it.** Nothing restarts; the full steps are in [`deploy/README.md`](deploy/README.md).
    1. You start the server (`deploy/`) with the address of the gateway's public key set (`MCP_GATEWAY_JWKS_URI`) and the address of the system.
       It serves TLS with a self-signed certificate that it generates on the first start (or your own: `MCP_TLS_CERT` and `MCP_TLS_KEY`, or a
       keystore) and logs the fingerprint of that certificate (`event=tls_certificate ... fingerprint=sha256:...`).
    2. The operator of the platform adds the system `notes` in the broker (`PUT /v1/admin/systems/notes`): adapter `generic-rest`,
       type `user-secret`, with `whoami` (`path` and `usernameField`, for example `/api/me` and `username`) so the broker checks a token
       when it is stored and records the backend user; without `whoami` it stores the token unverified.
    3. The operator enters the server address (`https://<host>:8443/mcp`) in the portal. The gateway connects, pulls the manifest and shows
       the certificate. The operator approves the connection after comparing the fingerprint with your log, then the policies and the tools
       (every tool waits, the read-only ones too). The gateway pushes the scope mapping and the other policy data itself.
    4. In the identity provider, put users in the groups `mcp-notes-read`, `mcp-notes-write` and `mcp-notes-destructive`; the
       administrator maps the groups that may change resources to scopes in the scope mapping.

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 25)"   # macOS; on Linux the JDK 25 directory
./mvnw -B verify     # jar: mcp-app/target/mcp-example.jar, manifest: mcp-app/target/mcp-manifest.json
```

Tests need no Docker and no platform environment. `TestGateway` from `mcp-platform-test` signs internal JWTs, serves the JWKS and pushes the
policy data to the server (with the scope mapping of `SampleScope`), and `StubBackend` (a test class) imitates the API of the system, including its
faults. The GitHub, GitLab and Jenkins files run `./mvnw -B verify`. The generator `new-server.sh` is tested in the `mcp-e2e`
repository (`tests/new-server-test.sh`, which expects this template in `../mcp-server-template`).

The starter version is the property `mcp-platform.version` in `pom.xml`; the BOM pins the contracts. `new-server.sh` reads the
version from there (the `.run/LocalGateway.run.xml` file names the jar of the same version). Platform artifacts come from Maven Central
or from `~/.m2` (see the README of `mcp-starter-lib`). Linux and macOS with bash 3.2 or newer; Windows only through WSL2.

The build writes `mcp-app/target/mcp-manifest.json`: the tools, the resource types, the policies and their hash. It is the document the running
server serves at `/.well-known/mcp-manifest` for the gateway to pull, kept as an artifact so that a reviewer can read what a commit publishes.
Nothing registers it: every new or changed tool, read-only ones included, and every change of the policies wait for an administrator.

Modules: `mcp-core` (domain and ports), `mcp-application` (use cases), `mcp-integration` (adapter, resolver, version probe),
`mcp-tools` (MCP tools and their errors), `mcp-app` (bootstrap, the policies, the single jar, end-to-end tests).

## Configuration

`application.yml` is the production configuration: TLS on the server port, the JWKS of the gateway, `jti` in memory, `https` to the system.
A server does not start without:

| Variable | Meaning |
| --- | --- |
| `MCP_GATEWAY_JWKS_URI` | Public key set of the gateway's internal JWT: `https://<domain of the platform>/.well-known/mcp-jwks.json` |
| `MCP_CONNECT_URL` | Portal page for connecting an account, with `{system}`; sent in `credential_required` |
| `MCP_EXAMPLE_URL` | API address of the system (`https` outside `dev`) |

Optional, the starter reads them: `MCP_TLS_CERT` and `MCP_TLS_KEY` (PEM files) or `MCP_TLS_KEYSTORE` and `MCP_TLS_STORE_PASSWORD` for your own
certificate, otherwise a self-signed one is generated once into `MCP_STATE_DIR` (`/var/lib/mcp/state`, a volume) and its fingerprint is logged at
start; `MCP_PLAIN_HTTP=true` for no TLS inside the Docker network of the platform. `MCP_REDIS_HOST` (`MCP_REDIS_PORT`, `MCP_REDIS_USER`,
`MCP_REDIS_PASSWORD`) and `MCP_PAGING_CURSOR_KEY` (Base64, at least 32 bytes, treated as a secret) are for several instances behind a balancer, which
need both, and the same certificate; one instance keeps the `jti` in memory and the cursor key in the state directory.

Also available: `MCP_EXAMPLE_SSL_BUNDLE`, `MCP_EXAMPLE_WRITES_ON_UNKNOWN_VERSION`, `MCP_GATEWAY_ISSUER`,
`MCP_PORT` (`8443`), `MCP_BIND`, and `MCP_EXAMPLE_ALLOW_PLAIN_HTTP`, which allows `http` to the system outside `dev` and exists only for the mocks of a
test stack. `MCP_ACCEPTED_CREDENTIAL_TYPES` lists the credential types the server accepts; the first one is the default and must match
what the gateway and the broker have for the system, otherwise the gateway refuses the server with `credential_type_mismatch`. The policies are in the jar and the data
is pushed by the gateway. The MCP endpoint is `/mcp`; without a JWT of the right purpose only
`/actuator/health` answers.

## Run locally

The `dev` profile must be the only active profile. It serves plain HTTP on 127.0.0.1, keeps `jti` in memory and needs no certificates.
The starter refuses it in a container or under systemd, so run it on the workstation. The IntelliJ configurations are in `.run/`; the
same from a shell, after `./mvnw verify`:

```bash
# the system on 127.0.0.1:18081 (or MCP_EXAMPLE_URL), for a first try the stub of mcp-e2e/stub-backend
java -jar ~/.m2/repository/pl/aiwcorpo/mcp/mcp-platform-test/4.0.0/mcp-platform-test-4.0.0-local-gateway.jar \
  --server mcp-example --jwks-port 18445 --policies-dir mcp-app/src/main/resources/mcp/policies \
  --scope-mapping .run/scope-mapping.json --env-file local-gateway.env &           # MCP endpoint 127.0.0.1:18500
SPRING_PROFILES_ACTIVE=dev "$JAVA_HOME/bin/java" -jar mcp-app/target/mcp-example.jar      # 127.0.0.1:8080
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/actuator/health            # 200
curl -s -o /dev/null -w '%{http_code}\n' -X POST -H 'Content-Type: application/json' \
     -H 'Accept: application/json, text/event-stream' \
     -d '{"jsonrpc":"2.0","id":1,"method":"ping"}' http://127.0.0.1:8080/mcp               # 401, no JWT
```

LocalGateway forwards `http://127.0.0.1:18500/mcp` to the server with an internal JWT of a local user and your `Authorization` header as
the backend credential. It does none of the gateway's work (no login, no tool approval, no confirmation of destructive tools) and
signs with the published test keys, which only the `dev` profile accepts. It pushes the approval of the policies in the jar to the server at 127.0.0.1:8080, so the
server decides at once; `--scope-mapping` takes your own mapping, `--groups` the groups of the local user. The `dev` profile accepts JWTs
signed with the published test keys and needs no certificate. For that reason it listens on loopback only; never put
`SPRING_PROFILES_ACTIVE=dev` into an `.env` file, a systemd unit or an image.

Against a real system: `MCP_EXAMPLE_URL=https://...` in the environment of the server and a real token in the
`Authorization` header of the client. A platform stack in containers (the `devstack` profile) is described in the README of
`mcp-starter-lib`.

## Install

A server is one jar or one image. `deploy/` has the Compose file with `server.env` (and `certs/`, only for your own certificate), the systemd
unit, the installation steps and the steps to add the server to the platform and to remove it; for a container run `docker build` (or
`docker compose build` in `deploy/`) after `./mvnw verify` (image `eclipse-temurin:25-jre`, user `mcp`, port 8443, TLS, a volume at
`/var/lib/mcp/state`). Operating notes are in `mcp-docs`.

## Documentation

The guides for developers, operators and administrators are in [`mcp-docs`](https://github.com/aiwcorpo/mcp-docs): [A new server](https://github.com/aiwcorpo/mcp-docs/blob/main/docs/developer/new-server.md) and [Writing tools](https://github.com/aiwcorpo/mcp-docs/blob/main/docs/developer/tools.md). Deployment files: `deploy/`, `Dockerfile`.

Vulnerability reports: `SECURITY.md`. Contributions: `CONTRIBUTING.md`. Changes: `CHANGELOG.md`.

## License

Apache-2.0, see `LICENSE`.
