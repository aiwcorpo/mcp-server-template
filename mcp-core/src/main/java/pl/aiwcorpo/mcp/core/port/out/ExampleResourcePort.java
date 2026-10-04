package pl.aiwcorpo.mcp.core.port.out;

import pl.aiwcorpo.mcp.core.domain.ExampleRecord;
import java.util.Optional;

/**
 * Outbound port. Declared here in the domain, IMPLEMENTED in mcp-integration.
 * The domain depends on this interface; the concrete HTTP/DB client is an implementation detail.
 */
public interface ExampleResourcePort {

    Optional<ExampleRecord> findById(String id);

    /** Stores the record under its id, replacing the previous version. */
    ExampleRecord save(ExampleRecord record);

    /** Removes every record and returns how many there were. */
    int deleteAll();
}
