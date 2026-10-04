package pl.aiwcorpo.mcp.app.config;

import pl.aiwcorpo.mcp.tools.ExampleTool;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers MCP tools with the Spring AI MCP server. Spring AI exposes the returned
 * {@link ToolCallbackProvider} over the configured transport (streamable-http, default {@code /mcp}).
 *
 * <p>Governance: keep an explicit, reviewed allow-list of tool objects here. Do not package-scan
 * for tools — the tool set is the server's public capability surface.
 */
@Configuration
public class McpToolsConfiguration {

    @Bean
    public ToolCallbackProvider mcpTools(ExampleTool exampleTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(exampleTool)   // add every @Tool-annotated bean here
                .build();
    }
}
