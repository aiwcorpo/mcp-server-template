package pl.aiwcorpo.mcp.example.core.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import pl.aiwcorpo.mcp.example.core.domain.ExampleRecord;
import pl.aiwcorpo.mcp.example.core.domain.CommentPage;
import pl.aiwcorpo.mcp.example.core.domain.ExampleRecordHit;
import pl.aiwcorpo.mcp.example.core.domain.RecordComment;
import pl.aiwcorpo.mcp.example.core.domain.ServerInfo;

/** Outbound port to the backend system, implemented in mcp-integration. */
public interface ExampleResourcePort {

    /** Empty when the backend does not show the record to the caller, whether it exists or not. */
    Optional<ExampleRecord> findById(String recordId);

    /** The records among {@code recordIds} the backend shows to the caller, by id; one call for the whole set. */
    Map<String, ExampleRecord> findAllById(Collection<String> recordIds);

    /**
     * Runs a query the server built (never one from the model), at most {@code limit} rows; the backend may add linked
     * records of other projects.
     */
    List<ExampleRecordHit> search(String query, int limit);

    /**
     * What the backend says about itself. Called by the version probe outside any request, so without a credential: it
     * reads only what the backend tells everybody.
     */
    ServerInfo serverInfo();

    /** One page of the comments of a record, from position {@code start}, at most {@code limit} rows. */
    CommentPage listComments(String recordId, int start, int limit);

    /**
     * The comment, only when the backend says it has this id and belongs to {@code recordId}; empty otherwise, whether
     * it does not exist, is hidden or sits under another record.
     */
    Optional<RecordComment> findComment(String recordId, String commentId);

    /** Sets the title of the record and returns the record as the backend has it afterwards. Empty when it is not visible. */
    Optional<ExampleRecord> updateTitle(String recordId, String title);
}
