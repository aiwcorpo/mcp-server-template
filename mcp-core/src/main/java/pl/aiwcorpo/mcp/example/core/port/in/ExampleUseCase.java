package pl.aiwcorpo.mcp.example.core.port.in;

import java.util.List;
import pl.aiwcorpo.mcp.example.core.domain.ExampleRecordHit;

/** Inbound port: what the server can do, in business terms. Implemented in mcp-application, called by mcp-tools. */
public interface ExampleUseCase {

    /** Upper bound of a search result, applied here and sent to the backend. */
    int MAX_HITS = 50;

    /**
     * Records of one project whose title matches {@code text}, at most {@link #MAX_HITS}. The caller has already
     * limited {@code project} to what the policy allows; the backend limits the rest to the user's own rights.
     */
    List<ExampleRecordHit> searchRecords(String project, String text);
}
