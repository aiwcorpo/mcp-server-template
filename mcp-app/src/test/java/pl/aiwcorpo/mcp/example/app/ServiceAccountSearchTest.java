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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import pl.aiwcorpo.mcp.platform.security.BackendCredential;

/**
 * {@code search_records} with the service account, which sees every project of the backend: the query must stay inside the
 * projects the caller is granted.
 *
 * <p>Not a subclass of {@link ServerTestSupport}: Spring applies the support class's dynamic properties after those of a
 * subclass, so the accepted credential type could not be changed there.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class ServiceAccountSearchTest {

    private static final String ACCEPTED_TYPES = "aiwcorpo.mcp.platform.manifest.accepted-credential-types";
    private static final String SECRET = "Bearer example-service-account-secret";

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

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aProjectOutsideTheGrantsIsNotSearched(boolean grantedAnotherProject) {
        List<String> groups = grantedAnotherProject ? List.of(READ_GROUP, SampleScope.OPEN_PROJECT_GROUP) : List.of(READ_GROUP);
        try (McpSyncClient client = client(groups)) {
            McpSchema.CallToolResult result = call(client, "search_records",
                    Map.of("project", SampleScope.OTHER_PROJECT, "text", "report"));

            assertThat(result.isError()).as(text(result)).isFalse();
            assertThat(text(result)).doesNotContain(StubBackend.OTHER_7);
            assertThat(BACKEND.searches()).isEmpty();
            assertThat(BACKEND.authorizations()).isEmpty();
        }
    }

    @Test
    void aGrantedProjectIsSearchedWithinTheGrants() {
        try (McpSyncClient client = client(List.of(READ_GROUP, SampleScope.OPEN_PROJECT_GROUP))) {
            McpSchema.CallToolResult result = call(client, "search_records",
                    Map.of("project", SampleScope.OPEN_PROJECT, "text", "report"));

            assertThat(result.isError()).as(text(result)).isFalse();
            assertThat(text(result)).contains(StubBackend.OPEN_1);
            assertThat(BACKEND.searches()).singleElement().asString()
                    .contains("project in (\"" + SampleScope.OPEN_PROJECT + "\") AND project in (\"" + SampleScope.OPEN_PROJECT + "\")");
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
