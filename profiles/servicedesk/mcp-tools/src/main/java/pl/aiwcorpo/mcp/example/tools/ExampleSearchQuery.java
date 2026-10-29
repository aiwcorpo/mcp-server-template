package pl.aiwcorpo.mcp.example.tools;

import java.util.Optional;
import pl.aiwcorpo.mcp.example.core.domain.ExampleResourceModel;
import pl.aiwcorpo.mcp.platform.resource.QueryScope;

/**
 * The backend query of {@code search_records}, built by the server from the scope of the policy and the values the
 * model supplied; the model never supplies a fragment of the query language. {@code new-server.sh} replaces this file
 * with the one of the generated system.
 *
 * <p>The service desk has no query builder in the starter, and its calls use the account of the service (credential
 * type {@code service-account}), which sees every queue: the rights of the user are not applied by the system. The
 * queue is therefore asked for only when the groups of the caller grant it (the grants come from the policy bundle
 * alone, never from the configuration of the server) and the virtual server link, if any, covers it; with no grant the
 * system is not called. Values are quoted with {@link QueryScope#quote}. The query syntax below is the sample
 * backend's; replace it with the parameters of the real API (the scope checks stay).
 */
final class ExampleSearchQuery {

    private ExampleSearchQuery() {
    }

    /** Empty when there is nothing to ask the system. */
    static Optional<String> of(QueryScope scope, String project, String text) {
        if (scope.nothingToQuery()) {
            return Optional.empty();
        }
        boolean outsideLink = scope.linkIds().map(link -> !ExampleResourceModel.withinLink(link, project)).orElse(false);
        if (outsideLink || !ExampleResourceModel.withinLink(scope.grantedIds(), project)) {
            return Optional.empty();
        }
        return Optional.of("project = " + QueryScope.quote(project) + " AND text ~ " + QueryScope.quote(text));
    }
}
