# Install the server

The server of the system `example` on one Linux host, behind the gateway. Compose is the default; a unit for a virtual machine without
Docker is at the end. The gateway, the credential broker and the identity provider run elsewhere. This directory is for one server: a
second server is its own repository and its own directory.

## Prerequisites

- Linux, Docker Engine with Compose v2.20 or newer, 1 GiB of memory.
- From your internal CA: a certificate for the server with CN `mcp-example`, SAN `mcp-example.internal`, and both `serverAuth` and
  `clientAuth` in the extended key usage (the server answers the gateway as a server and asks it for the policy data as a client). The
  gateway pins the SAN and the CN, so the names are not configurable. Plus the CA certificate.
- The addresses of the gateway's internal TLS port (8443), of the portal and of the backend system, and a Redis with an account for the
  server (ACL user `mcp-example`, keys `jti:mcp-example:*`, command `SET`).

## Install

1. Put two files in `certs/`:

    | File | What |
    | --- | --- |
    | `keystore.p12` | the server certificate and its key |
    | `truststore.p12` | the internal CA certificate |

    Both stores use one password. A request for the certificate and the stores, from PEM files:

    ```bash
    openssl req -new -newkey rsa:3072 -nodes -keyout server.key -subj "/CN=mcp-example" -out server.csr
    printf 'subjectAltName = DNS:mcp-example.internal\nextendedKeyUsage = serverAuth, clientAuth\n' > server.ext   # for your CA to apply
    openssl pkcs12 -export -inkey server.key -in server.crt -certfile ca.crt -name mcp-example -out certs/keystore.p12
    keytool -importcert -noprompt -alias ca -file ca.crt -keystore certs/truststore.p12 -storetype PKCS12
    ```

2. Fill in the empty values in `server.env`.
3. Get the image: `docker compose pull`, or after `./mvnw verify` in the repository `docker compose build`.
4. Start:

    ```bash
    docker compose up -d
    ```

The server does not start with a missing setting and says which. Without data from the gateway (it is down, or this server is not
registered there yet) it starts and refuses every call as `upstream_unavailable` until the data arrives; it asks again every few seconds.

## Add the server to the platform

Nothing restarts: the gateway and the broker read the change within a few seconds. The portal (Administration) does the same as the
calls below.

1. Broker, system: `PUT /v1/admin/systems/example` (`baseUrl`, `adapter`, `supportedTypes`, `defaultType`, `secretScheme`, OAuth client where
   the type needs one).

    ```bash
    curl --cert bff.crt --key bff.key --cacert ca.crt -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
      -H 'If-None-Match: *' -X PUT https://mcp-credential-broker.internal:8443/v1/admin/systems/example \
      -d '{"baseUrl":"https://backend.corp.internal","adapter":"generic-rest","supportedTypes":["user-secret"],"defaultType":"user-secret","secretScheme":"Bearer"}'
    ```

2. Gateway, server: `POST /admin/v1/servers`. The `serverId` is `mcp-example`; the credential agrees with the broker and with
   `MCP_ACCEPTED_CREDENTIAL_TYPES`.

    ```bash
    curl --cert bff.crt --key bff.key --cacert ca.crt -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
      https://mcp-gateway.internal:8443/admin/v1/servers -d '{"serverId":"mcp-example","system":"example",
      "internalUrl":"https://mcp-example.internal:8443/mcp","credential":{"required":true,"acceptedTypes":["user-secret"],"type":"user-secret"}}'
    ```

3. Deploy the server (this directory). It fetches its policy data at once.
4. The pipeline registers the manifest of the reviewed commit (`mcp-app/target/mcp-manifest.json`, the artifact of the build) with the
   certificate `mcp-registry-ci-mcp-example`: `PUT /admin/v1/registry/servers/mcp-example`. Tools that only read are approved by that
   call. Until the policies are approved the server has no tools for anyone.
5. An administrator approves the policies (`POST /admin/v1/registry/servers/mcp-example/policies/approve` with the pending
   `policiesHash`) and the `UPDATE` and `DESTRUCTIVE` tools, in the portal. Every later change of a policy or a resource type puts the
   server back in `pending_approval` until it is approved again.
6. In the identity provider, put the users in the groups `mcp-example-read`, `mcp-example-write` and `mcp-example-destructive`. Which
   group may change which resources is the scope mapping (Administration, Scope mapping): `mcp-example-proj-ABC` to
   `Example::Project/ABC`, for instance.

## Remove the server

1. Kill switch, if it must stop at once: `PUT /admin/v1/registry/servers/mcp-example/kill-switch` with a reason. Calls are refused within
   seconds.
2. Delete the server: `DELETE /admin/v1/servers/mcp-example`. Its tools leave every `tools/list`; its approvals, policies and the entries
   of the scope mapping for its types go with it.
3. Delete the system: `DELETE /v1/admin/systems/example` in the broker (the connections of users and the client secret go with it).
4. Stop the container: `docker compose down`.

Nothing else restarts.

## Test stack with the stub backend

`docker compose --profile stub up -d` also starts `mcp-stub-backend` (build the repository first). In `server.env` set
`MCP_EXAMPLE_URL=http://stub-backend:18081` and `MCP_EXAMPLE_ALLOW_PLAIN_HTTP=true`; in the broker the system points at that address
with an `http` base URL, which only a broker in its dev profile accepts. The stub takes any credential.

## Ports

| Port | For |
| --- | --- |
| 8443 | mTLS with the server certificate: the gateway's calls. `MCP_EXAMPLE_BIND` limits the address. |

## Closed network

Copy the image to your registry and point Compose at it in a `.env` file next to `compose.yaml`:

```bash
MCP_REGISTRY=artifactory.corp.test/mcp
```

## Upgrade

Change `MCP_EXAMPLE_VERSION` in `.env` (or the default in `compose.yaml`) and run `docker compose up -d`. A new version with other
policies waits in `pending_approval` after the pipeline registers it, as above.

## Virtual machine without Docker

`systemd/` has a unit and its environment file for the jar, `scripts/` the installation; step by step in the
[VM installation guide](https://github.com/aiwcorpo/mcp-docs/blob/main/docs/operator/install-vm.md) of `mcp-docs`. The environment
variables are the ones of `server.env`, with the stores at `/etc/mcp-example/tls`.

```bash
sudo scripts/install.sh mcp-example.jar     # account, directories, unit; then fill /etc/mcp-example/mcp-example.env
sudo systemctl enable --now mcp-example
scripts/healthcheck.sh https://mcp-example.internal:8443 ca.crt probe.crt probe.key   # health over mTLS
sudo scripts/update.sh new-mcp-example.jar  # replaces the jar, keeps the old one for rollback
```
