package pl.aiwcorpo.mcp.example.tools;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;
import pl.aiwcorpo.mcp.example.core.domain.BackendDegradedException;
import pl.aiwcorpo.mcp.example.core.domain.ObservedVersions;
import pl.aiwcorpo.mcp.platform.capability.Capabilities;

class ExampleWriteGateTest {

    private static final Capabilities SUPPORTED = Capabilities.supported("2.4.1", Set.of("record_read", "record_write"));
    private static final Capabilities DEGRADED = Capabilities.degraded("3.0.0", Set.of("record_read"), "newer than tested");
    private static final Capabilities UNAVAILABLE = Capabilities.unavailable("no answer");

    private static ExampleWriteGate gate(Capabilities state, ObservedVersions observed, boolean onUnknown) {
        return new ExampleWriteGate(() -> state, observed, onUnknown);
    }

    @Test
    void aTestedVersionMayBeWritten() {
        assertThatCode(() -> gate(SUPPORTED, new ObservedVersions(), false).requireWritable()).doesNotThrowAnyException();
    }

    @Test
    void anUntestedVersionIsNeverWrittenWhateverTheSetting() {
        assertThatThrownBy(() -> gate(DEGRADED, new ObservedVersions(), true).requireWritable())
                .isInstanceOf(BackendDegradedException.class);
    }

    @Test
    void anUnknownVersionIsClosedUnlessTheOperatorOpensIt() {
        assertThatThrownBy(() -> gate(UNAVAILABLE, new ObservedVersions(), false).requireWritable())
                .isInstanceOf(BackendDegradedException.class);
        assertThatCode(() -> gate(UNAVAILABLE, new ObservedVersions(), true).requireWritable()).doesNotThrowAnyException();
    }

    @Test
    void aFailingProbeAfterAnUntestedFindingDoesNotReopenWrites() {
        ObservedVersions observed = new ObservedVersions();
        observed.untested();

        assertThatThrownBy(() -> gate(UNAVAILABLE, observed, true).requireWritable())
                .isInstanceOf(BackendDegradedException.class);
    }
}
