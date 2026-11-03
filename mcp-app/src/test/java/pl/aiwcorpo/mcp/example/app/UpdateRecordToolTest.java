package pl.aiwcorpo.mcp.example.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** {@code update_record} (UPDATE): the policy judges the record, the gate judges the version of the backend. */
class UpdateRecordToolTest extends ServerTestSupport {

    private static final String PAT = "Bearer example-pat-of-jkowalski-7f3a";

    @BeforeEach
    void resetBackend() throws InterruptedException {
        Assumptions.assumeTrue(SampleScope.FULL_TOOL_SET);
        BACKEND.reset();
        awaitProbe();
    }

    @Test
    void setsTheTitleAndReturnsTheRecordAsTheBackendHasIt() {
        try (McpSyncClient client = clientWithToken(ServerTestSupport::writer, PAT)) {
            McpSchema.CallToolResult result = call(client, "update_record",
                    Map.of("recordId", StubBackend.OPEN_1, "title", "Q3 report"));

            assertThat(result.isError()).isFalse();
            assertThat(text(result)).contains("\"id\":\"" + StubBackend.OPEN_1 + "\"", "Q3 report");
            assertThat(BACKEND.updates()).containsExactly(StubBackend.OPEN_1 + " Q3 report");
        }
    }

    @Test
    void theSameCallTwiceLeavesTheSameState() {
        try (McpSyncClient client = clientWithToken(ServerTestSupport::writer, PAT)) {
            Map<String, Object> arguments = Map.of("recordId", StubBackend.OPEN_1, "title", "Q3 report");

            String first = text(call(client, "update_record", arguments));
            String second = text(call(client, "update_record", arguments));

            assertThat(second).isEqualTo(first);
        }
    }

    @Test
    void isListedAsAnUpdateNotReadOnlyNorDestructive() {
        try (McpSyncClient client = clientWithToken(ServerTestSupport::writer, PAT)) {
            McpSchema.Tool tool = client.listTools().tools().stream()
                    .filter(t -> t.name().equals("update_record")).findFirst().orElseThrow();

            assertThat(tool.annotations().readOnlyHint()).isFalse();
            assertThat(tool.annotations().destructiveHint()).isFalse();
        }
    }

    @Test
    void aCallerWithoutTheWriteGroupIsDeniedBeforeTheBackendIsAsked() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            McpSchema.CallToolResult result = call(client, "update_record",
                    Map.of("recordId", StubBackend.OPEN_1, "title", "x"));

            assertThat(result.isError()).isTrue();
            assertThat(text(result)).contains("\"error\":\"access_denied\"");
        }
        assertThat(BACKEND.updates()).isEmpty();
    }

    @Test
    void aRecordOfARestrictedProjectIsNotWrittenAndLooksLikeAMissingOne() {
        try (McpSyncClient client = clientWithToken(ServerTestSupport::writer, PAT)) {
            String restricted = text(call(client, "update_record",
                    Map.of("recordId", StubBackend.RESTRICTED_5, "title", "x")));
            String missing = text(call(client, "update_record", Map.of("recordId", StubBackend.MISSING, "title", "x")));

            assertThat(restricted).contains("\"error\":\"not_found_or_denied\"").isEqualTo(missing);
        }
        assertThat(BACKEND.updates()).isEmpty();
    }

    @Test
    void aTitleThatIsBlankOrHasLineBreaksIsRefusedWithoutAWrite() {
        try (McpSyncClient client = clientWithToken(ServerTestSupport::writer, PAT)) {
            for (String title : new String[] {"   ", "two\nlines", "x".repeat(256)}) {
                assertInvalidArgument(call(client, "update_record",
                        Map.of("recordId", StubBackend.OPEN_1, "title", title)));
            }
        }
        assertThat(BACKEND.updates()).isEmpty();
    }

    @Test
    void credentialRejectedByTheBackendAsksToReconnect() {
        try (McpSyncClient client = clientWithToken(ServerTestSupport::writer, StubBackend.EXPIRED_CREDENTIAL)) {
            assertCredentialRequired(call(client, "update_record", Map.of("recordId", StubBackend.OPEN_1, "title", "x")));
        }
        assertThat(BACKEND.updates()).isEmpty();
    }
}
