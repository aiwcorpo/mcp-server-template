package pl.aiwcorpo.mcp.example.app;

import java.util.List;
import pl.aiwcorpo.mcp.platform.security.BackendCredential;

/**
 * What differs between the systems in the tests of the resource policy: how ids look (they must match the contract
 * pattern of the resource type, which the starter checks before any resolver runs), and
 * whether an unreported classification is refused. {@code new-server.sh} replaces this file with the one of the
 * generated system; the Jira variant is the template's own.
 */
final class SampleScope {

    /**
     * Whether a record whose container classification the backend did not report is refused. True where the policy
     * requires the attribute (Jira category, Confluence labels, GitLab ancestors); false where it is optional
     * (ServiceDesk queue).
     */
    static final boolean UNKNOWN_CLASSIFICATION_REFUSED = false;

    /**
     * Type of the credential the gateway resolves for this system (the system's configuration in the gateway and the
     * broker): the server's {@code credential-type}, the {@code X-MCP-Credential-Type} of test calls.
     */
    static final BackendCredential.Type CREDENTIAL_TYPE = BackendCredential.Type.SERVICE_ACCOUNT;

    /**
     * Whether the system does not apply the user's rights (a service account sees everything), so that only what the
     * groups of the caller grant can be read or searched: ServiceDesk. Records of other containers are then
     * {@code not_found_or_denied}, and a search of one does not reach the system.
     */
    static final boolean ONLY_GRANTED_PROJECTS = true;

    /**
     * Whether the container of the unclassified record lies outside every scope of the caller, so that the record is
     * {@code not_found_or_denied} before its missing classification matters (Confluence space, Service Desk queue:
     * a record the caller's scopes do not cover and the policy cannot judge is hidden, not an internal error).
     */
    static final boolean UNCLASSIFIED_OUTSIDE_SCOPE = true;

    /**
     * Whether reading needs a scope in the policy bundle even for a caller with the read group: the service account of
     * Service Desk and OpenSearch, whose system does not apply the user's rights. Elsewhere the read group is enough.
     */
    static final boolean READ_REQUIRES_SCOPE = true;

    /** Groups of the test caller besides the read group: grants the policy needs to read anything (OpenSearch). */
    static final List<String> EXTRA_GROUPS = List.of("mcp-servicedesk-queue-42");

    /** Whether the server hands the requested project to the system inside the query (JQL, CQL) instead of checking it itself. */
    static final boolean QUERY_CARRIES_PROJECT = false;


    /**
     * Whether the server has the comment tools and {@code update_record}. False for OpenSearch, a read-only system
     * whose tools are the searches; its profile keeps only the version probe.
     */
    static final boolean FULL_TOOL_SET = true;

    /**
     * Groups that grant the scope of {@link #OPEN_PROJECT} in the example policy bundle of the contracts: a change needs
     * a grant, reading needs only the read group. Empty where {@link #EXTRA_GROUPS} already carries it.
     */
    static final List<String> WRITE_GRANTS = List.of();

    static final String OPEN_PROJECT = "42";
    static final String OTHER_PROJECT = "DEF";
    static final String RESTRICTED_PROJECT = "HR";
    static final String UNCLASSIFIED_PROJECT = "NOC";

    /** Id of record number {@code number} in {@code project}, in the shape of the resource type. */
    static String id(String project, int number) {
        return project + "-" + number;
    }

    private SampleScope() {
    }
}
