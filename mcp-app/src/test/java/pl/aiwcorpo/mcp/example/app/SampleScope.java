package pl.aiwcorpo.mcp.example.app;

import java.util.List;
import java.util.Map;
import pl.aiwcorpo.mcp.platform.security.BackendCredential;

/**
 * What the tests assume about the system: how ids look (they must match the idPattern of the resource types in
 * {@code mcp/policies/resource-types.json}, which the starter checks before any resolver runs), which containers
 * exist in the stub backend and which group the scope mapping of the tests ties to which container.
 */
final class SampleScope {

    /**
     * Type of the credential the gateway resolves for this system (the {@code credential.type} of the server in the
     * gateway and of the system in the broker): the server's first accepted type and the {@code X-MCP-Credential-Type}
     * of test calls.
     */
    static final BackendCredential.Type CREDENTIAL_TYPE = BackendCredential.Type.USER_SECRET;

    static final String OPEN_PROJECT = "ABC";
    static final String OTHER_PROJECT = "DEF";
    static final String RESTRICTED_PROJECT = "HR";
    static final String UNCLASSIFIED_PROJECT = "NOC";

    /**
     * The group an administrator maps to the scope of {@link #OPEN_PROJECT}: a change needs it, reading needs only the
     * read group. This is the scope mapping the gateway serves to the server in the tests.
     */
    static final String OPEN_PROJECT_GROUP = "mcp-example-proj-ABC";
    static final Map<String, List<String>> SCOPE_MAPPING = Map.of(OPEN_PROJECT_GROUP, List.of("Example::Project/ABC"));

    /** Id of record number {@code number} in {@code project}, in the shape of the resource type. */
    static String id(String project, int number) {
        return project + "-" + number;
    }

    private SampleScope() {
    }
}
