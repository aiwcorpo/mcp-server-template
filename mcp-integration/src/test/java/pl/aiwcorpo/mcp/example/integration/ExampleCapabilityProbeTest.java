package pl.aiwcorpo.mcp.example.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pl.aiwcorpo.mcp.example.core.domain.ServerInfo;
import pl.aiwcorpo.mcp.platform.capability.Capabilities;

class ExampleCapabilityProbeTest {

    @Test
    void aTestedVersionIsSupportedWithWrites() {
        Capabilities found = ExampleCapabilityProbe.classify(new ServerInfo("2.4.1"));

        assertThat(found.state()).isEqualTo(Capabilities.State.SUPPORTED);
        assertThat(found.supports(ExampleCapabilityProbe.RECORD_WRITE)).isTrue();
    }

    @Test
    void anOlderOrNewerVersionOnlyReads() {
        for (String version : new String[] {"1.9.0", "3.0.0", "11.2"}) {
            Capabilities found = ExampleCapabilityProbe.classify(new ServerInfo(version));

            assertThat(found.state()).as(version).isEqualTo(Capabilities.State.DEGRADED);
            assertThat(found.supports(ExampleCapabilityProbe.RECORD_READ)).as(version).isTrue();
            assertThat(found.supports(ExampleCapabilityProbe.RECORD_WRITE)).as(version).isFalse();
        }
    }

    @Test
    void noReadableVersionIsUnavailable() {
        for (String version : new String[] {null, "", "latest", "<html>login</html>"}) {
            assertThat(ExampleCapabilityProbe.classify(new ServerInfo(version)).state()).as(String.valueOf(version))
                    .isEqualTo(Capabilities.State.UNAVAILABLE);
        }
    }
}
