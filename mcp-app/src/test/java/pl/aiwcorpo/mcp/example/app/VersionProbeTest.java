package pl.aiwcorpo.mcp.example.app;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pl.aiwcorpo.mcp.platform.capability.Capabilities;

/** The probe reads the version from the stub without a credential and the server reports what it found. */
class VersionProbeTest extends ServerTestSupport {

    @Test
    void theTestedVersionOfTheStubIsSupportedWithWrites() throws InterruptedException {
        awaitProbe();

        Capabilities found = capabilities.of("example");
        assertThat(found.state()).isEqualTo(Capabilities.State.SUPPORTED);
        assertThat(found.supports("record_write")).isTrue();
    }
}
