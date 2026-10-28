package pl.aiwcorpo.mcp.example.tools;

import java.util.Optional;
import pl.aiwcorpo.mcp.example.core.domain.ExampleResourceModel;
import pl.aiwcorpo.mcp.platform.resource.QueryScope;

/**
 * The backend query of {@code search_records}, built by the server from the scope of the policy and the values the
 * model supplied; the model never supplies a fragment of the query language. {@code new-server.sh} replaces this file
 * with the one of the generated system.
 *
 * <p>GitLab has no query builder in the starter, so the narrowing of the virtual server link is applied here: a project
 * outside the link is not asked for at all. Values are quoted with {@link QueryScope#quote}. The query syntax below is
 * the sample backend's; replace it with the parameters of the real API (the scope check stays).
 */
final class ExampleSearchQuery {

    private ExampleSearchQuery() {
    }

    /** Empty when there is nothing to ask the system. */
    static Optional<String> of(QueryScope scope, String project, String text) {
        boolean outsideLink = scope.linkIds().map(link -> !ExampleResourceModel.withinLink(link, project)).orElse(false);
        if (outsideLink) {
            return Optional.empty();
        }
        return Optional.of("project = " + QueryScope.quote(project) + " AND text ~ " + QueryScope.quote(text));
    }
}
