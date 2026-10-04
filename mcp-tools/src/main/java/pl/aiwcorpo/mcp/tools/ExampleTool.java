package pl.aiwcorpo.mcp.tools;

import pl.aiwcorpo.mcp.core.domain.ExampleRecord;
import pl.aiwcorpo.mcp.core.port.in.ExampleUseCase;
import java.util.regex.Pattern;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import pl.aiwcorpo.mcp.platform.guard.RiskLevel;
import pl.aiwcorpo.mcp.platform.guard.ToolRisk;

/**
 * Example MCP tools (inbound adapter). One class per cohesive capability group; each public
 * {@code @Tool} method becomes an AI-callable capability.
 *
 * <p><b>Every tool must carry {@link ToolRisk}</b> — the server does not start otherwise. The flag
 * decides who may call the tool and whether a human has to confirm the call:
 * <ul>
 *   <li>{@code READ_ONLY} — open to every caller, including anonymous ones;</li>
 *   <li>{@code UPDATE} — callers cleared for {@code update} or higher;</li>
 *   <li>{@code DESTRUCTIVE} — callers cleared for {@code destructive}, and only after the user
 *       confirms with a one-time code.</li>
 * </ul>
 * You write the flag and the tool; the platform starter enforces the rest. Do not add your own
 * "are you sure?" parameter — the platform's confirmation cannot be answered by the model.
 *
 * <p>Treat every argument as untrusted LLM output: validate before use, apply least-privilege,
 * never build shell/SQL/paths from raw args, redact secrets in results, and rely on the platform's
 * AuditLogger for the invocation record.
 */
@Service
public class ExampleTool {

    /** Conservative allow-list for ids coming from the model. */
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9-]{1,64}");

    /** The one dataset this server manages; the purge tool makes the caller name it. */
    private static final String DATASET = "records";

    private final ExampleUseCase useCase;

    public ExampleTool(ExampleUseCase useCase) {
        this.useCase = useCase;
    }

    @Tool(name = "example_lookup", description = "Return a record by its id. Read-only. Try 'demo-1'.")
    @ToolRisk(RiskLevel.READ_ONLY)
    public ExampleRecord lookup(@ToolParam(description = "Record id, alphanumeric/dash, max 64 chars") String id) {
        return useCase.getById(validId(id));
    }

    @Tool(name = "example_rename", description = "Change the name of a record. Reversible: rename it back.")
    @ToolRisk(RiskLevel.UPDATE)
    public ExampleRecord rename(
            @ToolParam(description = "Record id, alphanumeric/dash, max 64 chars") String id,
            @ToolParam(description = "New name, 1-120 characters") String name) {
        return useCase.rename(validId(id), name);
    }

    @Tool(name = "example_purge", description = "Permanently remove ALL records of a dataset. Cannot be undone.")
    @ToolRisk(RiskLevel.DESTRUCTIVE)
    public String purge(@ToolParam(description = "Dataset to purge. The only dataset is 'records'.") String dataset) {
        if (!DATASET.equals(dataset)) {
            throw new IllegalArgumentException("unknown dataset; the only dataset is '" + DATASET + "'");
        }
        return "Purged " + useCase.purgeAll() + " record(s) from '" + DATASET + "'.";
    }

    private static String validId(String id) {
        if (id == null || !ID.matcher(id).matches()) {
            throw new IllegalArgumentException("id must match [A-Za-z0-9-]{1,64}");
        }
        return id;
    }
}
