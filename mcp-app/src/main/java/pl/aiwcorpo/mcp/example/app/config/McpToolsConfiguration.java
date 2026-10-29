package pl.aiwcorpo.mcp.example.app.config;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.aiwcorpo.mcp.example.tools.ExampleTool;
import pl.aiwcorpo.mcp.platform.input.SchemaLimitedToolCallbacks;

/**
 * The tool set of the server, as an explicit list: the MCP server exposes only tools registered through a
 * {@link ToolCallbackProvider} (an {@code @Tool} bean alone answers "Unknown tool"), and a reviewed list
 * keeps the public surface from growing by accident. {@link SchemaLimitedToolCallbacks} also publishes the
 * {@code @ArgumentLimits} of the arguments in the input schema.
 */
@Configuration
public class McpToolsConfiguration {

    @Bean
    public ToolCallbackProvider mcpTools(ExampleTool exampleTool) {
        return SchemaLimitedToolCallbacks.of(exampleTool);
    }
}
