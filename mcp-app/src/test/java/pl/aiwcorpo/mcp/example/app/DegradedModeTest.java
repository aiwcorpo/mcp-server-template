package pl.aiwcorpo.mcp.example.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pl.aiwcorpo.mcp.example.core.domain.BackendDegradedException;
import pl.aiwcorpo.mcp.example.tools.ExampleWriteGate;

/** A backend version nobody tested: every tool that changes something refuses and writes nothing, reads go on. */
class DegradedModeTest extends ServerTestSupport {

    private static final String PAT = "Bearer example-pat-of-jkowalski-7f3a";

    @MockitoBean
    ExampleWriteGate gate;

    @BeforeEach
    void degrade() {
        Assumptions.assumeTrue(SampleScope.FULL_TOOL_SET);
        BACKEND.reset();
        doThrow(new BackendDegradedException()).when(gate).requireWritable();
    }

    @Test
    void aChangeIsRefusedAsUpstreamUnavailableAndNothingIsWritten() {
        try (McpSyncClient client = clientWithToken(ServerTestSupport::writer, PAT)) {
            McpSchema.CallToolResult result = call(client, "update_record",
                    Map.of("recordId", StubBackend.OPEN_1, "title", "x"));

            assertThat(result.isError()).isTrue();
            assertThat(text(result)).contains("\"error\":\"upstream_unavailable\"", "Nothing was changed");
        }
        assertThat(BACKEND.updates()).isEmpty();
    }

    @Test
    void readsKeepWorking() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            assertThat(call(client, "get_record", Map.of("recordId", StubBackend.OPEN_1)).isError()).isFalse();
            assertThat(call(client, "list_comments", Map.of("recordId", StubBackend.OPEN_1)).isError()).isFalse();
        }
    }
}
