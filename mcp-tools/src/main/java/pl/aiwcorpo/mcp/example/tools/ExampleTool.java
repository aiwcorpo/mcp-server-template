package pl.aiwcorpo.mcp.example.tools;

import java.util.regex.Pattern;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import pl.aiwcorpo.mcp.example.core.port.in.ExampleUseCase;
import pl.aiwcorpo.mcp.platform.error.InvalidToolArgumentException;
import pl.aiwcorpo.mcp.platform.guard.RiskLevel;
import pl.aiwcorpo.mcp.platform.guard.ToolRisk;
import pl.aiwcorpo.mcp.platform.input.ArgumentLimits;

/**
 * The MCP tools of this server. Each public {@code @Tool} method is a capability an AI client can call
 * through the gateway, so every argument is untrusted input from a model.
 *
 * <p>The gateway decides who may call a tool and asks for confirmations; the starter verifies that the
 * call came from the gateway for exactly this tool. What stays here: a risk flag on every tool, strict
 * argument checks (published in the schema and enforced in code), and the resource always named by an
 * explicit argument.
 */
@Service
public class ExampleTool {

    /**
     * Record ids as the backend issues them. The first character excludes "." and "..", which URI template
     * encoding leaves as path segments. One constant for the schema and the check.
     */
    static final String RECORD_ID_PATTERN = "^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$";
    static final int RECORD_ID_MAX_LENGTH = 64;

    private static final Pattern RECORD_ID = Pattern.compile(RECORD_ID_PATTERN);

    private final ExampleUseCase useCase;

    public ExampleTool(ExampleUseCase useCase) {
        this.useCase = useCase;
    }

    @Tool(name = "get_record", description = "Returns one record by its id: id, project, title and status.")
    @ToolRisk(RiskLevel.READ_ONLY)
    public ExampleRecordView getRecord(
            @ToolParam(description = "Record id: letters, digits, '-' or '_', at most 64 characters")
            @ArgumentLimits(maxLength = RECORD_ID_MAX_LENGTH, pattern = RECORD_ID_PATTERN) String recordId) {
        return ExampleRecordView.of(useCase.getRecord(validRecordId(recordId)));
    }

    private static String validRecordId(String recordId) {
        if (recordId == null || !RECORD_ID.matcher(recordId).matches()) {
            // Fixed text: the value came from the model and must not be echoed back (C10).
            throw new InvalidToolArgumentException("recordId must be 1-64 characters: letters, digits, '-' or '_'");
        }
        return recordId;
    }
}
