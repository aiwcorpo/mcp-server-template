package pl.aiwcorpo.mcp.example.application.service;

import java.util.List;
import pl.aiwcorpo.mcp.example.core.domain.ExampleRecordHit;
import pl.aiwcorpo.mcp.example.core.port.in.ExampleUseCase;
import pl.aiwcorpo.mcp.example.core.port.out.ExampleResourcePort;

/** Use cases of the server. A plain class: mcp-app makes it a bean, so this module stays free of Spring. */
public class ExampleService implements ExampleUseCase {

    private final ExampleResourcePort resource;

    public ExampleService(ExampleResourcePort resource) {
        this.resource = resource;
    }

    @Override
    public String resolveIndices(String expression) {
        return resource.resolveIndices(expression);
    }

    @Override
    public List<ExampleRecordHit> searchRecords(List<String> indices, String text) {
        List<ExampleRecordHit> hits = resource.search(indices, text, MAX_HITS);
        return hits.size() > MAX_HITS ? hits.subList(0, MAX_HITS) : hits;
    }
}
