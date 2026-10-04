package pl.aiwcorpo.mcp.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import pl.aiwcorpo.mcp.integration.InMemoryExampleResourceAdapter;
import pl.aiwcorpo.mcp.platform.guard.CallerIdentity;
import pl.aiwcorpo.mcp.platform.guard.ConfirmationCodeSender;

/**
 * The seed's three tools, called the way a client calls them — over HTTP, through the platform's
 * guard — against the static mock backend, the local policy ({@code guard-policy-local.yml}) and an
 * in-test OIDC provider that issues real, signed access tokens.
 *
 * <p>This is the scenario to keep green when you turn the seed into a real server: one tool per
 * risk level, one caller per clearance, and the destructive call that only runs once the user has
 * supplied the code.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@Import(GuardedToolsTest.CodeCapture.class)
class GuardedToolsTest {

    /** Stands in for the identity provider; the server validates its tokens for real. */
    private static final TestIdentityProvider IDP = TestIdentityProvider.start();

    @DynamicPropertySource
    static void identityProvider(DynamicPropertyRegistry registry) {
        registry.add("aiwcorpo.mcp.platform.guard.oidc.issuer-uri", IDP::issuer);
        registry.add("aiwcorpo.mcp.platform.guard.oidc.audiences", () -> TestIdentityProvider.AUDIENCE);
    }

    @LocalServerPort
    int port;

    @Autowired
    InMemoryExampleResourceAdapter backend;

    @Autowired
    AtomicReference<String> lastCode;

    /** Replaces the log-only demo sender, so the test can read the code "the user received". */
    @TestConfiguration
    static class CodeCapture {

        @Bean
        AtomicReference<String> lastCode() {
            return new AtomicReference<>();
        }

        @Bean
        ConfirmationCodeSender confirmationCodeSender(AtomicReference<String> lastCode) {
            return (CallerIdentity identity, String tool, String summary, String code, Instant expiresAt) ->
                    lastCode.set(code);
        }
    }

    @BeforeEach
    void restoreDemoData() {
        backend.seed();
        lastCode.set(null);
    }

    @Test
    void anyoneMayRead() {
        try (McpSyncClient anonymous = connect(null)) {
            assertThat(text(call(anonymous, "example_lookup", Map.of("id", "demo-1")))).contains("Quarterly report");
        }
    }

    @Test
    void renamingNeedsUpdateClearance() {
        try (McpSyncClient anna = connect(IDP.token("anna")); McpSyncClient ola = connect(IDP.token("ola", "mcp-operator"))) {
            McpSchema.CallToolResult refused = call(anna, "example_rename", Map.of("id", "demo-1", "name", "Hacked"));
            assertThat(refused.isError()).isTrue();
            assertThat(text(refused)).contains("access_denied");
            assertThat(backend.findById("demo-1").orElseThrow().name()).isEqualTo("Quarterly report");

            assertThat(text(call(ola, "example_rename", Map.of("id", "demo-1", "name", "Q3 report"))))
                    .contains("Q3 report");
            assertThat(backend.findById("demo-1").orElseThrow().name()).isEqualTo("Q3 report");
        }
    }

    @Test
    void purgingNeedsDestructiveClearanceAndTheUsersCode() {
        try (McpSyncClient ola = connect(IDP.token("ola", "mcp-operator")); McpSyncClient karol = connect(IDP.token("karol", "mcp-admin"))) {
            assertThat(text(call(ola, "example_purge", Map.of("dataset", "records")))).contains("access_denied");
            assertThat(lastCode.get()).as("no code is sent for a call that is not allowed anyway").isNull();

            McpSchema.CallToolResult first = call(karol, "example_purge", Map.of("dataset", "records"));
            assertThat(first.isError()).isTrue();
            assertThat(text(first)).contains("confirmation_required");
            assertThat(backend.findById("demo-1")).as("nothing is removed before the user confirms").isPresent();

            McpSchema.CallToolResult second = call(karol, "example_purge",
                    Map.of("dataset", "records", "confirmation_code", lastCode.get()));
            assertThat(text(second)).contains("Purged 3 record(s)");
            assertThat(backend.findById("demo-1")).isEmpty();
        }
    }

    private McpSyncClient connect(String token) {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                .builder("http://127.0.0.1:" + port)
                .endpoint("/mcp/example")
                .httpRequestCustomizer((request, method, uri, body, context) -> {
                    if (token != null) {
                        request.header("Authorization", "Bearer " + token);
                    }
                })
                .build();
        McpSyncClient client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(20)).build();
        client.initialize();
        return client;
    }

    private static McpSchema.CallToolResult call(McpSyncClient client, String tool, Map<String, Object> arguments) {
        return client.callTool(McpSchema.CallToolRequest.builder(tool).arguments(arguments).build());
    }

    private static String text(McpSchema.CallToolResult result) {
        StringBuilder text = new StringBuilder();
        for (McpSchema.Content content : result.content()) {
            if (content instanceof McpSchema.TextContent t) {
                text.append(t.text());
            }
        }
        return text.toString();
    }
}
