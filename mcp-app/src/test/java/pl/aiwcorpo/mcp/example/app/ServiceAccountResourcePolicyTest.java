package pl.aiwcorpo.mcp.example.app;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.aiwcorpo.mcp.example.app.ServerTestSupport.BACKEND;
import static pl.aiwcorpo.mcp.example.app.ServerTestSupport.GATEWAY;
import static pl.aiwcorpo.mcp.example.app.ServerTestSupport.READ_GROUP;
import static pl.aiwcorpo.mcp.example.app.ServerTestSupport.call;
import static pl.aiwcorpo.mcp.example.app.ServerTestSupport.text;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import pl.aiwcorpo.mcp.platform.security.BackendCredential;

/**
 * The resource policy of {@code get_record} with the service account, which sees every record of the backend: a caller
 * without a grant on the project must not tell an existing record from a missing one.
 *
 * <p>Not a subclass of {@link ServerTestSupport}: Spring applies the support class's dynamic properties after those of a
 * subclass, so the accepted credential type could not be changed there.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class ServiceAccountResourcePolicyTest {

    private static final String ACCEPTED_TYPES = "aiwcorpo.mcp.platform.manifest.accepted-credential-types";
    private static final String SECRET = "Bearer example-service-account-secret";
    private static final String NOT_FOUND_OR_DENIED = "\"error\":\"not_found_or_denied\"";

    @DynamicPropertySource
    static void serviceAccount(DynamicPropertyRegistry registry) {
        ServerTestSupport.environment((name, value) -> {
            if (!ACCEPTED_TYPES.equals(name)) {
                registry.add(name, value);
            }
        });
        registry.add(ACCEPTED_TYPES, BackendCredential.Type.SERVICE_ACCOUNT::wireName);
    }

    @LocalServerPort
    int port;

    @BeforeEach
    void resetBackend() {
        BACKEND.reset();
    }

    @Test
    void aCallerGrantedTheProjectReadsTheRecord() {
        try (McpSyncClient client = client(List.of(READ_GROUP, SampleScope.OPEN_PROJECT_GROUP))) {
            McpSchema.CallToolResult result = call(client, "get_record", Map.of("recordId", StubBackend.OPEN_1));

            assertThat(result.isError()).as(text(result)).isFalse();
        }
    }

    @Test
    void anUnclassifiedRecordOutsideTheGrantsLooksLikeAMissingOne() {
        try (McpSyncClient client = client(List.of(READ_GROUP))) {
            McpSchema.CallToolResult unclassified = call(client, "get_record", Map.of("recordId", StubBackend.UNCLASSIFIED));
            List<String> backendCalls = BACKEND.authorizations();
            McpSchema.CallToolResult missing = call(client, "get_record", Map.of("recordId", StubBackend.MISSING));

            assertThat(text(unclassified)).contains(NOT_FOUND_OR_DENIED).isEqualTo(text(missing));
            assertThat(backendCalls).as("refused before the backend is asked").isEmpty();
        }
    }

    private McpSyncClient client(List<String> groups) {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + port)
                .endpoint(ServerTestSupport.ENDPOINT)
                .openConnectionOnStartup(false)
                .httpRequestCustomizer(GATEWAY.asGateway(user -> user.username("jkowalski").groups(groups), SECRET,
                        BackendCredential.Type.SERVICE_ACCOUNT))
                .build();
        McpSyncClient client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(20)).build();
        client.initialize();
        return client;
    }
}
