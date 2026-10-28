package pl.aiwcorpo.mcp.example.app;

import java.util.List;

/**
 * What differs between the systems in the tests of the resource policy: how ids look (they must match the contract
 * pattern of the resource type, which the starter checks before any resolver runs), the key of the link narrowing, and
 * whether an unreported classification is refused. {@code new-server.sh} replaces this file with the one of the
 * generated system; the Jira variant is the template's own.
 */
final class SampleScope {

    /** Key in the {@code vsc} claim with which a virtual server link narrows the container type (C2). */
    static final String LINK_KEY = "jiraProjects";

    /**
     * Whether a record whose container classification the backend did not report is refused. True where the policy
     * requires the attribute (Jira category, Confluence labels, GitLab ancestors); false where it is optional
     * (ServiceDesk queue).
     */
    static final boolean UNKNOWN_CLASSIFICATION_REFUSED = true;

    /** Groups of the test caller besides the read group: grants the policy needs to read anything (OpenSearch). */
    static final List<String> EXTRA_GROUPS = List.of();

    /** Searching this project makes the backend fail. */
    static final String FAIL_PROJECT = "FAIL";

    /** Whether the server hands the narrowing of a link to the system inside the query (JQL, CQL) instead of checking it itself. */
    static final boolean QUERY_CARRIES_LINK = true;

    static final String OPEN_PROJECT = "ABC";
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
