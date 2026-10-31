package pl.aiwcorpo.mcp.example.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code search_records}: the server builds the backend query, the requested project narrows it, and the hits are
 * filtered by the policy, so a search never shows what {@code get_record} would refuse.
 */
class SearchRecordsToolTest extends ServerTestSupport {

    private static final String PAT = "Bearer example-pat-of-jkowalski-7f3a";

    @BeforeEach
    void resetBackend() {
        BACKEND.reset();
    }

    private static Map<String, Object> search(String project, String text) {
        return Map.of("project", project, "text", text);
    }

    @Test
    void listsTheAllowedRecordsOfTheProject() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            McpSchema.CallToolResult result = call(client, "search_records", search(SampleScope.OPEN_PROJECT, "report"));

            assertThat(result.isError()).isFalse();
            assertThat(text(result)).contains(StubBackend.OPEN_1, StubBackend.OPEN_2).contains("\"kind\":\"untrusted-data\"");
        }
    }

    @Test
    void aRecordMovedToTheRestrictedProjectIsRemovedFromTheList() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            McpSchema.CallToolResult result = call(client, "search_records", search(SampleScope.OPEN_PROJECT, "report"));

            assertThat(result.isError()).isFalse();
            assertThat(text(result)).as("the search index still lists the moved record under its old project")
                    .doesNotContain(StubBackend.MOVED, "Payroll");
        }
    }

    @Test
    void aRestrictedProjectYieldsNoRecords() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            McpSchema.CallToolResult result = call(client, "search_records", search(SampleScope.RESTRICTED_PROJECT, "report"));

            assertThat(result.isError()).isFalse();
            assertThat(text(result)).doesNotContain(StubBackend.RESTRICTED_5, "Salary");
        }
    }

    @Test
    void onlyTheRowsOfTheRequestedProjectAreReturned() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            String open = text(call(client, "search_records", search(SampleScope.OPEN_PROJECT, "report")));
            String other = text(call(client, "search_records", search(SampleScope.OTHER_PROJECT, "report")));

            assertThat(open).doesNotContain(StubBackend.OTHER_7);
            assertThat(other).doesNotContain(StubBackend.OPEN_1);
            if (!SampleScope.ONLY_GRANTED_PROJECTS) {
                assertThat(other).contains(StubBackend.OTHER_7);
            }
        }
    }

    @Test
    void theRequestedProjectTravelsInsideTheQueryWhereTheSystemHasAQueryLanguage() {
        org.junit.jupiter.api.Assumptions.assumeTrue(SampleScope.QUERY_CARRIES_PROJECT);
        try (McpSyncClient client = clientWithCredential(PAT)) {
            call(client, "search_records", search(SampleScope.OPEN_PROJECT, "report"));

            assertThat(BACKEND.searches()).singleElement().asString()
                    .containsPattern("(project|space) in \\(\"" + SampleScope.OPEN_PROJECT + "\"\\)");
        }
    }

    @Test
    void valuesFromTheModelStayLiteralsInTheQuery() {
        String hostile = "x\" OR project = " + SampleScope.RESTRICTED_PROJECT + " OR text ~ \"";
        try (McpSyncClient client = clientWithCredential(PAT)) {
            call(client, "search_records", search(SampleScope.OPEN_PROJECT, hostile));

            assertThat(BACKEND.searches()).singleElement().asString()
                    .as("the quote is escaped, so the value cannot close the literal")
                    .contains("\\\" OR project")
                    .doesNotContain("x\" OR project");
        }
    }

    @Test
    void aContainerTheGroupsDoNotGrantIsNotSearchedWhereTheSystemIgnoresTheUsersRights() {
        org.junit.jupiter.api.Assumptions.assumeTrue(SampleScope.ONLY_GRANTED_PROJECTS);
        try (McpSyncClient client = clientWithCredential(PAT)) {
            McpSchema.CallToolResult result = call(client, "search_records", search(SampleScope.OTHER_PROJECT, "report"));

            assertThat(result.isError()).isFalse();
            assertThat(text(result)).contains("\"hits\":[]");
            assertThat(BACKEND.searches()).isEmpty();
        }
    }

    @Test
    void theHitsAreResolvedWithOneBatchCall() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            call(client, "search_records", search(SampleScope.OPEN_PROJECT, "report"));

            assertThat(BACKEND.batches()).as("one lookup for the whole result").hasSize(1);
        }
    }

    @Test
    void theBackendGetsTheCredentialOfTheCaller() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            call(client, "search_records", search(SampleScope.OPEN_PROJECT, "report"));

            assertThat(BACKEND.authorizations()).isNotEmpty().containsOnly(PAT);
        }
    }

    @Test
    void withoutCredentialAsksToConnectTheAccount() {
        try (McpSyncClient client = clientWithCredential(null)) {
            McpSchema.CallToolResult result = call(client, "search_records", search(SampleScope.OPEN_PROJECT, "report"));

            assertCredentialRequired(result);
            assertThat(BACKEND.searches()).isEmpty();
        }
    }

    @Test
    void credentialRejectedByTheBackendAsksToReconnect() {
        try (McpSyncClient client = clientWithCredential(StubBackend.EXPIRED_CREDENTIAL)) {
            assertCredentialRequired(call(client, "search_records", search(SampleScope.OPEN_PROJECT, "report")));
        }
    }

    @Test
    void aCallerWithoutTheGroupGetsAccessDenied() {
        try (McpSyncClient client = clientWithToken(user -> user.username("jkowalski").groups(List.of("mcp-nobody")), PAT)) {
            McpSchema.CallToolResult result = call(client, "search_records", search(SampleScope.OPEN_PROJECT, "report"));

            assertThat(result.isError()).isTrue();
            assertThat(text(result)).contains("\"error\":\"access_denied\"");
            assertThat(BACKEND.searches()).isEmpty();
        }
    }

    @Test
    void aFailingBackendIsNotAnEmptyResult() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            McpSchema.CallToolResult result = call(client, "search_records", search(SampleScope.OPEN_PROJECT, StubBackend.FAILING_TEXT));

            assertThat(result.isError()).isTrue();
            assertThat(text(result)).contains("\"error\":\"internal_error\"").doesNotContain("boom").doesNotContain("example-pat");
        }
    }

    @Test
    void invalidArgumentsAreRejectedBeforeTheBackend() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            for (Map<String, Object> arguments : List.of(search("../" + SampleScope.RESTRICTED_PROJECT, "report"), search(SampleScope.OPEN_PROJECT, ""),
                    search(SampleScope.OPEN_PROJECT, "a".repeat(201)), Map.<String, Object>of("project", SampleScope.OPEN_PROJECT),
                    Map.<String, Object>of("project", SampleScope.OPEN_PROJECT, "text", "report", "jql", "project = " + SampleScope.RESTRICTED_PROJECT))) {
                McpSchema.CallToolResult result = call(client, "search_records", arguments);

                assertThat(result.isError()).as(arguments.toString()).isTrue();
            }
            assertThat(BACKEND.searches()).isEmpty();
        }
    }

    @Test
    void isListedAsReadOnlyWithAClosedSchema() {
        try (McpSyncClient client = clientWithCredential(PAT)) {
            McpSchema.Tool tool = client.listTools().tools().stream()
                    .filter(t -> t.name().equals("search_records")).findFirst().orElseThrow();

            assertThat(tool.annotations().readOnlyHint()).isTrue();
            assertThat(tool.title()).isEqualTo("Szukaj rekordów");
            assertThat(tool.inputSchema()).containsEntry("additionalProperties", false);
            assertThat(tool.inputSchema().get("properties")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                    .containsOnlyKeys("project", "text");
        }
    }
}
