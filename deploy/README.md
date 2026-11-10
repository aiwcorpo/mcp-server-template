# Install mcp-example

The MCP server of the system `example` as one container. It runs on any host that the platform can reach; the platform (gateway,
broker, portal) is installed separately.

## Requirements

- Docker Engine with Compose v2.20 or newer, 1 GiB of memory.
- The domain of the platform, from its operator. This host must reach `https://<platform>` on port 443.
- The platform must reach this host on port 8443.
- The portal page for connecting an account, and the API address of the system (`https`).

## Install

1. Fill in the three empty values in `server.env`:

    ```bash
    MCP_GATEWAY_JWKS_URI=https://<platform>/.well-known/mcp-jwks.json
    MCP_CONNECT_URL=https://<platform>/connections?system={system}
    MCP_EXAMPLE_URL=https://<system host>
    ```

2. If the platform certificate is signed by a company CA, add that CA to a trust store in `certs/`:

    ```bash
    keytool -importcert -noprompt -alias company-ca -file company-ca.crt \
      -keystore certs/truststore.p12 -storetype PKCS12 -storepass changeit
    chmod 644 certs/truststore.p12
    ```

    Then uncomment the `JAVA_TOOL_OPTIONS` line in `server.env`. The option replaces the JVM trust store, so import every CA
    the server needs, the CA of the system included.

3. Get the image. Pull it, or build it after `./mvnw verify` in the repository:

    ```bash
    docker compose pull
    docker compose build
    ```

    Run one of the two. A server generated from the template has no published image yet, so build.

4. Start the server:

    ```bash
    docker compose up -d
    ```

5. Read the certificate fingerprint:

    ```bash
    docker compose logs mcp-example | grep event=tls_certificate
    ```

    Expected: `event=tls_certificate source=generated fingerprint=sha256:...`

6. Send the platform operator the address `https://<this host>:8443/mcp` and the fingerprint.

## Check that it works

```bash
curl -fsSk https://localhost:8443/actuator/health
```

Expected: `{"status":"UP"...}`. Every other path answers 401 without a token from the gateway.

## Add the server to the platform

The platform operator does this in the portal, Administration, with a fresh login and OTP:

1. Systems: add `example`, adapter `generic-rest`, address `https://<system host>`, credential type `user-secret`, scheme `Bearer`.
   Add `whoami` (the path the portal calls with the token and the field that holds the user name) to check a token when a user
   stores it.
2. Servers, Add server: `https://<this host>:8443/mcp`.
3. Approvals: compare the fingerprint with the one from step 5 and approve the connection, then the policies, then the tools.
4. In the identity provider, put the users in the groups `mcp-example-read` and `mcp-example-write`.
5. Scope mapping, for writes: map a group to a scope such as `Example::Project/ABC`.

Each user then pastes a token of the system in the portal, Connections.

## Common problems

| Symptom | Cause | Fix |
| --- | --- | --- |
| Server stops at start: cannot fetch the key set | The host does not resolve `<platform>` or does not trust its certificate | Check DNS; do step 2 |
| Portal, Add server: `tls_handshake_failed` or `manifest_unreachable` | Port 8443 closed between the platform and this host | Open it to the platform host |
| Tool call: `upstream_unavailable`, log has `SSLHandshakeException` | The trust store is missing the CA of the system, or `JAVA_TOOL_OPTIONS` is commented out | Import the CA, then `docker compose up -d --force-recreate` |
| Server status `certificate_changed` | The state volume was recreated and the server generated a new certificate | Approve the new fingerprint in the portal |
| Server refuses calls with `policies_differ` | A new or changed policy waits for approval | Approve the policies in the portal |

The state volume keeps the certificate between restarts. `docker compose down -v` deletes it.

## Your own certificate

Put a PEM pair or a PKCS12 keystore in `certs/` and set `MCP_TLS_CERT` and `MCP_TLS_KEY`, or `MCP_TLS_KEYSTORE` and
`MCP_TLS_STORE_PASSWORD`, in `server.env`. A certificate from a CA the gateway trusts, for the host name in the address, is
trusted without pinning, and a renewal needs no new approval.

## Several instances

Instances behind one address need the same values in `server.env`:

| Setting | Why |
| --- | --- |
| `MCP_REDIS_HOST` and the other `MCP_REDIS_*` | one-time token ids shared between instances |
| `MCP_PAGING_CURSOR_KEY` (`openssl rand -base64 32`) | a cursor from one instance works on another |
| `MCP_TLS_CERT` and `MCP_TLS_KEY` | the gateway sees one fingerprint |

Compose runs one instance; Redis and the balancer are yours.

## Closed network

Copy the image to your registry and set it in a `.env` file next to `compose.yaml`:

```bash
MCP_REGISTRY=artifactory.corp.test/mcp
```

## Upgrade

Set `MCP_EXAMPLE_VERSION` in `.env` and run `docker compose up -d`. The state volume keeps the certificate, so the connection
stays approved. A version with other tools or policies waits in the portal until the operator approves it.

## Virtual machine without Docker

`systemd/` has a unit and its environment file for the jar (`mcp-app/target/mcp-example.jar` after `./mvnw verify`):

```bash
sudo useradd --system --no-create-home --shell /usr/sbin/nologin mcp
sudo install -d -o root -g mcp -m 0750 /opt/mcp-example /etc/mcp-example
sudo install -o root -g mcp -m 0640 mcp-example.jar /opt/mcp-example/mcp-example.jar
sudo install -o root -g mcp -m 0640 systemd/mcp-example.env.template /etc/mcp-example/mcp-example.env
sudo install -m 0644 systemd/mcp-example.service /etc/systemd/system/
```

Fill in `/etc/mcp-example/mcp-example.env` as in step 1, then:

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now mcp-example
journalctl -u mcp-example | grep event=tls_certificate
```

To update, keep the old jar as `.bak`, replace it and run `sudo systemctl restart mcp-example`.
