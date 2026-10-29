package pl.aiwcorpo.mcp.example.core.port.in;

import java.util.List;
import pl.aiwcorpo.mcp.example.core.domain.ExampleRecordHit;

/** Inbound port: what the server can do, in business terms. Implemented in mcp-application, called by mcp-tools. */
public interface ExampleUseCase {

    /** Upper bound of a search result, applied here and sent to the backend. */
    int MAX_HITS = 50;

    /** The answer of the system to the resolution of an index expression, for {@code QueryScope#resolveIndexNames}. */
    String resolveIndices(String expression);

    /** Rows of a search over the concrete index names, at most {@link #MAX_HITS}. */
    List<ExampleRecordHit> searchRecords(List<String> indices, String text);
}
