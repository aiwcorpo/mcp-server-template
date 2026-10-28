package pl.aiwcorpo.mcp.example.core.port.out;

import java.util.List;
import java.util.Optional;
import pl.aiwcorpo.mcp.example.core.domain.ExampleRecord;
import pl.aiwcorpo.mcp.example.core.domain.ExampleRecordHit;

/** Outbound port to the backend system, implemented in mcp-integration. */
public interface ExampleResourcePort {

    /** Empty when the backend does not show the record to the caller, whether it exists or not. */
    Optional<ExampleRecord> findById(String recordId);

    /** Matches of {@code text} in {@code project}, at most {@code limit}; the backend may add linked records of other projects. */
    List<ExampleRecordHit> search(String project, String text, int limit);
}
