package pl.aiwcorpo.mcp.example.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import pl.aiwcorpo.mcp.example.core.domain.ExampleResourceModel;
import pl.aiwcorpo.mcp.platform.error.InvalidToolArgumentException;
import pl.aiwcorpo.mcp.platform.input.SchemaLimitedToolCallbacks;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** What the tool class checks itself and what its schemas publish; the policy and the wire are tested in mcp-app. */
class ExampleToolTest {

    private final ExampleTool tool = new ExampleTool((project, text) -> List.of(), null);

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
    void theSchemasPublishTheLimitsAndAreClosed() {
        ToolCallback[] callbacks = SchemaLimitedToolCallbacks.of(tool).getToolCallbacks();
        JsonMapper json = JsonMapper.builder().build();

        for (ToolCallback callback : callbacks) {
            JsonNode schema = json.readTree(callback.getToolDefinition().inputSchema());
            assertThat(schema.path("additionalProperties").asBoolean(true)).as(callback.getToolDefinition().name()).isFalse();
            assertThat(schema.toString()).doesNotContain("$ref", "anyOf", "oneOf");
        }
        assertThat(callbacks[0].getToolDefinition().name()).isEqualTo("get_record");
        JsonNode recordId = json.readTree(callbacks[0].getToolDefinition().inputSchema()).path("properties").path("recordId");
        assertThat(recordId.path("maxLength").asInt()).isEqualTo(ExampleResourceModel.RECORD_ID_MAX_LENGTH);
        assertThat(recordId.path("pattern").asString()).startsWith("^").endsWith("$");

        assertThat(callbacks[1].getToolDefinition().name()).isEqualTo("search_records");
        JsonNode properties = json.readTree(callbacks[1].getToolDefinition().inputSchema()).path("properties");
        assertThat(properties.path("project").path("pattern").asString()).startsWith("^").endsWith("$");
        assertThat(properties.path("text").path("maxLength").asInt()).isEqualTo(ExampleTool.SEARCH_TEXT_MAX_LENGTH);
    }
}
