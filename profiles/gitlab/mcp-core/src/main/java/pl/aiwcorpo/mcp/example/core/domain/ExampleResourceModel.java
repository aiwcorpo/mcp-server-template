package pl.aiwcorpo.mcp.example.core.domain;

import java.util.Collection;

/**
 * How this server's records map to the resource types of the policy (C7). {@code new-server.sh} replaces this
 * file with the one of the generated system; the Jira variant is the template's own.
 */
public final class ExampleResourceModel {

    /** Type of the record itself: the argument of {@code get_record} and the id of a search hit. */
    public static final String RECORD_TYPE = "GitLab::Project";

    /** Type of the container, the one a virtual server link narrows ({@code QueryScope}). */
    public static final String PARENT_TYPE = "GitLab::Group";

    /**
     * Contract pattern (common.schema.json) and bound of the record id argument; the starter also checks every
     * {@code @ResourceRef} value against the pattern of its type before the resolver sees it.
     */
    public static final String RECORD_ID_CONTRACT = "GitLabProjectPath";
    public static final int RECORD_ID_MAX_LENGTH = 1024;

    /** The same for the argument that names the container in a search. */
    public static final String PARENT_ID_CONTRACT = "GitLabGroupPath";
    public static final int PARENT_ID_MAX_LENGTH = 1024;

    private ExampleResourceModel() {
    }

    /**
     * Whether a search in {@code parentId} stays inside the narrowing of the link ({@code linkIds}): groups nest, so a
     * group is inside when it is a linked group or lies below one.
     */
    public static boolean withinLink(Collection<String> linkIds, String parentId) {
        return linkIds.stream().anyMatch(link -> parentId.equals(link) || parentId.startsWith(link + "/"));
    }
}
