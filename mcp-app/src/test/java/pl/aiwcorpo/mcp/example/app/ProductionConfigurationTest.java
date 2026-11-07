package pl.aiwcorpo.mcp.example.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;
import pl.aiwcorpo.mcp.example.integration.ExampleBackendProperties;
import pl.aiwcorpo.mcp.example.integration.ExampleBackendClient;

/** What the production configuration must keep, checked without starting a server. */
class ProductionConfigurationTest {

    @Test
    void outsideDevTheServerDemandsAClientCertificate() throws IOException {
        List<PropertySource<?>> documents = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));

        PropertySource<?> production = documents.stream()
                .filter(document -> "!dev".equals(document.getProperty("spring.config.activate.on-profile")))
                .findFirst().orElseThrow();
        assertThat(String.valueOf(production.getProperty("server.ssl.client-auth"))).isEqualTo("need");
        assertThat(String.valueOf(production.getProperty("server.ssl.bundle"))).isEqualTo("mcp-server");
        assertThat(documents).allSatisfy(document -> {
            Object clientAuth = document.getProperty("server.ssl.client-auth");
            assertThat(clientAuth == null || "need".equals(String.valueOf(clientAuth))).isTrue();
        });
    }

    @Test
    void theBaseConfigurationNeedsThePolicyDataAndTheManifestButNeverAllowsTestKeys() throws IOException {
        List<PropertySource<?>> documents = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        PropertySource<?> base = documents.getFirst();

        assertThat(base.containsProperty("aiwcorpo.mcp.platform.policy-data.url")).isTrue();
        assertThat(base.containsProperty("aiwcorpo.mcp.platform.manifest.version")).isTrue();
        assertThat(base.containsProperty("aiwcorpo.mcp.platform.manifest.internal-url")).isTrue();
        assertThat(base.containsProperty("aiwcorpo.mcp.platform.manifest.accepted-credential-types")).isTrue();
        assertThat(documents).allSatisfy(document ->
                assertThat(document.containsProperty("aiwcorpo.mcp.platform.internal-jwt.allow-test-keys")).isFalse());
    }

    @Test
    void theDefaultCredentialTypeIsTheFirstAcceptedTypeInEveryConfiguration() throws IOException {
        PropertySource<?> base = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml")).getFirst();
        PropertySource<?> dev = new YamlPropertySourceLoader()
                .load("application-dev.yml", new ClassPathResource("application-dev.yml")).getFirst();

        String type = SampleScope.CREDENTIAL_TYPE.wireName();
        String key = "aiwcorpo.mcp.platform.manifest.accepted-credential-types";
        assertThat(base.containsProperty("aiwcorpo.mcp.platform.credential-type")).as("removed in beta-7").isFalse();
        assertThat(String.valueOf(base.getProperty(key))).as("the default type is the first").contains(":" + type);
        assertThat(String.valueOf(dev.getProperty(key))).startsWith(type);
    }

    @Test
    void testKeysAndTheLocalPolicyDataAreConfiguredInDevOnly() throws IOException {
        Map<String, Object> dev = (Map<String, Object>) new YamlPropertySourceLoader()
                .load("application-dev.yml", new ClassPathResource("application-dev.yml")).getFirst().getSource();

        assertThat(String.valueOf(dev.get("aiwcorpo.mcp.platform.internal-jwt.allow-test-keys"))).isEqualTo("true");
        assertThat(String.valueOf(dev.get("aiwcorpo.mcp.platform.policy-data.url"))).contains("http://127.0.0.1:");
    }

    @Test
    void outsideDevTheBackendMustBeHttps() {
        assertThatThrownBy(() -> adapter("http://backend.internal:8080", environment()))
                .hasMessageContaining("https");
        assertThatCode(() -> adapter("https://backend.internal", environment())).doesNotThrowAnyException();

        assertThatCode(() -> new ExampleBackendClient(
                new ExampleBackendProperties("http://backend.internal:8080", null, null, null, null, true),
                new DefaultListableBeanFactory().getBeanProvider(SslBundles.class), environment()))
                .as("explicit opt-in for the dev environment's mocks").doesNotThrowAnyException();

        MockEnvironment dev = environment();
        dev.setActiveProfiles("dev");
        assertThatCode(() -> adapter("http://localhost:18081", dev)).doesNotThrowAnyException();
    }

    @Test
    void devIsReachableOnLoopbackOnly() throws IOException {
        Map<String, Object> dev = (Map<String, Object>) new YamlPropertySourceLoader()
                .load("application-dev.yml", new ClassPathResource("application-dev.yml")).getFirst().getSource();
        assertThat(String.valueOf(dev.get("server.address"))).isEqualTo("127.0.0.1");
    }

    private static MockEnvironment environment() {
        return new MockEnvironment().withProperty("aiwcorpo.mcp.platform.server-id", "mcp-example");
    }

    private static ExampleBackendClient adapter(String baseUrl, MockEnvironment environment) {
        return new ExampleBackendClient(new ExampleBackendProperties(baseUrl, null, null, null, null, false),
                new DefaultListableBeanFactory().getBeanProvider(SslBundles.class), environment);
    }
}
