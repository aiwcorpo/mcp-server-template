package pl.aiwcorpo.mcp.example.core.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import pl.aiwcorpo.mcp.example.core.domain.ExampleRecord;
import pl.aiwcorpo.mcp.example.core.domain.ExampleRecordHit;
import pl.aiwcorpo.mcp.example.core.domain.ServerInfo;

/** Outbound port to the backend system, implemented in mcp-integration. */
public interface ExampleResourcePort {

    /** Empty when the backend does not show the record to the caller, whether it exists or not. */
    Optional<ExampleRecord> findById(String recordId);

    /** The records among {@code recordIds} the backend shows to the caller, by id; one call for the whole set. */
    Map<String, ExampleRecord> findAllById(Collection<String> recordIds);

    /** The raw answer of {@code GET _resolve/index/<expression>}: indices, aliases and data streams the patterns match. */
    String resolveIndices(String expression);

    /**
     * Searches the concrete indices {@code indices} (never patterns) for {@code text}, at most {@code limit} rows;
     * {@code text} is passed as one quoted literal built by the server, never as query syntax from the model.
     */
    List<ExampleRecordHit> search(List<String> indices, String text, int limit);

    /** What the backend says about itself, for the version probe; no credential (the probe runs outside any request). */
    ServerInfo serverInfo();
}
