package pl.aiwcorpo.mcp.example.tools;

import java.util.Optional;
import org.springframework.stereotype.Component;
import pl.aiwcorpo.mcp.example.core.domain.BackendDegradedException;
import pl.aiwcorpo.mcp.example.core.domain.NotFoundOrDeniedException;
import pl.aiwcorpo.mcp.platform.error.McpErrorMapper.SafeError;
import pl.aiwcorpo.mcp.platform.error.ToolErrorCode;
import pl.aiwcorpo.mcp.platform.error.ToolErrorMapping;

/**
 * Domain exceptions of this server as C10 errors with fixed texts. The text of {@code not_found_or_denied} is the
 * constant of the starter, so a refusal after the policy cannot be told from one before it; everything else falls
 * through to the defaults of the starter. No text here carries a value from the arguments or from the backend.
 */
@Component
public class ExampleToolErrors implements ToolErrorMapping {

    @Override
    public Optional<SafeError> map(Throwable ex) {
        if (ex instanceof NotFoundOrDeniedException) {
            return Optional.of(new SafeError(ToolErrorCode.NOT_FOUND_OR_DENIED.code(),
                    ToolErrorCode.NOT_FOUND_OR_DENIED_MESSAGE));
        }
        if (ex instanceof BackendDegradedException) {
            return Optional.of(new SafeError(ToolErrorCode.UPSTREAM_UNAVAILABLE.code(),
                    "This backend version is not supported for changes; reading still works. Nothing was changed."));
        }
        return Optional.empty();
    }
}
