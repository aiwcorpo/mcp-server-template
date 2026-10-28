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

    /**
     * The sample policy bundles of the contracts permit the five real systems only. A trial system therefore runs
     * its tests under the identity of Jira (server id, groups, credential system); the code under test does not know:
     * it takes the system from the server id.
     */
    static final String POLICY_SYSTEM = TRIAL_SYSTEM ? "jira" : SYSTEM;
    static final String SERVER_ID = "mcp-" + POLICY_SYSTEM;
    static final String READ_GROUP = "mcp-" + POLICY_SYSTEM + "-read";
    static final String ENDPOINT = "/mcp";
    static final Set<String> CONTRACT_SYSTEMS = contractSystems();

    static final TestGateway GATEWAY = TestGateway.start(SERVER_ID);
    static final StubBackend BACKEND = StubBackend.start();

    @DynamicPropertySource
    static void environment(DynamicPropertyRegistry registry) {
        GATEWAY.serverProperties().forEach((name, value) -> registry.add(name, () -> value));
        registry.add("example.backend.base-url", BACKEND::baseUrl);
        // Short limits so the timeout and size tests run quickly; StubBackend's slow and huge records exceed them.
        registry.add("example.backend.read-timeout", () -> "1s");
        registry.add("example.backend.max-response-size", () -> "64KB");
    }

    @LocalServerPort
    int port;

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

    /** As {@link #clientWithCredential}, with the caller's token shaped by {@code user} (claims of a link call). */
    McpSyncClient clientWithToken(Consumer<TestGateway.Token> user, String credential) {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(baseUrl())
                .endpoint(ENDPOINT)
                .openConnectionOnStartup(false)
                .httpRequestCustomizer(GATEWAY.asGateway(user, credential))
                .build();
        McpSyncClient client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(20)).build();
        client.initialize();
        return client;
    }

    /** The error a client gets when the user has not connected the account: C10 {@code credential_required} with the portal link. */
    static void assertCredentialRequired(McpSchema.CallToolResult result) {
        assertThat(result.isError()).isTrue();
        assertThat(text(result)).contains("\"error\":\"credential_required\"", "\"system\":\"" + POLICY_SYSTEM + "\"",
                "\"connectUrl\":\"http://localhost:18090/connections?system=" + POLICY_SYSTEM + "\"");
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
