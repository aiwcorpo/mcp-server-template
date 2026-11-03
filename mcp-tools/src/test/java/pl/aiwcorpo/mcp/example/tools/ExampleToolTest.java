package pl.aiwcorpo.mcp.example.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import pl.aiwcorpo.mcp.example.core.domain.ExampleRecord;
import pl.aiwcorpo.mcp.example.core.domain.ExampleResourceModel;
import pl.aiwcorpo.mcp.platform.error.InvalidToolArgumentException;
import pl.aiwcorpo.mcp.platform.input.SchemaLimitedToolCallbacks;
import pl.aiwcorpo.mcp.platform.resource.ResourceContext;
import pl.aiwcorpo.mcp.platform.test.TestResourceContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** What the tool class checks itself and what its schemas publish; the policy and the wire are tested in mcp-app. */
class ExampleToolTest {

    private final ExampleTool tool = new ExampleTool(new NoBackendUseCase(), null, null, null);

    @Test
    void searchRejectsABlankOrTooLongTextWithAFixedMessage() {
        for (String text : new String[] {"", "  ", "a".repeat(201)}) {
            assertThatThrownBy(() -> tool.searchRecords("ABC", text))
                    .isInstanceOf(InvalidToolArgumentException.class)
                    .hasMessage("text must be 1-200 characters");
        }
        assertThatThrownBy(() -> tool.searchRecords("ABC", null)).isInstanceOf(InvalidToolArgumentException.class);
    }

    @Test
    void getRecordReturnsTheRecordTheResolverFetched() {
        ExampleRecord record = new ExampleRecord("ABC-1", "ABC", "", "Quarterly report", "open");
        try (ResourceContext context = TestResourceContext.open("get_record", Map.of("recordId", "ABC-1"))
                .withFetched(ExampleResourceModel.RECORD_TYPE, "ABC-1", record, null)) {
            ExampleRecordView view = tool.getRecord("ABC-1");

            assertThat(view.id()).isEqualTo("ABC-1");
            assertThat(view.project()).isEqualTo("ABC");
            assertThat(view.status()).isEqualTo("open");
        }
    }

    @Test
    void theSchemasPublishTheLimitsAndAreClosed() {
        ToolCallback[] callbacks = SchemaLimitedToolCallbacks.of(tool).getToolCallbacks();
        JsonMapper json = JsonMapper.builder().build();

        for (ToolCallback callback : callbacks) {
            JsonNode schema = json.readTree(callback.getToolDefinition().inputSchema());
            assertThat(schema.path("additionalProperties").asBoolean(true)).as(callback.getToolDefinition().name()).isFalse();
            assertThat(schema.toString()).doesNotContain("$ref", "anyOf", "oneOf");
        }
        JsonNode recordId = json.readTree(schemaOf(callbacks, "get_record")).path("properties").path("recordId");
        assertThat(recordId.path("maxLength").asInt()).isEqualTo(ExampleResourceModel.RECORD_ID_MAX_LENGTH);
        assertThat(recordId.path("pattern").asString()).startsWith("^").endsWith("$");

        JsonNode properties = json.readTree(schemaOf(callbacks, "search_records")).path("properties");
        assertThat(properties.path("project").path("pattern").asString()).startsWith("^").endsWith("$");
        assertThat(properties.path("text").path("maxLength").asInt()).isEqualTo(ExampleTool.SEARCH_TEXT_MAX_LENGTH);
    }

    @Test
    void everyToolOfTheServerIsListedAndTheChangeIsAnUpdate() {
        ToolCallback[] callbacks = SchemaLimitedToolCallbacks.of(tool).getToolCallbacks();

        assertThat(java.util.Arrays.stream(callbacks).map(callback -> callback.getToolDefinition().name()))
                .containsExactlyInAnyOrder("get_record", "search_records", "list_comments", "get_comment", "update_record");
        JsonNode cursor = json().readTree(schemaOf(callbacks, "list_comments")).path("properties").path("cursor");
        assertThat(cursor.path("maxLength").asInt()).isEqualTo(pl.aiwcorpo.mcp.platform.paging.CursorCodec.MAX_CURSOR_LENGTH);
        JsonNode commentId = json().readTree(schemaOf(callbacks, "get_comment")).path("properties").path("commentId");
        assertThat(commentId.path("pattern").asString()).isEqualTo(ExampleTool.COMMENT_ID_PATTERN);
    }

    private static JsonMapper json() {
        return JsonMapper.builder().build();
    }

    private static String schemaOf(ToolCallback[] callbacks, String name) {
        return java.util.Arrays.stream(callbacks).filter(callback -> callback.getToolDefinition().name().equals(name))
                .findFirst().orElseThrow().getToolDefinition().inputSchema();
    }

    /** A use case that must not be called: these tests only look at what the tool class checks itself. */
    private static final class NoBackendUseCase implements pl.aiwcorpo.mcp.example.core.port.in.ExampleUseCase {

        @Override
        public List<pl.aiwcorpo.mcp.example.core.domain.ExampleRecordHit> searchRecords(String query) {
            return List.of();
        }

        @Override
        public pl.aiwcorpo.mcp.example.core.domain.CommentPage listComments(String recordId, int start) {
            throw new UnsupportedOperationException();
        }

        @Override
        public pl.aiwcorpo.mcp.example.core.domain.RecordComment getComment(String recordId, String commentId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ExampleRecord updateTitle(String recordId, String title) {
            throw new UnsupportedOperationException();
        }
    }
}
