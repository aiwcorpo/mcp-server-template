package pl.aiwcorpo.mcp.example.application.service;

import pl.aiwcorpo.mcp.example.core.domain.ExampleRecord;
import pl.aiwcorpo.mcp.example.core.domain.RecordNotFoundException;
import pl.aiwcorpo.mcp.example.core.port.in.ExampleUseCase;
import pl.aiwcorpo.mcp.example.core.port.out.ExampleResourcePort;

/** Use cases of the server. A plain class: mcp-app makes it a bean, so this module stays free of Spring. */
public class ExampleService implements ExampleUseCase {

    private final ExampleResourcePort resource;

    public ExampleService(ExampleResourcePort resource) {
        this.resource = resource;
    }

    @Override
    public ExampleRecord getRecord(String recordId) {
        return resource.findById(recordId).orElseThrow(RecordNotFoundException::new);
    }
}
