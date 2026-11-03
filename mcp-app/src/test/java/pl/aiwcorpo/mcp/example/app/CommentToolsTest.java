package pl.aiwcorpo.mcp.example.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code list_comments} and {@code get_comment}: the record is judged by the policy, the comment ids are children of
 * it and are only served under the record that the backend files them under. The stub files every fourth comment of
 * {@link StubBackend#OPEN_1} under another record.
 */
class CommentToolsTest extends ServerTestSupport {

    private static final String PAT = "Bearer example-pat-of-jkowalski-7f3a";
    private static final Pattern NEXT_CURSOR = Pattern.compile("\"nextCursor\":\"([A-Za-z0-9_.-]+)\"");

    @BeforeEach
    void resetBackend() {
        Assumptions.assumeTrue(SampleScope.FULL_TOOL_SET);
        BACKEND.reset();
    }

    private static int commentsIn(String result) {
        return result.split("\"id\":\"[0-9]+\"", -1).length - 1;
    }

    @Test
    void aPageHoldsOnlyTheCommentsOfTheRecordAndIsFilledFromFurtherBackendPages() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            McpSchema.CallToolResult first = call(client, "list_comments", Map.of("recordId", StubBackend.OPEN_1));

            assertThat(first.isError()).isFalse();
            String text = text(first);
            assertThat(text).contains("\"kind\":\"untrusted-data\"", "comment 1", "comment 3")
                    .doesNotContain("comment 4\"", "comment 8\"");
            assertThat(commentsIn(text)).as("two backend pages of ten, a quarter of them filed elsewhere").isEqualTo(15);

            Matcher next = NEXT_CURSOR.matcher(text);
            assertThat(next.find()).isTrue();
            McpSchema.CallToolResult second = call(client, "list_comments",
                    Map.of("recordId", StubBackend.OPEN_1, "cursor", next.group(1)));
            assertThat(second.isError()).isFalse();
            assertThat(text(second)).contains("comment 21", "comment 23").doesNotContain("comment 24\"")
                    .contains("\"nextCursor\":null");
            assertThat(commentsIn(text(second))).isEqualTo(3);
        }
    }

    @Test
    void aCursorOfAnotherRecordIsRefused() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            Matcher next = NEXT_CURSOR.matcher(text(call(client, "list_comments", Map.of("recordId", StubBackend.OPEN_1))));
            assertThat(next.find()).isTrue();

            McpSchema.CallToolResult result = call(client, "list_comments",
                    Map.of("recordId", StubBackend.OPEN_2, "cursor", next.group(1)));

            assertInvalidArgument(result);
        }
    }

    @Test
    void aRecordTheCallerMayNotSeeHasNoComments() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            String missing = text(call(client, "list_comments", Map.of("recordId", StubBackend.MISSING)));
            String restricted = text(call(client, "list_comments", Map.of("recordId", StubBackend.RESTRICTED_5)));

            assertThat(missing).contains("\"error\":\"not_found_or_denied\"");
            assertThat(restricted).isEqualTo(missing);
        }
    }

    @Test
    void aCommentOfTheRecordIsReturned() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            McpSchema.CallToolResult result = call(client, "get_comment",
                    Map.of("recordId", StubBackend.OPEN_1, "commentId", "3"));

            assertThat(result.isError()).isFalse();
            assertThat(text(result)).contains("\"id\":\"3\"", "comment 3", "\"kind\":\"untrusted-data\"");
        }
    }

    @Test
    void aCommentFiledUnderAnotherRecordLooksLikeAMissingOne() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            String foreign = text(call(client, "get_comment", Map.of("recordId", StubBackend.OPEN_1, "commentId", "4")));
            String missing = text(call(client, "get_comment", Map.of("recordId", StubBackend.OPEN_1, "commentId", "99")));

            assertThat(foreign).contains("\"error\":\"not_found_or_denied\"").doesNotContain("comment 4");
            assertThat(foreign).as("the same error as for a comment that does not exist").isEqualTo(missing);
        }
    }

    @Test
    void aCommentIdOutsideThePatternNeverReachesTheBackend() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            assertInvalidArgument(call(client, "get_comment",
                    Map.of("recordId", StubBackend.OPEN_1, "commentId", "../" + StubBackend.RESTRICTED_5)));
        }
        assertThat(BACKEND.authorizations()).isEmpty();
    }
}
