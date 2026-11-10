# mcp-server-template

Template for MCP servers of the aiwcorpo MCP platform, for developers who write a server for a new system. `new-server.sh`
generates a working server from it; the template itself is a server for the placeholder system `example` with a stub backend.

## Quick start

JDK 25 is required.

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 25)"   # macOS; on Linux the JDK 25 directory
./mvnw -B verify                                      # once: the generator reads the system id pattern from the contracts jar
./new-server.sh notes                                 # creates ../mcp-server-notes
cd ../mcp-server-notes && git init && ./mvnw -B verify
```

The generated server builds and passes its tests without changes. `./new-server.sh notes /path/to/dir` puts it elsewhere; the
target must not exist and must lie outside the template.

The system id is 2 to 31 lower case letters and digits, and not an id that the contracts reserve (`gateway`, `broker`, `portal`,
`admin`, ...) or a Java keyword.

## Write the server

In the generated directory:

1. Replace the sample record domain in `mcp-core`, `mcp-application` and `mcp-integration` with the resources and the API
   calls of your system.
2. Declare the resource types in `mcp-app/src/main/resources/mcp/policies/resource-types.json` and the entity types in
   `schema.cedarschema`.
3. Write the access rules in `*.cedar` in the same directory.
4. Change the tools in `mcp-tools`. Each tool needs `@ToolRisk`, `@ToolTitle`, `@ResourceRef` on every id argument and a closed
   argument schema.
5. Change the tests: `<System>PolicyTest` for the policies, the tool tests in `mcp-app` against the in-process stub backend.
6. Run `./mvnw -B verify`. It also writes `mcp-app/target/mcp-manifest.json`.
7. Start the server with `deploy/` ([deploy/README.md](deploy/README.md)) and give the platform operator its address. The
   operator adds it in the portal; nothing else in the platform changes or restarts.

Details for each step: [CONTRIBUTING.md](CONTRIBUTING.md). Platform guides: [A new server](https://github.com/aiwcorpo/mcp-docs/blob/main/docs/developer/new-server.md),
[Writing tools](https://github.com/aiwcorpo/mcp-docs/blob/main/docs/developer/tools.md).

## Tools of the sample server

Shown to a client as `example__<name>`. Reading needs the group `mcp-example-read`, writing `mcp-example-write`.

| Area | Read | Write |
| --- | --- | --- |
| Records | `get_record`, `search_records` | `update_record` |
| Comments | `list_comments`, `get_comment` | |

## What the script renames

It copies the files git tracks and replaces `example`, `Example` and `EXAMPLE` with the system id, `Notes` and `NOTES`, in contents
and in paths. A copy made by hand changes the same places; `grep -rIil example .` lists them.

| Token | Becomes | In |
| --- | --- | --- |
| `example` | `notes` | package `pl.aiwcorpo.mcp.notes` and its directories, Maven groupId, parent `mcp-notes-parent`, jar `mcp-notes.jar`, `server-id: mcp-notes`, the properties `notes.*`, the variables `MCP_NOTES_*`, the groups `mcp-notes-read` and `mcp-notes-write`, `deploy/`, `Dockerfile`, release workflow, `NOTICE`, `.run/` |
| `Example` | `Notes` | class prefix (`NotesTool`, `NotesPolicyTest`), Cedar namespace `Notes::` in the policies, `resource-types.json` and `.run/scope-mapping.json` |
| `EXAMPLE` | `NOTES` | environment variables |

The version is reset to `0.1.0-SNAPSHOT`. The script also writes a `CHANGELOG.md` and a short `README.md` for the new server.
`new-server.sh`, this README and the template's `CHANGELOG.md` are not copied. It fails, and removes the target, if the word
`example` is left anywhere except in `LICENSE`. For a Cedar namespace that reads differently (`NotesApp`), change it only in the
files that name `Notes::`; the groups and the system id do not depend on it.

## Configuration

Required, in `deploy/server.env`:

| Variable | Description |
| --- | --- |
| `MCP_GATEWAY_JWKS_URI` | key set of the gateway: `https://<platform>/.well-known/mcp-jwks.json` |
| `MCP_CONNECT_URL` | portal page where a user connects an account: `https://<platform>/connections?system={system}` |
| `MCP_EXAMPLE_URL` | API address of the system, `https` outside the `dev` profile |

Optional settings (own certificate, Redis for several instances, accepted credential types) are commented in the same file.

## Build

```bash
export JAVA_HOME=<path to JDK 25>
./mvnw -B verify
```

The jar is `mcp-app/target/mcp-example.jar`. Tests need neither Docker nor a platform. Working on the template:
[CONTRIBUTING.md](CONTRIBUTING.md). `new-server.sh` is tested in the `mcp-e2e` repository (`tests/new-server-test.sh`, which
expects this template in `../mcp-server-template`).

## More

- [deploy/README.md](deploy/README.md): installation of a server
- [CHANGELOG.md](CHANGELOG.md), [SECURITY.md](SECURITY.md)

Apache License 2.0.
