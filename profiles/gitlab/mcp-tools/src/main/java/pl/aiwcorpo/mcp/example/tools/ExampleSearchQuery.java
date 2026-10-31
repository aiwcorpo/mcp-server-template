package pl.aiwcorpo.mcp.example.tools;

import java.util.Optional;
import pl.aiwcorpo.mcp.platform.resource.QueryScope;

/**
 * The backend query of {@code search_records}, built by the server from the scope of the policy and the values the
 * model supplied; the model never supplies a fragment of the query language. {@code new-server.sh} replaces this file
 * with the one of the generated system.
 *
 * <p>GitLab has no query builder in the starter and applies the rights of the user's own token, so the query is the
 * requested group and the text and nothing else; the hits are filtered afterwards by their {@code @ResourceRef}.
 * {@code scope} is not read here; a system that must limit the query to grants reads it as the service desk does.
 * Values are quoted with {@link QueryScope#quote}. The query syntax below is the sample backend's; replace it with the
 * parameters of the real API.
 */
final class ExampleSearchQuery {

    private ExampleSearchQuery() {
    }

    /** Empty when there is nothing to ask the system. */
    static Optional<String> of(QueryScope scope, String project, String text) {
        return Optional.of("project = " + QueryScope.quote(project) + " AND text ~ " + QueryScope.quote(text));
    }
}
