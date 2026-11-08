# Install the server

The server of the system `example` on one Linux host, behind the gateway. Compose is the default; a unit for a virtual machine without
Docker is at the end. The gateway, the credential broker and the identity provider run elsewhere, and the host of the server may be
another machine than the platform. This directory is for one server: a second server is its own repository and its own directory.

The server author starts the server. The platform operator enters its address in the portal and approves the connection. Nobody issues a
certificate for the server, creates a token or registers anything from a pipeline: the gateway connects to the address, reads the
certificate, pulls the manifest and pushes the policy data itself.

## Prerequisites

- Linux, Docker Engine with Compose v2.20 or newer, 1 GiB of memory.
- The public key set of the gateway, `https://<domain of the platform>/.well-known/mcp-jwks.json` (the operator tells you the domain). The
  host must reach that domain on port 443, and the platform must reach the port of the server (8443 by default).
- The addresses of the portal page for connecting an account and of the backend system.
- A certificate is optional. Without one the server generates a self-signed certificate on its first start and keeps it in the state
  volume. With your own (PEM files or a PKCS12 keystore, from any CA, signed or not) put it in `certs/`; see `server.env`.

## Install

1. Fill in the empty values in `server.env`: `MCP_GATEWAY_JWKS_URI`, `MCP_CONNECT_URL`, `MCP_EXAMPLE_URL`. The other lines are optional and
   commented out.
2. Get the image: `docker compose pull`, or after `./mvnw verify` in the repository `docker compose build`.
3. Start:

    ```bash
    docker compose up -d
    docker compose logs mcp-example | grep event=tls_certificate
    ```

    The server does not start with a missing setting and says which. The log line has the fingerprint of the certificate it serves:

    ```
    event=tls_certificate source=generated fingerprint=sha256:9f2c...e1 detail="..."
    ```

    Keep it: the operator compares it with the one the portal shows before approving the connection. `source` is `generated`, `pem` or `keystore`.
    The certificate is generated once and reused at every start, as long as the state volume (`state`, mounted at `/var/lib/mcp/state`) stays;
    a new volume is a new certificate and a new fingerprint to approve. With `docker compose down -v` the volume is gone.

4. Give the operator the address: `https://<host>:8443/mcp`, with the host name or address the platform reaches, and the fingerprint.

Until the gateway has pushed policy data (a fresh start, or a restart), the server answers `tools/call` with `409 policy_data_stale`, which the
gateway handles by pushing and repeating the call. The gateway pushes after every change and every minute, so the state lasts seconds.

## Add the server to the platform

This part is for the operator of the platform. Nothing restarts: the gateway and the broker read the change within a few seconds. The portal
(Administration) does the same as the calls.

1. Broker, system: `PUT /v1/admin/systems/example` (`baseUrl`, `adapter`, `supportedTypes`, `defaultType`, `secretScheme`, OAuth client where
   the type needs one).

    ```bash
    curl --cert bff.crt --key bff.key --cacert ca.crt -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
      -H 'If-None-Match: *' -X PUT https://mcp-credential-broker.internal:8443/v1/admin/systems/example \
      -d '{"baseUrl":"https://backend.corp.internal","adapter":"generic-rest","supportedTypes":["user-secret"],"defaultType":"user-secret","secretScheme":"Bearer","whoami":{"path":"/api/me","usernameField":"username"}}'
    ```

    `whoami` is the path the broker calls with the user's token and the field of the answer that holds the user name: the token is then
    checked when it is stored and the backend user is recorded. Without `whoami` the broker stores the token unverified.

2. Portal, Administration, "Add server": the address `https://<host>:8443/mcp` (or `POST /admin/v1/servers` with `{"internalUrl": "..."}`, with
   fresh MFA). The gateway connects, reads the certificate, pulls the manifest and shows a card with the system, the certificate (subject,
   issuer, names, validity, fingerprint) and the tools and policies it found. The `serverId` is `mcp-example`, derived from the manifest; the credential
   agrees with the broker and with `MCP_ACCEPTED_CREDENTIAL_TYPES`. A server whose address is loopback or link-local is refused, and `http` needs
   `allowPlainHttp`, which only an administrator may set (for a server inside the Docker network of the platform, started with `MCP_PLAIN_HTTP=true`).
3. Approve the connection (fresh MFA), after comparing the fingerprint on the card with the one in the server log:
    - a certificate that a CA trusted by the gateway signed, for the name of the address, is trusted as `ca`, and a renewal needs nothing;
    - any other certificate, a self-signed one included, is pinned (`pinned`) by its fingerprint: when the server later presents another
      certificate it becomes unreachable, and the portal shows the new fingerprint for approval.
4. Approve the policies (`POST /admin/v1/registry/servers/mcp-example/policies/approve` with the pending `policiesHash`) and the tools, in the portal.
   Every tool waits for an administrator, the read-only ones too. Every later change of a policy, a resource type or a tool puts the server back in
   `pending_approval` until it is approved again.
5. In the identity provider, put the users in the groups `mcp-example-read`, `mcp-example-write` and `mcp-example-destructive`. Which
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

## Ports

| Port | For |
| --- | --- |
| 8443 | TLS, no client certificate: the gateway's calls, with an internal JWT on every path but `/actuator/health`. `MCP_EXAMPLE_BIND` limits the address. |

The host also needs outbound access to the public address of the gateway (443, the key set), to the backend system and to the image registry.

## Several instances

Behind a balancer the instances share one address, so they need what one instance keeps in its own memory or state directory: Redis for the
one-time `jti` (`MCP_REDIS_HOST` and the lines under it; without it a token could be used once on each instance), the same
`MCP_PAGING_CURSOR_KEY` (otherwise a cursor from one instance is refused by another) and the same certificate (`MCP_TLS_CERT` and `MCP_TLS_KEY`,
otherwise each instance generates its own and the gateway sees the fingerprint change from one call to the next). Compose runs one instance; Redis is
yours.

## Closed network

Copy the image to your registry and point Compose at it in a `.env` file next to `compose.yaml`:

```bash
MCP_REGISTRY=artifactory.corp.test/mcp
```

## Upgrade

Change `MCP_EXAMPLE_VERSION` in `.env` (or the default in `compose.yaml`) and run `docker compose up -d`. The state volume keeps the certificate, so
the connection stays approved. A new version with other policies or tools waits in `pending_approval`: the gateway pulls the manifest within a minute
and the administrator approves the change, as above.

## Virtual machine without Docker

`systemd/` has a unit and its environment file for the jar; step by step in the
[VM installation guide](https://github.com/aiwcorpo/mcp-docs/blob/main/docs/operator/install-vm.md) of `mcp-docs`. The environment
variables are the ones of `server.env`; the unit's `StateDirectory=` keeps the certificate in `/var/lib/mcp-example`.

```bash
sudo useradd --system --no-create-home --shell /usr/sbin/nologin mcp
sudo install -d -o root -g mcp -m 0750 /opt/mcp-example /etc/mcp-example
sudo install -o root -g mcp -m 0640 mcp-example.jar /opt/mcp-example/mcp-example.jar
sudo install -o root -g mcp -m 0640 systemd/mcp-example.env.template /etc/mcp-example/mcp-example.env   # then fill it in
sudo install -m 0644 systemd/mcp-example.service /etc/systemd/system/
sudo systemctl daemon-reload && sudo systemctl enable --now mcp-example
journalctl -u mcp-example | grep event=tls_certificate
```

Health (any certificate; no token is needed for this path):

```bash
curl -fsSk https://localhost:8443/actuator/health
```

Update: keep the old jar for a rollback, replace it and restart.

```bash
sudo cp -a /opt/mcp-example/mcp-example.jar /opt/mcp-example/mcp-example.jar.bak
sudo install -o root -g mcp -m 0640 new-mcp-example.jar /opt/mcp-example/mcp-example.jar
sudo systemctl restart mcp-example
```
