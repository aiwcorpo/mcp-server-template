package pl.aiwcorpo.mcp.application.service;

import pl.aiwcorpo.mcp.core.domain.ExampleRecord;
import pl.aiwcorpo.mcp.core.port.in.ExampleUseCase;
import pl.aiwcorpo.mcp.core.port.out.ExampleResourcePort;

/**
 * Use-case implementation. Implements an inbound port, orchestrates an outbound port. Owns coarse
 * authorization and orchestration; contains NO transport/MCP/Spring concerns.
 *
 * <p>Kept as a plain POJO so this module depends only on mcp-core — it is turned into a Spring bean
 * by explicit wiring in mcp-app ({@code ApplicationWiringConfiguration}).
 */
public class ExampleService implements ExampleUseCase {

    private static final int MAX_NAME_LENGTH = 120;

    private final ExampleResourcePort resource;

    public ExampleService(ExampleResourcePort resource) {
        this.resource = resource;
    }

    @Override
    public ExampleRecord getById(String id) {
        return resource.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown record: " + id));
    }

    @Override
    public ExampleRecord rename(String id, String newName) {
        if (newName == null || newName.isBlank() || newName.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("name must be 1.." + MAX_NAME_LENGTH + " characters");
        }
        ExampleRecord current = getById(id);
        return resource.save(new ExampleRecord(current.id(), newName.trim(), current.status()));
    }

    @Override
    public int purgeAll() {
        return resource.deleteAll();
    }
}
