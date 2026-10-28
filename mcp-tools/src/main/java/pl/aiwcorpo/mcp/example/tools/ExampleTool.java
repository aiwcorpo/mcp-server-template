package pl.aiwcorpo.mcp.example.tools;

import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import pl.aiwcorpo.mcp.example.core.domain.ExampleRecord;
import pl.aiwcorpo.mcp.example.core.domain.ExampleRecordHit;
import pl.aiwcorpo.mcp.example.core.domain.ExampleResourceModel;
import pl.aiwcorpo.mcp.example.core.port.in.ExampleUseCase;
import pl.aiwcorpo.mcp.platform.error.InvalidToolArgumentException;
import pl.aiwcorpo.mcp.platform.guard.RiskLevel;
import pl.aiwcorpo.mcp.platform.guard.ToolRisk;
import pl.aiwcorpo.mcp.platform.guard.ToolTitle;
import pl.aiwcorpo.mcp.platform.input.ArgumentLimits;
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

    static final int SEARCH_TEXT_MAX_LENGTH = 200;

    private final ExampleUseCase useCase;
    private final ResourcePolicy policy;

    public ExampleTool(ExampleUseCase useCase, ResourcePolicy policy) {
        this.useCase = useCase;
        this.policy = policy;
    }

    /**
     * {@code @ResourceRef} makes the starter resolve the id (through the resolver, with the caller's credential) and
     * evaluate the policy before this method runs. A record that is missing, not visible, or in a restricted project
     * never gets here: the client sees the same {@code not_found_or_denied} for all three.
     */
    @Tool(name = "get_record", description = "Returns one record by its id: id, project, title and status.")
    @ToolRisk(RiskLevel.READ_ONLY)
    @ToolTitle("Pobierz rekord")
    public ExampleRecordView getRecord(
            @ToolParam(description = "Record id as the system issues it")
            @ArgumentLimits(maxLength = ExampleResourceModel.RECORD_ID_MAX_LENGTH,
                    contractPattern = ExampleResourceModel.RECORD_ID_CONTRACT)
            @ResourceRef(type = ExampleResourceModel.RECORD_TYPE) String recordId) {
        // The record the resolver fetched for the policy: no second call to the backend, and the answer judged is
        // the answer returned.
        ExampleRecord record = ResourceContext.current().orElseThrow()
                .fetched(ExampleResourceModel.RECORD_TYPE, recordId, ExampleRecord.class).orElseThrow();
        return ExampleRecordView.of(record);
    }

    /**
     * The model names the project and the text, never a query: the server builds the backend query. The project is
     * not a {@code @ResourceRef} on purpose, because a link that narrows the virtual server to other projects must
     * give an empty search, not an error that tells which projects exist. The link narrowing comes from
     * {@link QueryScope}; the hits are filtered afterwards by their {@code @ResourceRef} component, which also removes
     * what the backend returns beyond the project (linked records) and what the policy refuses (restricted).
     */
    @Tool(name = "search_records", description = "Searches the titles of the records of one project for a text.")
    @ToolRisk(RiskLevel.READ_ONLY)
    @ToolTitle("Szukaj rekordów")
    public ExampleSearchView searchRecords(
            @ToolParam(description = "Project to search in, as the system names it")
            @ArgumentLimits(maxLength = ExampleResourceModel.PARENT_ID_MAX_LENGTH,
                    contractPattern = ExampleResourceModel.PARENT_ID_CONTRACT) String project,
            @ToolParam(description = "Text to look for in the title, at most 200 characters")
            @ArgumentLimits(minLength = 1, maxLength = SEARCH_TEXT_MAX_LENGTH) String text) {
        if (text == null || text.isBlank() || text.length() > SEARCH_TEXT_MAX_LENGTH) {
            throw new InvalidToolArgumentException("text must be 1-" + SEARCH_TEXT_MAX_LENGTH + " characters");
        }
        QueryScope scope = policy.queryScope(ExampleResourceModel.PARENT_TYPE);
        boolean insideLink = scope.linkIds().map(link -> ExampleResourceModel.withinLink(link, project)).orElse(true);
        if (!insideLink) {
            return new ExampleSearchView(List.of());
        }
        List<ExampleRecordHit> hits = useCase.searchRecords(project, text);
        return ExampleSearchView.of(hits);
    }
}
