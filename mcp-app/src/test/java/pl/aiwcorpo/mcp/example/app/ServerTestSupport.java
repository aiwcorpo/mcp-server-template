package pl.aiwcorpo.mcp.example.app;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import pl.aiwcorpo.mcp.platform.security.BackendCredential;
import pl.aiwcorpo.mcp.platform.test.TestGateway;
import tools.jackson.databind.json.JsonMapper;

/**
 * The whole server over HTTP, with TestGateway in place of the gateway and {@link StubBackend} in place of
 * the backend system. Shared by the test classes so they run against one application context.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
abstract class ServerTestSupport {

    static final String SYSTEM = "example";

    /**
     * True only for a system outside the contracts' SystemId (the template's placeholder, a server generated
     * with new-server.sh --trial). new-server.sh sets it to false for a real system, and then the system must be
     * on the list (checked in {@link #contractSystemIsKnown()}).
     */
    static final boolean TRIAL_SYSTEM = true;

    static final String SERVER_ID = "mcp-" + SYSTEM;
    static final String READ_GROUP = SERVER_ID + "-read";
    static final String WRITE_GROUP = SERVER_ID + "-write";
    static final String ENDPOINT = "/mcp";
    static final Set<String> CONTRACT_SYSTEMS = contractSystems();

    static final TestGateway GATEWAY = TestGateway.start(SERVER_ID);
    static final StubBackend BACKEND = StubBackend.start();

    @DynamicPropertySource
    static void environment(DynamicPropertyRegistry registry) {
        GATEWAY.serverProperties().forEach((name, value) -> registry.add(name, () -> value));
        // TestGateway accepts user-secret and user-oauth; the server of a system with another type says so in its own
        // configuration (the first accepted type is the default of the system).
        registry.add("aiwcorpo.mcp.platform.manifest.accepted-credential-types", () -> SampleScope.CREDENTIAL_TYPE.wireName());
        registry.add("example.backend.base-url", BACKEND::baseUrl);
        // Short limits so the timeout and size tests run quickly; StubBackend's slow and huge records exceed them.
        registry.add("example.backend.read-timeout", () -> "1s");
        registry.add("example.backend.max-response-size", () -> "64KB");
    }

    @LocalServerPort
    int port;

    @org.springframework.beans.factory.annotation.Autowired
    pl.aiwcorpo.mcp.platform.capability.BackendCapabilities capabilities;

    /** Writes are closed until the version probe has answered, so a test that writes waits for its first answer. */
    void awaitProbe() throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (capabilities.of("example").state() == pl.aiwcorpo.mcp.platform.capability.Capabilities.State.UNAVAILABLE
                && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        assertThat(capabilities.of("example").state()).as("the probe of the stub backend")
                .isNotEqualTo(pl.aiwcorpo.mcp.platform.capability.Capabilities.State.UNAVAILABLE);
    }

    String baseUrl() {
        return "http://127.0.0.1:" + port;
    }

    /** An MCP client of a user in the read group, reaching the server the way the gateway does, with {@code credential} as C3 header. */
    McpSyncClient clientWithCredential(String credential) {
        return clientWithToken(ServerTestSupport::reader, credential);
    }

    /** The caller of most tests: a member of the read group of the system. */
    static void reader(TestGateway.Token user) {
        user.username("jkowalski").groups(Stream.concat(Stream.of(READ_GROUP), SampleScope.EXTRA_GROUPS.stream()).toList());
    }

    /** A caller who may also change records: the read group and the write group of the system. */
    static void writer(TestGateway.Token user) {
        user.username("jkowalski").groups(Stream.of(Stream.of(READ_GROUP, WRITE_GROUP), SampleScope.EXTRA_GROUPS.stream(), SampleScope.WRITE_GRANTS.stream())
                .flatMap(group -> group).toList());
    }

    /** As {@link #clientWithCredential}, with the caller's token shaped by {@code user} (claims of a link call). */
    McpSyncClient clientWithToken(Consumer<TestGateway.Token> user, String credential) {
        return clientWithToken(user, credential, SampleScope.CREDENTIAL_TYPE);
    }

    /** As above with the credential type the gateway puts in {@code X-MCP-Credential-Type}. */
    McpSyncClient clientWithToken(Consumer<TestGateway.Token> user, String credential, BackendCredential.Type type) {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(baseUrl())
                .endpoint(ENDPOINT)
                .openConnectionOnStartup(false)
                .httpRequestCustomizer(GATEWAY.asGateway(user, credential, type))
                .build();
        McpSyncClient client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(20)).build();
        client.initialize();
        return client;
    }

    /**
     * The starter sends only C10 errors valid against {@code tool-error.schema.json}, whose {@code system} is the
     * closed {@code SystemId} list of the contracts. A trial system (outside the list) gets {@code internal_error}
     * instead; a real system must get the full error with the portal link, never {@code internal_error}.
     */
    /** A refusal by the schema or by the tool: the model sent something the tool does not accept. */
    static void assertInvalidArgument(McpSchema.CallToolResult result) {
        assertThat(result.isError()).isTrue();
        assertThat(text(result)).containsAnyOf("\"error\":\"invalid_argument\"", "input validation failed");
    }

    static void assertCredentialRequired(McpSchema.CallToolResult result) {
        assertThat(result.isError()).isTrue();
        if (TRIAL_SYSTEM) {
            assertThat(CONTRACT_SYSTEMS).as("a trial system is outside SystemId").doesNotContain(SYSTEM);
            assertThat(text(result)).contains("\"error\":\"internal_error\"");
        } else {
            assertThat(text(result)).contains("\"error\":\"credential_required\"", "\"system\":\"" + SYSTEM + "\"",
                    "\"connectUrl\":\"http://localhost:18090/connections?system=" + SYSTEM + "\"");
        }
    }

    /**
     * The error text for a failure of the system while the resource is resolved. For a service account (Service Desk)
     * the platform answers {@code not_found_or_denied} to every such failure: a fault of the system must not tell
     * whether the ticket exists.
     */
    static String resolverFault(String code) {
        return "\"error\":\"" + (SampleScope.ONLY_GRANTED_PROJECTS ? "not_found_or_denied" : code) + "\"";
    }

    /** A real server's system is one of the contracts'; a trial one is not. */
    @Test
    void contractSystemIsKnown() {
        assertThat(CONTRACT_SYSTEMS.contains(SYSTEM)).isEqualTo(!TRIAL_SYSTEM);
    }

    private static Set<String> contractSystems() {
        try (InputStream schema = ServerTestSupport.class.getClassLoader()
                .getResourceAsStream("mcp-contracts/schemas/common.schema.json")) {
            Set<String> systems = new HashSet<>();
            JsonMapper.builder().build().readTree(schema).at("/$defs/SystemId/enum")
                    .forEach(system -> systems.add(system.asString()));
            return Set.copyOf(systems);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    static McpSchema.CallToolResult call(McpSyncClient client, String tool, Map<String, Object> arguments) {
        return client.callTool(McpSchema.CallToolRequest.builder(tool).arguments(arguments).build());
    }

    static String text(McpSchema.CallToolResult result) {
        StringBuilder text = new StringBuilder();
        result.content().forEach(content -> {
            if (content instanceof McpSchema.TextContent t) {
                text.append(t.text());
            }
        });
        return text.toString();
    }
}
