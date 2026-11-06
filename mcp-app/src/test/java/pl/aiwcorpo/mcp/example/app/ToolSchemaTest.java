package pl.aiwcorpo.mcp.example.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import pl.aiwcorpo.mcp.example.core.domain.ExampleResourceModel;
import pl.aiwcorpo.mcp.example.tools.ExampleTool;
import pl.aiwcorpo.mcp.platform.input.SchemaLimitedToolCallbacks;
import pl.aiwcorpo.mcp.platform.paging.CursorCodec;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * What the tool schemas publish. Here and not in mcp-tools because the id patterns of the resource arguments come from
 * the declarations in {@code mcp/policies/resource-types.json}, which belong to the application module.
 */
class ToolSchemaTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final ToolCallback[] callbacks = SchemaLimitedToolCallbacks.of(ExampleTool.forManifest()).getToolCallbacks();

    @Test
    void theSchemasPublishTheLimitsAndAreClosed() {
        for (ToolCallback callback : callbacks) {
            JsonNode schema = JSON.readTree(callback.getToolDefinition().inputSchema());
            assertThat(schema.path("additionalProperties").asBoolean(true)).as(callback.getToolDefinition().name()).isFalse();
            assertThat(schema.toString()).doesNotContain("$ref", "anyOf", "oneOf");
        }
        JsonNode recordId = schemaOf("get_record").path("properties").path("recordId");
        assertThat(recordId.path("maxLength").asInt()).isEqualTo(ExampleResourceModel.RECORD_ID_MAX_LENGTH);
        assertThat(recordId.path("pattern").asString()).as("the idPattern of Example::Record").isEqualTo("^[A-Z][A-Z0-9_]{1,9}-[1-9][0-9]{0,9}$");

        JsonNode properties = schemaOf("search_records").path("properties");
        assertThat(properties.path("project").path("pattern").asString()).as("the idPattern of Example::Project")
                .isEqualTo("^[A-Z][A-Z0-9_]{1,9}$");
        assertThat(properties.path("text").path("maxLength").asInt()).isEqualTo(ExampleTool.SEARCH_TEXT_MAX_LENGTH);
    }

    @Test
    void everyToolOfTheServerIsListedAndTheChangeIsAnUpdate() {
        assertThat(Arrays.stream(callbacks).map(callback -> callback.getToolDefinition().name()))
                .containsExactlyInAnyOrder("get_record", "search_records", "list_comments", "get_comment", "update_record");
        JsonNode cursor = schemaOf("list_comments").path("properties").path("cursor");
        assertThat(cursor.path("maxLength").asInt()).isEqualTo(CursorCodec.MAX_CURSOR_LENGTH);
        JsonNode commentId = schemaOf("get_comment").path("properties").path("commentId");
        assertThat(commentId.path("pattern").asString()).isEqualTo(ExampleTool.COMMENT_ID_PATTERN);
    }

    private JsonNode schemaOf(String name) {
        return JSON.readTree(Arrays.stream(callbacks).filter(callback -> callback.getToolDefinition().name().equals(name))
                .findFirst().orElseThrow().getToolDefinition().inputSchema());
    }
}
