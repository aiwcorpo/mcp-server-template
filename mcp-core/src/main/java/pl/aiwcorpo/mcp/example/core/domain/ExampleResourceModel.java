package pl.aiwcorpo.mcp.example.core.domain;

/**
 * Names of the resource types this server declares in {@code mcp/policies/resource-types.json}: the string in
 * {@code @ResourceRef}, in the resolver and in the descriptions the policy reads. Ids and their patterns are not
 * repeated here; the declaration is their only source.
 */
public final class ExampleResourceModel {

    /** Type of the record itself: the argument of {@code get_record} and the id of a search hit. */
    public static final String RECORD_TYPE = "Example::Record";

    /** Type of the container, the one a search is narrowed to ({@code QueryScope}) and a scope is granted on. */
    public static final String PARENT_TYPE = "Example::Project";

    /** Bounds of the id arguments: the schema of a tool needs a length, the pattern comes from the declaration. */
    public static final int RECORD_ID_MAX_LENGTH = 16;
    public static final int PARENT_ID_MAX_LENGTH = 10;

    private ExampleResourceModel() {
    }
}
