package pl.aiwcorpo.mcp.example.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * OpenSearch searches go to concrete index names, never to the patterns of the scope: the patterns are sent to
 * {@code _resolve/index} only, and what the answer lists as an alias is not queried (CC-160).
 */
class IndexResolutionTest extends ServerTestSupport {

    private static final String PAT = "Bearer example-pat-of-jkowalski-7f3a";

    @BeforeEach
    void resetBackend() {
        BACKEND.reset();
    }

    private static Map<String, Object> search(String prefix) {
        return Map.of("project", prefix, "text", "report");
    }

    @Test
    void thePatternsOfTheGrantsGoToTheResolutionOnly() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            call(client, "search_records", search(SampleScope.OPEN_PROJECT));

            assertThat(BACKEND.resolutions()).singleElement().asString()
                    .contains(SampleScope.OPEN_PROJECT + "-*", SampleScope.OTHER_PROJECT + "-*");
            assertThat(BACKEND.indexSearches()).singleElement().asString().doesNotContain("*");
        }
    }

    @Test
    void anAliasThatMatchesThePatternIsNotQueried() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            McpSchema.CallToolResult result = call(client, "search_records", search(SampleScope.OPEN_PROJECT));

            assertThat(result.isError()).isFalse();
            assertThat(BACKEND.indexSearches()).singleElement().asString()
                    .as("names of indices, the alias of another team's data is not among them")
                    .contains(StubBackend.OPEN_1, StubBackend.OPEN_2)
                    .doesNotContain(StubBackend.ALIAS, StubBackend.OTHER_7);
        }
    }

    @Test
    void theSearchCarriesTheParametersForAListOfNames() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            call(client, "search_records", search(SampleScope.OPEN_PROJECT));

            assertThat(BACKEND.indexSearches()).singleElement().asString()
                    .contains("expand_wildcards=open", "ignore_unavailable=true", "allow_no_indices=true");
        }
    }

    @Test
    void aResolutionThatAnswers404MeansNoIndexAndNoSearch() {
        BACKEND.resolveAnswersNotFound(true);
        try (McpSyncClient client = clientWithCredential(PAT)) {
            McpSchema.CallToolResult result = call(client, "search_records", search(SampleScope.OPEN_PROJECT));

            assertThat(result.isError()).isFalse();
            assertThat(text(result)).contains("\"hits\":[]");
            assertThat(BACKEND.indexSearches()).isEmpty();
        }
    }

    @Test
    void theResolutionAsksForOpenIndicesOnly() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            call(client, "search_records", search(SampleScope.OPEN_PROJECT));

            assertThat(BACKEND.resolutionQueries()).singleElement().asString().contains("expand_wildcards=open");
        }
    }

    @Test
    void aLinkOnAnotherPrefixLeavesNothingToAskFor() {
        try (McpSyncClient client = clientWithToken(user -> {
            reader(user);
            user.virtualServer("team-b-only", Map.of(SampleScope.LINK_KEY, List.of(SampleScope.OTHER_PROJECT)));
        }, PAT)) {
            McpSchema.CallToolResult result = call(client, "search_records", search(SampleScope.OPEN_PROJECT));

            assertThat(result.isError()).isFalse();
            assertThat(text(result)).contains("\"hits\":[]");
            assertThat(BACKEND.indexSearches()).isEmpty();
        }
    }

    @Test
    void aCallerWithNoIndexGrantDoesNotReachTheSystem() {
        try (McpSyncClient client = clientWithToken(user -> user.username("jkowalski").groups(List.of(READ_GROUP)), PAT)) {
            McpSchema.CallToolResult result = call(client, "search_records", search(SampleScope.OPEN_PROJECT));

            assertThat(result.isError()).isFalse();
            assertThat(text(result)).contains("\"hits\":[]");
            assertThat(BACKEND.resolutions()).isEmpty();
            assertThat(BACKEND.indexSearches()).isEmpty();
        }
    }
}
