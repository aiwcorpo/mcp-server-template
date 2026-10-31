package pl.aiwcorpo.mcp.example.core.domain;

import java.util.Collection;

/**
 * How this server's records map to the resource types of the policy (C7). {@code new-server.sh} replaces this
 * file with the one of the generated system; the Jira variant is the template's own.
 */
public final class ExampleResourceModel {

    /** Type of the record itself: the argument of {@code get_record} and the id of a search hit. */
    public static final String RECORD_TYPE = "OpenSearch::Index";

    /** Type a search is narrowed to ({@code QueryScope}): index prefixes. An index has no container. */
    public static final String PARENT_TYPE = "OpenSearch::Index";

    /**
     * Contract pattern (common.schema.json) and bound of the record id argument; the starter also checks every
     * {@code @ResourceRef} value against the pattern of its type before the resolver sees it.
     */
    public static final String RECORD_ID_CONTRACT = "OpenSearchIndexName";
    public static final int RECORD_ID_MAX_LENGTH = 255;

    /** The same for the argument that names the container in a search. */
    public static final String PARENT_ID_CONTRACT = "OpenSearchIndexPrefix";
    public static final int PARENT_ID_MAX_LENGTH = 255;

    private ExampleResourceModel() {
    }

    /**
     * Whether a search in {@code parentId} (an index prefix) stays inside the ids of a scope ({@code scopeIds}):
     * a prefix of the scope covers the index named exactly like it and every index starting with {@code <prefix>-}.
     */
    public static boolean within(Collection<String> scopeIds, String parentId) {
        return scopeIds.stream().anyMatch(id -> parentId.equals(id) || parentId.startsWith(id + "-"));
    }
}
