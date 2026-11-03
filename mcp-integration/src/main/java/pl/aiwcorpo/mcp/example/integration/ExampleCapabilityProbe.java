package pl.aiwcorpo.mcp.example.integration;

import java.util.Set;
import org.springframework.stereotype.Component;
import pl.aiwcorpo.mcp.example.core.domain.ObservedVersions;
import pl.aiwcorpo.mcp.example.core.domain.ServerInfo;
import pl.aiwcorpo.mcp.example.core.port.out.ExampleResourcePort;
import pl.aiwcorpo.mcp.platform.capability.Capabilities;
import pl.aiwcorpo.mcp.platform.capability.CapabilityProbe;
import pl.aiwcorpo.mcp.platform.capability.ProductVersion;

/**
 * Reads the version of the backend and decides what this server may do with it. A new server sets {@link #MIN_MAJOR},
 * {@link #MIN_MINOR} and {@link #MAX_MAJOR} to the range it was actually tested against; nothing outside it is guessed.
 *
 * <ul>
 *   <li>inside the range: {@code SUPPORTED}, reads and writes;</li>
 *   <li>older or newer: {@code DEGRADED}, reads only; every tool that changes something refuses
 *       ({@code ExampleWriteGate});</li>
 *   <li>no answer, an answer without a readable version: {@code UNAVAILABLE}. A failing probe after a finding of
 *       "untested" keeps reporting that finding ({@link ObservedVersions}): one timeout must not reopen writes.</li>
 * </ul>
 *
 * <p>The version is untrusted text from the backend: it is only parsed to numbers here, and the starter cleans what it
 * logs. The probe has no credential: a backend whose version endpoint needs a login needs another source of the version
 * (a public endpoint, a decision about a service credential), not a secret kept in this server.
 */
@Component
public class ExampleCapabilityProbe implements CapabilityProbe {

    public static final String NAME = "example";

    /** Reads that work on every version that answers. */
    public static final String RECORD_READ = "record_read";
    /** Tools that change something; granted only for a tested version. */
    public static final String RECORD_WRITE = "record_write";

    static final int MIN_MAJOR = 2;
    static final int MIN_MINOR = 0;
    static final int MAX_MAJOR = 2;

    private final ExampleResourcePort backend;
    private final ObservedVersions observed;
    private volatile Capabilities lastUntested;

    public ExampleCapabilityProbe(ExampleResourcePort backend, ObservedVersions observed) {
        this.backend = backend;
        this.observed = observed;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Capabilities probe() {
        Capabilities found;
        try {
            found = classify(backend.serverInfo());
        } catch (RuntimeException failed) {
            Capabilities untested = lastUntested;
            if (untested != null) {
                return untested;
            }
            throw failed;
        }
        switch (found.state()) {
            case SUPPORTED -> {
                lastUntested = null;
                observed.tested();
            }
            case DEGRADED -> {
                lastUntested = found;
                observed.untested();
            }
            case UNAVAILABLE -> {
                if (lastUntested != null) {
                    return lastUntested;
                }
            }
        }
        return found;
    }

    static Capabilities classify(ServerInfo info) {
        ProductVersion version = ProductVersion.parse(info.version()).orElse(null);
        if (version == null) {
            return Capabilities.unavailable("server info has no version");
        }
        if (version.before(MIN_MAJOR, MIN_MINOR)) {
            return Capabilities.degraded(version.toString(), Set.of(RECORD_READ), "older than tested");
        }
        if (version.major() > MAX_MAJOR) {
            return Capabilities.degraded(version.toString(), Set.of(RECORD_READ), "newer than tested");
        }
        return Capabilities.supported(version.toString(), Set.of(RECORD_READ, RECORD_WRITE));
    }
}
