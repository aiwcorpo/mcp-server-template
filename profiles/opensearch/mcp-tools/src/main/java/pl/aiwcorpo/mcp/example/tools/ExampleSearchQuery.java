package pl.aiwcorpo.mcp.example.tools;

import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;
import pl.aiwcorpo.mcp.example.core.domain.ExampleResourceModel;
import pl.aiwcorpo.mcp.platform.resource.QueryScope;

/**
 * Which indices a search of {@code search_records} may ask the system for. {@code new-server.sh} replaces this file with
 * the one of the generated system.
 *
 * <p>OpenSearch applies the rights of the exchanged token, but the platform does not rely on that for the scope: the
 * indices come from the grants of the caller and the link of the virtual server ({@link QueryScope}). The patterns of
 * the scope ({@code p} and {@code p-*}) are sent to {@code GET _resolve/index/} only; the answer is reduced by
 * {@link QueryScope#opensearchSearchTarget} to concrete index names (aliases, data streams and names outside the
 * contract pattern are never in the list) and then to the requested prefix. A tool that takes an index name as its argument passes it through
 * {@link QueryScope#resolveIndexArgument} with the same answer instead.
 */
final class ExampleSearchQuery {

    private ExampleSearchQuery() {
    }

    /** Empty when there is nothing to ask the system: no grant, a link that does not meet the grants, nothing under the prefix. */
    static List<String> indices(QueryScope scope, String prefix, UnaryOperator<String> resolveIndex) {
        if (scope.nothingToQuery()) {
            return List.of();
        }
        String resolved = resolveIndex.apply(scope.opensearchIndexExpression());
        // An empty target means "ask nothing": a request with an empty index list would search every index.
        return scope.opensearchSearchTarget(resolved)
                .map(target -> Arrays.stream(target.split(","))
                        .filter(name -> ExampleResourceModel.withinLink(List.of(prefix), name))
                        .toList())
                .orElse(List.of());
    }
}
