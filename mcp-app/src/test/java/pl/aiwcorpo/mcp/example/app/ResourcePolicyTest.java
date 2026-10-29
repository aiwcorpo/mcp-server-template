package pl.aiwcorpo.mcp.example.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The policy (C7) on the resource of {@code get_record}, with the example bundle of the contracts: a record is judged by
 * the project the backend says it is in, whatever the argument looks like, and every refusal looks like a missing record.
 */
class ResourcePolicyTest extends ServerTestSupport {

    private static final String PAT = "Bearer example-pat-of-jkowalski-7f3a";
    private static final String NOT_FOUND_OR_DENIED = "\"error\":\"not_found_or_denied\"";

    @BeforeEach
    void resetBackend() {
        BACKEND.reset();
    }

    @Test
    void aRecordInARestrictedProjectIsNotFoundOrDeniedLikeAMissingOne() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            McpSchema.CallToolResult missing = call(client, "get_record", Map.of("recordId", StubBackend.MISSING));
            McpSchema.CallToolResult restricted = call(client, "get_record", Map.of("recordId", StubBackend.RESTRICTED_5));

            assertThat(restricted.isError()).isTrue();
            assertThat(text(restricted)).contains(NOT_FOUND_OR_DENIED).isEqualTo(text(missing))
                    .as("one fixed text for every refusal, set by the starter")
                    .contains(pl.aiwcorpo.mcp.platform.error.ToolErrorCode.NOT_FOUND_OR_DENIED_MESSAGE);
        }
    }

    @Test
    void aRecordMovedToARestrictedProjectIsJudgedByTheProjectItIsInNow() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            McpSchema.CallToolResult missing = call(client, "get_record", Map.of("recordId", StubBackend.MISSING));
            McpSchema.CallToolResult moved = call(client, "get_record", Map.of("recordId", StubBackend.MOVED));

            assertThat(moved.isError()).isTrue();
            assertThat(text(moved)).as("the id looks like one of the open project; the backend says it is in the restricted one").isEqualTo(text(missing));
        }
    }

    @Test
    void aRecordWhoseProjectClassificationIsUnknownIsNotServedWhereThePolicyRequiresIt() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            McpSchema.CallToolResult result = call(client, "get_record", Map.of("recordId", StubBackend.UNCLASSIFIED));

            if (SampleScope.ONLY_GRANTED_PROJECTS) {
                assertThat(result.isError()).as("no grant on this container: the same answer as for a missing record").isTrue();
                assertThat(text(result)).contains(NOT_FOUND_OR_DENIED);
            } else if (SampleScope.UNKNOWN_CLASSIFICATION_REFUSED) {
                // The attribute is required: a resource without it cannot be built, which the contract makes an
                // internal_error (not a denial); nothing of the record reaches the client.
                assertThat(result.isError()).isTrue();
                assertThat(text(result)).contains("\"error\":\"internal_error\"").doesNotContain("Unclassified");
            } else {
                assertThat(result.isError()).isFalse();
            }
        }
    }

    @Test
    void aCallerWithoutTheGroupOfTheToolGetsAccessDeniedAndNothingReachesTheBackend() {
        try (McpSyncClient client = clientWithToken(user -> user.username("jkowalski").groups(List.of("mcp-nobody")), PAT)) {
            McpSchema.CallToolResult result = call(client, "get_record", Map.of("recordId", StubBackend.OPEN_1));

            assertThat(result.isError()).isTrue();
            assertThat(text(result)).contains("\"error\":\"access_denied\"");
            assertThat(BACKEND.authorizations()).isEmpty();
        }
    }

    @Test
    void aLinkThatNarrowsToAnotherProjectHidesTheRecord() {
        try (McpSyncClient client = clientWithToken(
                user -> { reader(user); user.virtualServer("def-only", Map.of(SampleScope.LINK_KEY, List.of(SampleScope.OTHER_PROJECT))); }, PAT)) {
            McpSchema.CallToolResult inside = call(client, "get_record", Map.of("recordId", StubBackend.OTHER_7));
            McpSchema.CallToolResult outside = call(client, "get_record", Map.of("recordId", StubBackend.OPEN_1));

            assertThat(inside.isError()).isEqualTo(SampleScope.ONLY_GRANTED_PROJECTS);
            assertThat(outside.isError()).isTrue();
            assertThat(text(outside)).contains(NOT_FOUND_OR_DENIED);
        }
    }

    @Test
    void aBackendThatFailsIsNotADenial() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            McpSchema.CallToolResult result = call(client, "get_record", Map.of("recordId", StubBackend.FAILING));

            assertThat(result.isError()).isTrue();
            assertThat(text(result)).doesNotContain(NOT_FOUND_OR_DENIED);
        }
    }
}
