# Contributing

The server follows the contracts of `mcp-contracts` and the library `mcp-starter-lib`. A change that needs a different
contract (a resource type, a query field, a credential type) starts as a `proposed` entry in `CONTRACT_CHANGES.md` of
`mcp-contracts` and waits for approval.

## Setup

JDK 25, Linux or macOS with bash 3.2 or newer; Windows only through WSL2. The platform artifacts come from Maven Central or must be
in `~/.m2` (see the README of `mcp-starter-lib`). No container runtime and no backend system are
needed: the tests run the whole server against an in-process stub backend (`StubBackend` in `mcp-app`).

```bash
./mvnw -B verify     # the same command runs in CI
```

`./mvnw -B verify` must pass before a merge. A new tool needs the items of the [tool guide](https://github.com/aiwcorpo/mcp-docs/blob/main/docs/developer/tools.md) in `mcp-docs`: risk, title,
`@ResourceRef` for every id it names, a closed argument schema, a negative test, and a row in the README table.

## Policies

The Cedar files are in `mcp-app/src/main/resources/mcp/policies/`. They go into the jar and the manifest, and the starter refuses to
start without them:

| File | Content |
| --- | --- |
| `schema.cedarschema` | one namespace, the server's own: an entity type for every resource the policies judge, each with `scopes: Set<ResourceScope>`, and the actions `read`, `update`, `delete`, `admin` |
| `resource-types.json` | one declaration per entity type: `attributes`, `parent` and `parentAttribute`, `idPattern`, `scopePattern` (makes the type grantable in the scope mapping), `earlyScopeCheck` (`true` only for a type whose scope is its exact id and that a write tool takes as `@ResourceRef`), `classification` |
| `*.cedar` | the resource policies, each with an `@id` unique in the server. A policy that names `Action::"call"` or `Tool` is refused: that decision belongs to the gateway |

Write the access rules only. The platform generates the rule for `restricted` data and the one for the service account. The
resolver gives each resource its `scopes`: the scope of the resource and of every container it lies in (see
`ExampleResourceDescription`). The starter validates the files in the `package` phase and at start. A change to any of them
changes `policiesHash` in the manifest, and an administrator approves it before the server decides anything.

`ExamplePolicyTest` asks `PolicyDecisions` of `mcp-platform-test` in plain JUnit, without Spring or a backend:

```java
PolicyDecisions policies = PolicyDecisions.fromResources()
        .scopeMapping(Map.of("mcp-example-proj-ABC", List.of("Example::Project/ABC")));

policies.assertAllowed(PolicyDecisions.caller("mcp-example-write", "mcp-example-proj-ABC"), "update", record);
policies.assertDenied(PolicyDecisions.caller("mcp-example-write"), "update", record);
```

Write allowed and denied cases for each policy you add: a scope that covers the record and one that does not, restricted data,
the service account, a client the gateway does not allow, an emergency override.

## Modules

`mcp-core` (domain and ports), `mcp-application` (use cases), `mcp-integration` (adapter, resolver, version probe), `mcp-tools`
(MCP tools and their errors), `mcp-app` (bootstrap, policies, the single jar, end-to-end tests). The build writes
`mcp-app/target/mcp-manifest.json`: the tools, resource types, policies and their hash, the same document the server serves at
`/.well-known/mcp-manifest`. The starter version is the property `mcp-platform.version` in `pom.xml`.

## Running locally

In IntelliJ start the configurations `LocalGateway` and `Server (dev)` from `.run/`. LocalGateway stands in for the gateway:
test JWKS, policy data from your policies and `.run/scope-mapping.json`, MCP endpoint on port 18500. The server listens on
127.0.0.1:8080 with the `dev` profile and calls the system at `MCP_EXAMPLE_URL` (default `http://localhost:18081`; the stub
backend of the `mcp-e2e` repository listens there). Connect an MCP client to `http://127.0.0.1:18500/mcp` with
`Authorization: Bearer any-token`. The local user is in the groups `mcp-example-read` and `mcp-example-write`.

Never set `SPRING_PROFILES_ACTIVE=dev` in an `.env` file, a systemd unit or an image: that profile accepts JWTs signed with the
published test keys and serves plain HTTP. The starter refuses it in a container.

From a shell, after `./mvnw verify` (replace `<platform version>` with `mcp-platform.version` from `pom.xml`):

```bash
java -jar ~/.m2/repository/pl/aiwcorpo/mcp/mcp-platform-test/<platform version>/mcp-platform-test-<platform version>-local-gateway.jar \
  --server mcp-example --jwks-port 18445 --policies-dir mcp-app/src/main/resources/mcp/policies \
  --scope-mapping .run/scope-mapping.json --env-file local-gateway.env &
SPRING_PROFILES_ACTIVE=dev java -jar mcp-app/target/mcp-example.jar
```

## Commits

Subject in English, imperative, up to 72 characters. The body describes the change, not the process. Tests go in the
same commit as the code they cover.

## License

Contributions are licensed under Apache-2.0 (see `LICENSE`).
