package pl.aiwcorpo.mcp.example.tools;

import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.aiwcorpo.mcp.example.core.domain.BackendDegradedException;
import pl.aiwcorpo.mcp.example.core.domain.ObservedVersions;
import pl.aiwcorpo.mcp.platform.capability.BackendCapabilities;
import pl.aiwcorpo.mcp.platform.capability.Capabilities;

/**
 * Keeps the tools that change the backend from running against a version nobody tested (fail closed). Every tool of
 * risk {@code UPDATE} or {@code DESTRUCTIVE} calls {@link #requireWritable()} before it does anything:
 * <ul>
 *   <li>{@code SUPPORTED} with the feature {@code record_write}: writes go through;</li>
 *   <li>{@code DEGRADED} (older or newer than tested): reads only, whatever the setting;</li>
 *   <li>before the first probe answered, and {@code UNAVAILABLE} (no answer, no readable version): reads only, unless
 *       {@code example.writes-on-unknown-version} is true ({@code MCP_EXAMPLE_WRITES_ON_UNKNOWN_VERSION}), which opens
 *       writes in this state only;</li>
 *   <li>{@code UNAVAILABLE} after the probe once classified the backend as untested: reads only, whatever the
 *       setting ({@link ObservedVersions}).</li>
 * </ul>
 */
@Component
public class ExampleWriteGate {

    static final String PROBE = "example";
    static final String WRITE_FEATURE = "record_write";

    private final Supplier<Capabilities> current;
    private final ObservedVersions observed;
    private final boolean writesOnUnknownVersion;

    @Autowired
    public ExampleWriteGate(BackendCapabilities capabilities, ObservedVersions observed,
            @Value("${example.writes-on-unknown-version:false}") boolean writesOnUnknownVersion) {
        this(() -> capabilities.of(PROBE), observed, writesOnUnknownVersion);
    }

    /** For tests: {@code BackendCapabilities} of the starter cannot be filled from outside its package. */
    ExampleWriteGate(Supplier<Capabilities> current, ObservedVersions observed, boolean writesOnUnknownVersion) {
        this.current = current;
        this.observed = observed;
        this.writesOnUnknownVersion = writesOnUnknownVersion;
    }

    public void requireWritable() {
        Capabilities capabilities = current.get();
        boolean allowed = switch (capabilities.state()) {
            case SUPPORTED -> capabilities.supports(WRITE_FEATURE) && !observed.untestedSeen();
            case DEGRADED -> false;
            case UNAVAILABLE -> writesOnUnknownVersion && !observed.untestedSeen();
        };
        if (!allowed) {
            throw new BackendDegradedException();
        }
    }
}
