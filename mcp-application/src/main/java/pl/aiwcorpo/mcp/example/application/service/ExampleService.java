package pl.aiwcorpo.mcp.example.application.service;

import java.util.List;
import pl.aiwcorpo.mcp.example.core.domain.ExampleRecordHit;
import pl.aiwcorpo.mcp.example.core.domain.ExampleResourceModel;
import pl.aiwcorpo.mcp.example.core.port.in.ExampleUseCase;
import pl.aiwcorpo.mcp.example.core.port.out.ExampleResourcePort;

/** Use cases of the server. A plain class: mcp-app makes it a bean, so this module stays free of Spring. */
public class ExampleService implements ExampleUseCase {

    private final ExampleResourcePort resource;

    public ExampleService(ExampleResourcePort resource) {
        this.resource = resource;
    }

    /**
     * The project is not a condition of the query: the fields a query may filter on are fixed by the contracts and a
     * container is not one of them. The rows are narrowed to the requested project here, so the backend limit counts
     * rows of every project the caller sees; a row without a project is dropped.
     */
    @Override
    public List<ExampleRecordHit> searchRecords(String query, String project) {
        return resource.search(query, MAX_HITS).stream()
                .filter(hit -> hit.project() != null && ExampleResourceModel.withinLink(List.of(project), hit.project()))
                .limit(MAX_HITS)
                .toList();
    }
}
