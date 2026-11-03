package pl.aiwcorpo.mcp.example.core.port.in;

import java.util.List;
import pl.aiwcorpo.mcp.example.core.domain.CommentPage;
import pl.aiwcorpo.mcp.example.core.domain.ExampleRecord;
import pl.aiwcorpo.mcp.example.core.domain.ExampleRecordHit;
import pl.aiwcorpo.mcp.example.core.domain.RecordComment;

/** Inbound port: what the server can do, in business terms. Implemented in mcp-application, called by mcp-tools. */
public interface ExampleUseCase {

    /** Upper bound of a search result, applied here and sent to the backend. */
    int MAX_HITS = 50;

    /**
     * Rows of a query the tool built from the scope of the policy and the values of the model, at most
     * {@link #MAX_HITS}. The backend limits the rest to the user's own rights.
     */
    List<ExampleRecordHit> searchRecords(String query);

    /** Rows of one page of comments, the size a tool asks for after the filter of the use case. */
    int COMMENT_PAGE_SIZE = 10;

    /**
     * A page of the comments of a record from position {@code start}. A comment the backend files under another record
     * is dropped here, so a page can be shorter than the backend page it came from.
     */
    CommentPage listComments(String recordId, int start);

    /** The comment of the record; {@code NotFoundOrDeniedException} when it is missing or belongs to another record. */
    RecordComment getComment(String recordId, String commentId);

    /** Sets the title (the same call twice leaves the same state); {@code NotFoundOrDeniedException} when the record vanished. */
    ExampleRecord updateTitle(String recordId, String title);
}
