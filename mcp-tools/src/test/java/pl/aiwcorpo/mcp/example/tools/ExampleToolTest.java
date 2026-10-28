package pl.aiwcorpo.mcp.example.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import pl.aiwcorpo.mcp.example.core.domain.ExampleRecord;
import pl.aiwcorpo.mcp.platform.error.InvalidToolArgumentException;
import pl.aiwcorpo.mcp.platform.input.SchemaLimitedToolCallbacks;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ExampleToolTest {

    private final ExampleTool tool = new ExampleTool(id -> new ExampleRecord(id, "ABC", "title", "open"));

    @Test
    void theToolChecksTheIdItselfWithAFixedMessage() {
        for (String recordId : new String[] {"../admin", "..", ".hidden", "a".repeat(65), "", "ABC 1"}) {
            assertThatThrownBy(() -> tool.getRecord(recordId))
                    .isInstanceOf(InvalidToolArgumentException.class)
                    .hasMessage("recordId must be 1-64 characters: letters, digits, '-' or '_'");
        }
        assertThatThrownBy(() -> tool.getRecord(null)).isInstanceOf(InvalidToolArgumentException.class);
        assertThat(tool.getRecord("a".repeat(64)).id()).hasSize(64);
    }

    @Test
    void theSchemaPublishesTheSameLimits() {
        ToolCallback callback = SchemaLimitedToolCallbacks.of(tool).getToolCallbacks()[0];
        JsonNode recordId = JsonMapper.builder().build().readTree(callback.getToolDefinition().inputSchema())
                .path("properties").path("recordId");

        assertThat(recordId.path("maxLength").asInt()).isEqualTo(ExampleTool.RECORD_ID_MAX_LENGTH);
        assertThat(recordId.path("pattern").asString()).isEqualTo(ExampleTool.RECORD_ID_PATTERN);
        assertThat(callback.getToolDefinition().name()).isEqualTo("get_record");
        assertThat(callback.getToolDefinition().inputSchema()).contains("\"additionalProperties\":false");
    }
}
