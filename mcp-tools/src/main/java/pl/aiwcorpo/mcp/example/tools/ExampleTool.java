package pl.aiwcorpo.mcp.example.tools;

import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import pl.aiwcorpo.mcp.example.core.domain.CommentPage;
import pl.aiwcorpo.mcp.example.core.domain.ExampleRecord;
import pl.aiwcorpo.mcp.example.core.domain.ExampleResourceModel;
import pl.aiwcorpo.mcp.example.core.port.in.ExampleUseCase;
import pl.aiwcorpo.mcp.platform.error.InvalidToolArgumentException;
import pl.aiwcorpo.mcp.platform.guard.RiskLevel;
import pl.aiwcorpo.mcp.platform.guard.ToolRisk;
import pl.aiwcorpo.mcp.platform.guard.ToolTitle;
import pl.aiwcorpo.mcp.platform.input.ArgumentLimits;
import pl.aiwcorpo.mcp.platform.paging.CursorCodec;
import pl.aiwcorpo.mcp.platform.paging.PageFiller;
import pl.aiwcorpo.mcp.platform.paging.PagedResult;
import pl.aiwcorpo.mcp.platform.resource.QueryScope;
import pl.aiwcorpo.mcp.platform.resource.ResourceContext;
import pl.aiwcorpo.mcp.platform.resource.ResourcePolicy;
import pl.aiwcorpo.mcp.platform.resource.ResourceRef;

/**
 * The MCP tools of this server. Each public {@code @Tool} method is a capability an AI client can call
 * through the gateway, so every argument is untrusted input from a model.
 *
 * <p>The gateway decides who may call a tool and asks for confirmations; the starter verifies that the call came
 * from the gateway for exactly this tool and evaluates the policy (C7) on the resources the tool names. What stays
 * here: a risk flag and a title on every tool, strict argument limits (published in the schema and enforced by the
 * starter, identifiers by the pattern of the contracts), every resource named by an explicit argument marked
 * {@link ResourceRef}, and searches whose query the server builds.
 */
@Service
public class ExampleTool {

    public static final int SEARCH_TEXT_MAX_LENGTH = 200;
    static final int TITLE_MAX_LENGTH = 255;
    /** Comment ids of the backend are numbers; the pattern keeps anything else out of the path of the request. */
    public static final String COMMENT_ID_PATTERN = "^[0-9]{1,12}$";
    static final int COMMENT_ID_MAX_LENGTH = 12;
    /**
     * Backend pages one call may read to fill a page that the filter of the use case thinned out: a short page with a
     * cursor would tell the caller that something was left out. Each read is a call to the backend, so the number is small.
     */
    static final int FILL_READS = 4;

    private final ExampleUseCase useCase;
    private final ResourcePolicy policy;
    private final CursorCodec cursors;
    private final ExampleWriteGate gate;

    /** The tool class for the build step that writes the manifest: it only reads the annotations, nothing is called. */
    public static ExampleTool forManifest() {
        return new ExampleTool(null, null, null, null);
    }

    public ExampleTool(ExampleUseCase useCase, ResourcePolicy policy, CursorCodec cursors, ExampleWriteGate gate) {
        this.useCase = useCase;
        this.policy = policy;
        this.cursors = cursors;
        this.gate = gate;
    }

    /**
     * {@code @ResourceRef} makes the starter resolve the id (through the resolver, with the caller's credential) and
     * evaluate the policy before this method runs. A record that is missing, not visible, or in a restricted project
     * never gets here: the client sees the same {@code not_found_or_denied} for all three.
     */
    @Tool(name = "get_record", description = "Returns one record by its id: id, project, title and status.")
    @ToolRisk(RiskLevel.READ_ONLY)
    @ToolTitle("Get record")
    public ExampleRecordView getRecord(
            @ToolParam(description = "Record id as the system issues it")
            @ArgumentLimits(maxLength = ExampleResourceModel.RECORD_ID_MAX_LENGTH,
                    resourceType = ExampleResourceModel.RECORD_TYPE)
            @ResourceRef(type = ExampleResourceModel.RECORD_TYPE) String recordId) {
        // The record the resolver fetched for the policy: no second call to the backend, and the answer judged is
        // the answer returned.
        ExampleRecord record = ResourceContext.current().orElseThrow()
                .fetched(ExampleResourceModel.RECORD_TYPE, recordId, ExampleRecord.class).orElseThrow();
        return ExampleRecordView.of(record);
    }

    /**
     * The model names the project and the text, never a query: {@link ExampleSearchQuery} builds the backend query
     * from them and from the {@link QueryScope} of the policy, narrowed to the requested project with
     * {@link QueryScope#narrowedTo} where the system has a query builder. The project is not a
     * {@code @ResourceRef} on purpose: that would need a resolver for the container type, and a project the caller
     * is not granted must give an empty search, not an error that tells which projects exist. The hits are filtered afterwards by their {@code @ResourceRef} component,
     * which also removes what the backend returns beyond the project (records linked from it) and what the policy refuses
     * (a restricted project yields no hits).
     */
    @Tool(name = "search_records", description = "Searches the titles of the records of one project for a text.")
    @ToolRisk(RiskLevel.READ_ONLY)
    @ToolTitle("Search records")
    public ExampleSearchView searchRecords(
            @ToolParam(description = "Project to search in, as the system names it")
            @ArgumentLimits(maxLength = ExampleResourceModel.PARENT_ID_MAX_LENGTH,
                    resourceType = ExampleResourceModel.PARENT_TYPE) String project,
            @ToolParam(description = "Text to look for in the title, at most 200 characters")
            @ArgumentLimits(minLength = 1, maxLength = SEARCH_TEXT_MAX_LENGTH) String text) {
        if (text == null || text.isBlank() || text.length() > SEARCH_TEXT_MAX_LENGTH) {
            throw new InvalidToolArgumentException("text must be 1-" + SEARCH_TEXT_MAX_LENGTH + " characters");
        }
        QueryScope scope = policy.queryScope(ExampleResourceModel.PARENT_TYPE);
        return ExampleSearchQuery.of(scope, project, text)
                .map(query -> ExampleSearchView.of(useCase.searchRecords(query)))
                .orElseGet(() -> new ExampleSearchView(List.of()));
    }

    /**
     * A paged list: the page, the cursor of the next one, no total. The position travels in a cursor of
     * {@link CursorCodec} (encrypted, bound to the caller, the tool and the arguments, so a cursor of another record or
     * user is refused). {@link PageFiller} reads further backend pages while the use case dropped rows (a comment filed
     * under another record), so the size of a page does not show how much was dropped.
     */
    @Tool(name = "list_comments", description = "Lists the comments of one record, oldest first, 10 per page: id, "
            + "author and the text as written by the users of the system, marked as untrusted data. Pass nextCursor "
            + "back, unchanged, to get the next page.")
    @ToolRisk(RiskLevel.READ_ONLY)
    @ToolTitle("List comments")
    public PagedResult<ExampleCommentItem> listComments(
            @ToolParam(description = "Record id as the system issues it")
            @ArgumentLimits(maxLength = ExampleResourceModel.RECORD_ID_MAX_LENGTH,
                    resourceType = ExampleResourceModel.RECORD_TYPE)
            @ResourceRef(type = ExampleResourceModel.RECORD_TYPE) String recordId,
            @ToolParam(description = "nextCursor of the previous page, unchanged", required = false)
            @ArgumentLimits(maxLength = CursorCodec.MAX_CURSOR_LENGTH, pattern = CursorCodec.ARGUMENT_PATTERN)
            String cursor) {
        // The id the backend reports now: the one judged is the one whose comments are listed.
        String id = judged(recordId).id();
        String fingerprint = CursorCodec.fingerprint("list_comments", id);
        PageFiller.Filled<ExampleCommentItem> page = PageFiller.fill(cursor == null ? null : position(cursor, fingerprint),
                ExampleUseCase.COMMENT_PAGE_SIZE, FILL_READS,
                from -> {
                    CommentPage found = useCase.listComments(id, from == null ? 0 : Integer.parseInt(from));
                    return new PageFiller.BackendPage<>(found.comments(),
                            found.nextStart() == null ? null : found.nextStart().toString());
                },
                comments -> comments.stream().map(ExampleCommentItem::of).toList());
        return PagedResult.of(page.items(),
                page.next() == null ? null : cursors.issue("list_comments", fingerprint, page.next()));
    }

    /**
     * A child object named by its own id: the record is judged by the policy ({@code @ResourceRef}), the comment id is
     * not a resource of its own, so the use case checks that the backend files the comment under that record. Without
     * the check a caller allowed to read record A could read any comment by guessing ids and naming A.
     */
    @Tool(name = "get_comment", description = "Returns one comment of a record: id, author and the text as written by "
            + "the users of the system, marked as untrusted data.")
    @ToolRisk(RiskLevel.READ_ONLY)
    @ToolTitle("Get comment")
    public ExampleCommentItem getComment(
            @ToolParam(description = "Record id as the system issues it")
            @ArgumentLimits(maxLength = ExampleResourceModel.RECORD_ID_MAX_LENGTH,
                    resourceType = ExampleResourceModel.RECORD_TYPE)
            @ResourceRef(type = ExampleResourceModel.RECORD_TYPE) String recordId,
            @ToolParam(description = "Comment id from list_comments, digits only")
            @ArgumentLimits(minLength = 1, maxLength = COMMENT_ID_MAX_LENGTH, pattern = COMMENT_ID_PATTERN)
            String commentId) {
        return ExampleCommentItem.of(useCase.getComment(judged(recordId).id(), commentId));
    }

    /**
     * A change. {@code UPDATE} is delivered at least once (the client may repeat a call after a 502 from the balancer),
     * so the tool states the target (the title is set to a value) instead of applying an increment: the second call
     * leaves the same state. The gate comes first: against an untested backend version nothing is sent.
     */
    @Tool(name = "update_record", description = "Sets the title of one record to the given text. Calling it again with "
            + "the same text changes nothing more.")
    @ToolRisk(RiskLevel.UPDATE)
    @ToolTitle("Update record title")
    public ExampleUpdateView updateRecord(
            @ToolParam(description = "Record id as the system issues it")
            @ArgumentLimits(maxLength = ExampleResourceModel.RECORD_ID_MAX_LENGTH,
                    resourceType = ExampleResourceModel.RECORD_TYPE)
            @ResourceRef(type = ExampleResourceModel.RECORD_TYPE) String recordId,
            @ToolParam(description = "New title, one line, 1-255 characters")
            @ArgumentLimits(minLength = 1, maxLength = TITLE_MAX_LENGTH) String title) {
        gate.requireWritable();
        if (title == null || title.isBlank() || title.length() > TITLE_MAX_LENGTH
                || title.chars().anyMatch(Character::isISOControl)) {
            throw new InvalidToolArgumentException("title must be 1-" + TITLE_MAX_LENGTH
                    + " characters on one line without control characters");
        }
        // Written to the id the backend reports now, not to the requested one of a moved record.
        return ExampleUpdateView.of(useCase.updateTitle(judged(recordId).id(), title.strip()));
    }

    /** The record the resolver fetched for the policy: the one judged is the one used. */
    private static ExampleRecord judged(String recordId) {
        return ResourceContext.current().orElseThrow()
                .fetched(ExampleResourceModel.RECORD_TYPE, recordId, ExampleRecord.class).orElseThrow();
    }

    /** The offset a cursor carries; a cursor of another user, tool or record is refused by the codec. */
    private String position(String cursor, String fingerprint) {
        String state = cursors.open(cursor, "list_comments", fingerprint);
        try {
            Integer.parseInt(state);
        } catch (NumberFormatException notOurs) {
            throw new InvalidToolArgumentException(CursorCodec.REFUSED_MESSAGE);
        }
        return state;
    }
}
