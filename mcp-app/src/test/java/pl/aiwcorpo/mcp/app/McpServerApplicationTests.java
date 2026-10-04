package pl.aiwcorpo.mcp.app;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import pl.aiwcorpo.mcp.core.port.in.ExampleUseCase;

/**
 * Verifies the whole server wires up: platform auto-config (PAT forwarding, security headers,
 * observability, error), the hexagon (use-case bean + outbound adapter) and MCP tool registration
 * all load together. No OAuth — the context starts without any IdP.
 */
@SpringBootTest
@ActiveProfiles("test")   // no backend needed just to load the context
class McpServerApplicationTests {

    @Autowired
    ToolCallbackProvider toolCallbackProvider;

    @Autowired
    ExampleUseCase exampleUseCase;

    @Test
    void contextLoadsAndRegistersTools() {
        assertThat(exampleUseCase).isNotNull();
        assertThat(toolCallbackProvider.getToolCallbacks())
                .extracting(cb -> cb.getToolDefinition().name())
                .contains("example_lookup", "example_rename", "example_purge");
    }
}
