package pl.aiwcorpo.mcp.example.core.domain;

import java.util.Collection;

/**
 * How this server's records map to the resource types of the policy (C7). {@code new-server.sh} replaces this
 * file with the one of the generated system; the Jira variant is the template's own.
 */
public final class ExampleResourceModel {

    /** Type of the record itself: the argument of {@code get_record} and the id of a search hit. */
    public static final String RECORD_TYPE = "ServiceDesk::Ticket";

    /** Type of the container, the one a search is narrowed to ({@code QueryScope}). */
    public static final String PARENT_TYPE = "ServiceDesk::Queue";

    /**
     * Contract pattern (common.schema.json) and bound of the record id argument; the starter also checks every
     * {@code @ResourceRef} value against the pattern of its type before the resolver sees it.
     */
    public static final String RECORD_ID_CONTRACT = "ServiceDeskTicketId";
    public static final int RECORD_ID_MAX_LENGTH = 64;

    /** The same for the argument that names the container in a search. */
    public static final String PARENT_ID_CONTRACT = "ServiceDeskQueueId";
    public static final int PARENT_ID_MAX_LENGTH = 64;

    private ExampleResourceModel() {
    }

    /**
     * Whether a search in {@code parentId} stays inside the ids of a scope ({@code scopeIds}): a queue is inside
     * when it is one of the granted queues.
     */
    public static boolean within(Collection<String> scopeIds, String parentId) {
        return scopeIds.contains(parentId);
    }
}
