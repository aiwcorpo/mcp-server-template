package pl.aiwcorpo.mcp.example.core.port.in;

import java.util.List;
import pl.aiwcorpo.mcp.example.core.domain.ExampleRecordHit;

/** Inbound port: what the server can do, in business terms. Implemented in mcp-application, called by mcp-tools. */
public interface ExampleUseCase {

    /** Upper bound of a search result, applied here and sent to the backend. */
    int MAX_HITS = 50;

    /**
     * Rows of a query the tool built from the scope of the policy and the values of the model, at most
     * {@link #MAX_HITS}, of the requested {@code project} only. The backend limits the rest to the user's own rights.
     */
    List<ExampleRecordHit> searchRecords(String query, String project);
}
