package pl.aiwcorpo.mcp.example.app;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import pl.aiwcorpo.mcp.platform.test.TestGateway;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Contracts 8.0.0 at the server: it serves tools only (resources, prompts, completion and logging are not declared and are answered
 * with -32601), and the principal of a call (human, agent, delegated) is decided against the client lists of the policy data and
 * shows in the audit event.
 */
class ToolsOnlyAndAgentsTest extends ServerTestSupport {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PAT = "Bearer example-pat-of-jkowalski-7f3a";
    private static final String AGENT_CLIENT = "agent-reports";
    private static final String AGENT_SUB = "3b0f6c52-8f3e-4f0e-b6a2-5d5a1f0e9c11";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @BeforeEach
    void resetBackend() {
        BACKEND.reset();
    }

    @AfterEach
    void withdrawAgentClients() {
        GATEWAY.policyData().allowedAgentClients().push();
    }

    @Test
    void initializeDeclaresToolsAndNothingElse() throws Exception {
        HttpResponse<String> initialize = post("""
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18",
                "capabilities":{},"clientInfo":{"name":"primitives-test","version":"1"}}}""", GATEWAY.user().mint().headers(), null);

        assertThat(initialize.statusCode()).isEqualTo(200);
        assertThat(initialize.body()).contains("\"tools\"")
                .doesNotContain("\"resources\"", "\"prompts\"", "\"completions\"", "\"logging\"");
    }

    @Test
    void resourcesPromptsCompletionAndLoggingAreRefusedWithAValidToken() throws Exception {
        String session = session();
        Map<String, String> requests = Map.of(
                "resources/list", "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"resources/list\"}",
                "resources/templates/list", "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"resources/templates/list\"}",
                "prompts/list", "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"prompts/list\"}",
                "prompts/get", "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"prompts/get\",\"params\":{\"name\":\"p\"}}",
                "completion/complete", "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"completion/complete\",\"params\":{}}",
                "logging/setLevel", "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"logging/setLevel\",\"params\":{\"level\":\"debug\"}}");
        for (Map.Entry<String, String> request : requests.entrySet()) {
            HttpResponse<String> answer = post(request.getValue(), GATEWAY.user().mint().headers(), session);

            assertThat(answer.body()).as(request.getKey()).contains("\"code\":-32601").doesNotContain("\"result\"");
        }
        String uri = "example://record/" + StubBackend.OPEN_1;
        HttpResponse<String> read = post("{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"resources/read\",\"params\":{\"uri\":\"" + uri + "\"}}",
                GATEWAY.user().extraClaim("resource", uri).mint().headers(), session);
        assertThat(read.body()).contains("\"code\":-32601");
        assertThat(BACKEND.authorizations()).isEmpty();
    }

    @Test
    void anAgentCallsOnlyFromTheClientsListedForAgents() {
        GATEWAY.policyData().allowedAgentClients(AGENT_CLIENT).push();

        McpSchema.CallToolResult listedAgent = getRecord(token -> token.client(AGENT_CLIENT)
                .username("service-account-agent-reports").extraClaim("pty", "agent"));
        assertThat(listedAgent.isError()).as(text(listedAgent)).isFalse();
        assertThat(text(listedAgent)).contains("\"id\":\"" + StubBackend.OPEN_1 + "\"");

        assertThat(text(getRecord(token -> token.client("claude-code").extraClaim("pty", "agent")))).as("an agent from a human client")
                .contains("access_denied");
        assertThat(text(getRecord(token -> token.client(AGENT_CLIENT)))).as("a human from an agent client").contains("access_denied");
        assertThat(getRecord(token -> { }).isError()).as("a human from a human client").isFalse();
    }

    @Test
    void withoutAgentClientsEveryAgentCallIsRefusedBeforeTheBackend() {
        McpSchema.CallToolResult agent = getRecord(token -> token.client(AGENT_CLIENT).extraClaim("pty", "agent"));

        assertThat(agent.isError()).isTrue();
        assertThat(text(agent)).contains("access_denied");
        assertThat(BACKEND.authorizations()).isEmpty();
    }

    @Test
    void aDelegatedCallIsLetInFromTheAgentListAndTheActorReachesTheAuditEvent() {
        GATEWAY.policyData().allowedAgentClients(AGENT_CLIENT).push();

        List<JsonNode> events = audit(() -> {
            McpSchema.CallToolResult result = getRecord(token -> token.client(AGENT_CLIENT).extraClaim("pty", "delegated")
                    .extraClaim("act", Map.of("sub", AGENT_SUB, "azp", AGENT_CLIENT)));
            assertThat(result.isError()).as(text(result)).isFalse();
        });

        JsonNode call = toolCall(events);
        assertThat(call.path("principalType").asString()).isEqualTo("delegated");
        assertThat(call.path("actorSub").asString()).isEqualTo(AGENT_SUB);
        assertThat(call.path("actorClient").asString()).isEqualTo(AGENT_CLIENT);
    }

    @Test
    void theAuditEventOfAnAgentNamesThePrincipalTypeAndOfAHumanDoesNot() {
        GATEWAY.policyData().allowedAgentClients(AGENT_CLIENT).push();

        JsonNode agent = toolCall(audit(() -> getRecord(token -> token.client(AGENT_CLIENT).extraClaim("pty", "agent"))));
        assertThat(agent.path("principalType").asString()).isEqualTo("agent");
        assertThat(agent.has("actorSub")).isFalse();
        assertThat(agent.has("actorClient")).isFalse();

        JsonNode human = toolCall(audit(() -> getRecord(token -> { })));
        assertThat(human.has("principalType")).isFalse();
        assertThat(human.has("actorSub")).isFalse();
    }

    @Test
    void aTokenWhosePrincipalClaimsContradictEachOtherIs401() throws Exception {
        String call = """
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"get_record","arguments":{"recordId":"%s"}}}"""
                .formatted(StubBackend.OPEN_1);
        Map<String, TestGateway.Token> refused = Map.of(
                "pty user", GATEWAY.user().extraClaim("pty", "user"),
                "delegated without act", GATEWAY.user().extraClaim("pty", "delegated"),
                "agent with act", GATEWAY.user().extraClaim("pty", "agent").extraClaim("act", Map.of("sub", AGENT_SUB, "azp", AGENT_CLIENT)),
                "act without pty", GATEWAY.user().extraClaim("act", Map.of("sub", AGENT_SUB, "azp", AGENT_CLIENT)));
        String session = session();
        for (Map.Entry<String, TestGateway.Token> token : refused.entrySet()) {
            Map<String, String> headers = new java.util.LinkedHashMap<>(token.getValue().tool("get_record").mint().headers());
            headers.put("X-MCP-Credential", PAT);
            headers.put("X-MCP-Credential-Type", SampleScope.CREDENTIAL_TYPE.wireName());

            assertThat(post(call, headers, session).statusCode()).as(token.getKey()).isEqualTo(401);
        }
        assertThat(BACKEND.authorizations()).isEmpty();

        Map<String, String> control = new java.util.LinkedHashMap<>(GATEWAY.user().tool("get_record").mint().headers());
        control.put("X-MCP-Credential", PAT);
        control.put("X-MCP-Credential-Type", SampleScope.CREDENTIAL_TYPE.wireName());
        assertThat(post(call, control, session).statusCode()).as("the same token without pty and act").isEqualTo(200);
    }

    private McpSchema.CallToolResult getRecord(Consumer<TestGateway.Token> principal) {
        try (McpSyncClient client = clientWithToken(token -> {
            reader(token);
            principal.accept(token);
        }, PAT)) {
            return call(client, "get_record", Map.of("recordId", StubBackend.OPEN_1));
        }
    }

    private static JsonNode toolCall(List<JsonNode> events) {
        return events.stream().filter(event -> "tool_call".equals(event.path("event").asString())).findFirst().orElseThrow();
    }

    private static List<JsonNode> audit(Runnable block) {
        Logger logger = (Logger) LoggerFactory.getLogger("pl.aiwcorpo.mcp.audit");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            block.run();
        } finally {
            logger.detachAppender(appender);
        }
        return appender.list.stream().map(event -> JSON.readTree(event.getFormattedMessage())).toList();
    }

    private String session() throws Exception {
        HttpResponse<String> initialize = post("""
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18",
                "capabilities":{},"clientInfo":{"name":"primitives-test","version":"1"}}}""", GATEWAY.user().mint().headers(), null);
        assertThat(initialize.statusCode()).isEqualTo(200);
        String session = initialize.headers().firstValue("Mcp-Session-Id").orElseThrow();
        post("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", GATEWAY.user().mint().headers(), session);
        return session;
    }

    private HttpResponse<String> post(String body, Map<String, String> headers, String session) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl() + ENDPOINT)).timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        headers.forEach(request::header);
        if (session != null) {
            request.header("Mcp-Session-Id", session);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
