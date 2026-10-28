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
    public List<ExampleRecordHit> searchRecords(String project, String text) {
        List<ExampleRecordHit> hits = resource.search(project, text, MAX_HITS);
        return hits.size() > MAX_HITS ? hits.subList(0, MAX_HITS) : hits;
    }
}
