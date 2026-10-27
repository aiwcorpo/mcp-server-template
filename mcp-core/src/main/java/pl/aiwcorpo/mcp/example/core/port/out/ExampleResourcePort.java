package pl.aiwcorpo.mcp.example.core.port.out;

import java.util.Optional;
import pl.aiwcorpo.mcp.example.core.domain.ExampleRecord;

/** Outbound port to the backend system, implemented in mcp-integration. */
public interface ExampleResourcePort {

    /** Empty when the backend does not show the record to the caller, whether it exists or not. */
    Optional<ExampleRecord> findById(String recordId);
}
