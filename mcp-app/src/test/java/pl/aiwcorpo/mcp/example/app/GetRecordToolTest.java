package pl.aiwcorpo.mcp.example.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import pl.aiwcorpo.mcp.example.core.domain.ExampleResourceModel;
import pl.aiwcorpo.mcp.example.core.port.out.ExampleResourcePort;
import pl.aiwcorpo.mcp.platform.security.CredentialRequiredException;

/**
 * {@code get_record} through the test gateway: the C3 credential reaches the backend, errors are C10. The policy side
 * (restricted projects, groups, links) is in {@link ResourcePolicyTest}.
 */
@ExtendWith(OutputCaptureExtension.class)
class GetRecordToolTest extends ServerTestSupport {

    private static final String PAT = "Bearer example-pat-of-jkowalski-7f3a";

    @Autowired
    ExampleResourcePort resourcePort;

    @BeforeEach
    void resetBackend() {
        BACKEND.reset();
    }

    @Test
    void readsTheRecordWithTheCredentialFromTheGateway() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            McpSchema.CallToolResult result = call(client, "get_record", Map.of("recordId", StubBackend.OPEN_1));

            assertThat(result.isError()).isFalse();
            assertThat(text(result))
                    .contains("\"id\":\"" + StubBackend.OPEN_1 + "\"", "\"project\":\"" + SampleScope.OPEN_PROJECT + "\"", "\"text\":\"Quarterly report\"", "\"kind\":\"untrusted-data\"")
                    .as("fields outside the domain record stay in the adapter").doesNotContain("internalNotes");
            assertThat(BACKEND.authorizations()).as("the record the policy resolved is the one returned: one backend call")
                    .containsExactly(PAT);
        }
    }

    @Test
    void aRecordWithoutStatusIsAnOkResultWithNull() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            McpSchema.CallToolResult result = call(client, "get_record", Map.of("recordId", StubBackend.NO_STATUS));

            assertThat(result.isError()).isFalse();
            assertThat(text(result)).contains("\"id\":\"" + StubBackend.NO_STATUS + "\"", "\"text\":\"Sparse record\"")
                    .contains("\"project\":\"" + SampleScope.OPEN_PROJECT + "\"", "\"status\":null");
        }
    }

    @Test
    void aRecordWithoutProjectIsTheBackendsFaultAndNotServed() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            McpSchema.CallToolResult result = call(client, "get_record", Map.of("recordId", StubBackend.NO_PROJECT));

            assertThat(result.isError()).isTrue();
            assertThat(text(result)).contains("\"error\":\"internal_error\"");
        }
    }

    @Test
    void acceptsTheVirtualServerClaimsOfACallThroughALink() {
        try (McpSyncClient client = clientWithToken(
                user -> { reader(user); user.virtualServer("team-reports", Map.of()); }, PAT)) {
            McpSchema.CallToolResult result = call(client, "get_record", Map.of("recordId", StubBackend.OPEN_1));

            assertThat(result.isError()).isFalse();
            assertThat(text(result)).contains("\"id\":\"" + StubBackend.OPEN_1 + "\"");
        }
    }

    @Test
    void isListedAsReadOnly() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            McpSchema.Tool tool = client.listTools().tools().stream()
                    .filter(t -> t.name().equals("get_record")).findFirst().orElseThrow();

            assertThat(tool.annotations().readOnlyHint()).isTrue();
            assertThat(tool.title()).isEqualTo("Pobierz rekord");
            assertThat(tool.inputSchema()).containsEntry("additionalProperties", false)
                    .doesNotContainKeys("$ref", "$defs", "anyOf", "oneOf");
            assertThat(tool.inputSchema()).extractingByKey("properties")
                    .asInstanceOf(InstanceOfAssertFactories.MAP).containsOnlyKeys("recordId")
                    .extractingByKey("recordId").asInstanceOf(InstanceOfAssertFactories.MAP)
                    .containsEntry("type", "string")
                    .containsEntry("maxLength", ExampleResourceModel.RECORD_ID_MAX_LENGTH)
                    .hasEntrySatisfying("pattern", pattern -> assertThat(pattern.toString()).startsWith("^").endsWith("$"));
        }
    }

    @Test
    void withoutCredentialAsksToConnectTheAccountAndDoesNotCallTheBackend() {
        try (McpSyncClient client = clientWithCredential(null)) {
            McpSchema.CallToolResult result = call(client, "get_record", Map.of("recordId", StubBackend.OPEN_1));

            assertCredentialRequired(result);
            assertThat(BACKEND.authorizations()).isEmpty();
        }
    }

    @Test
    void credentialRejectedByTheBackendAsksToReconnect(CapturedOutput output) {
        try (McpSyncClient client = clientWithCredential(StubBackend.EXPIRED_CREDENTIAL)) {
            McpSchema.CallToolResult result = call(client, "get_record", Map.of("recordId", StubBackend.OPEN_1));

            assertCredentialRequired(result);
        }
        assertThat(output.getAll()).contains("CredentialRequiredException");
    }

    @Test
    void theAdapterHasNoCredentialOutsideAGatewayRequest() {
        assertThatThrownBy(() -> resourcePort.findById(StubBackend.OPEN_1)).isInstanceOf(CredentialRequiredException.class);
        assertThat(BACKEND.authorizations()).isEmpty();
    }

    @Test
    void missingAndForbiddenRecordsLookTheSame() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            McpSchema.CallToolResult missing = call(client, "get_record", Map.of("recordId", StubBackend.MISSING));
            McpSchema.CallToolResult forbidden = call(client, "get_record", Map.of("recordId", StubBackend.FORBIDDEN));

            assertThat(missing.isError()).isTrue();
            assertThat(text(missing)).contains("\"error\":\"not_found_or_denied\"");
            assertThat(text(forbidden)).isEqualTo(text(missing));
        }
    }

    @Test
    void anInvalidIdIsRejectedBeforeTheBackend() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            for (String recordId : new String[] {"../admin", "ABC-1?x=1", "a".repeat(65), "", ".."}) {
                McpSchema.CallToolResult result = call(client, "get_record", Map.of("recordId", recordId));

                // The MCP SDK checks the published schema first and answers in its own format (not C10, reported
                // to ws-02); the tool's own check (ExampleToolTest) is the second line.
                assertThat(result.isError()).isTrue();
                assertThat(text(result)).containsAnyOf("\"error\":\"invalid_argument\"", "input validation failed");
                if (!recordId.isEmpty()) {
                    assertThat(text(result)).as("the argument value is not echoed").doesNotContain(recordId);
                }
            }
            assertThat(BACKEND.authorizations()).isEmpty();
        }
    }

    @Test
    void anUnreadableBodyStaysOutOfTheResultAndTheLog(CapturedOutput output) {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            McpSchema.CallToolResult result = call(client, "get_record", Map.of("recordId", StubBackend.BAD_JSON));

            assertThat(result.isError()).isTrue();
            assertThat(text(result)).contains("\"error\":\"internal_error\"").doesNotContain(StubBackend.BODY_MARKER);
        }
        assertThat(output.getAll()).contains("BackendResponseException").doesNotContain(StubBackend.BODY_MARKER);
    }

    @Test
    void aRecordWithoutIdIsTheBackendsFaultNotTheCallers() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            McpSchema.CallToolResult result = call(client, "get_record", Map.of("recordId", StubBackend.NO_ID));

            assertThat(text(result)).contains("\"error\":\"internal_error\"");
        }
    }

    @Test
    void aRedirectIsNotFollowedAndNotReadAsMissing() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            McpSchema.CallToolResult result = call(client, "get_record", Map.of("recordId", StubBackend.REDIRECT));

            assertThat(text(result)).contains("\"error\":\"internal_error\"");
            assertThat(BACKEND.elsewhereHits()).as("the credential must not follow the Location").isEmpty();
        }
    }

    @Test
    void aSlowBackendTimesOut() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            long start = System.nanoTime();
            McpSchema.CallToolResult result = call(client, "get_record", Map.of("recordId", StubBackend.SLOW));

            assertThat(text(result)).contains("\"error\":\"upstream_unavailable\"");
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(StubBackend.SLOW_MILLIS));
        }
    }

    @Test
    void anOversizedBodyIsCutOff() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            McpSchema.CallToolResult result = call(client, "get_record", Map.of("recordId", StubBackend.HUGE));

            assertThat(text(result)).contains("\"error\":\"internal_error\"");
        }
    }

    @Test
    void theCredentialNeverAppearsInTheResultOrTheLog(CapturedOutput output) {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            McpSchema.CallToolResult result = call(client, "get_record", Map.of("recordId", StubBackend.FAILING));

            assertThat(result.isError()).isTrue();
            assertThat(text(result)).contains("\"error\":\"internal_error\"").doesNotContain("example-pat");
        }
        assertThat(output.getAll()).contains("\"tool\":\"get_record\"").doesNotContain("example-pat")
                .as("the backend's error body stays out of the log").doesNotContain("boom");
    }
}
