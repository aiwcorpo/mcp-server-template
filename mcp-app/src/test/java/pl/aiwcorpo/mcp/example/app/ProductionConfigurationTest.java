package pl.aiwcorpo.mcp.example.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.catalina.util.ServerInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;
import pl.aiwcorpo.mcp.example.integration.ExampleBackendProperties;
import pl.aiwcorpo.mcp.example.integration.ExampleBackendClient;
import tools.jackson.core.Version;
import tools.jackson.core.json.PackageVersion;

/** What the production configuration must keep, checked without starting a server. */
class ProductionConfigurationTest {

    @Test
    void theServerAsksForNoClientCertificateAndLeavesTlsToTheStarter() throws IOException {
        List<PropertySource<?>> documents = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));

        // The gateway proves itself with the internal JWT, not with a certificate; the certificate of the server comes from
        // MCP_TLS_*, or the starter generates one (CC-249), so the file sets neither the client auth nor a bundle.
        assertThat(documents).allSatisfy(document -> {
            assertThat(document.containsProperty("server.ssl.client-auth")).isFalse();
            assertThat(document.containsProperty("server.ssl.bundle")).isFalse();
            assertThat(document.containsProperty("spring.ssl.bundle.jks.mcp-server.keystore.location")).isFalse();
        });
    }

    @Test
    void theBaseConfigurationNeedsTheJwksAndTheManifestButNeverAllowsTestKeys() throws IOException {
        List<PropertySource<?>> documents = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        PropertySource<?> base = documents.getFirst();

        assertThat(base.containsProperty("aiwcorpo.mcp.platform.internal-jwt.jwks-uri")).isTrue();
        assertThat(base.containsProperty("aiwcorpo.mcp.platform.manifest.version")).isTrue();
        assertThat(base.containsProperty("aiwcorpo.mcp.platform.manifest.accepted-credential-types")).isTrue();
        assertThat(documents).allSatisfy(document -> {
            assertThat(document.containsProperty("aiwcorpo.mcp.platform.internal-jwt.allow-test-keys")).isFalse();
            assertThat(document.containsProperty("aiwcorpo.mcp.platform.policy-data.url")).as("pushed by the gateway").isFalse();
            assertThat(document.containsProperty("aiwcorpo.mcp.platform.mtls.required")).isFalse();
            assertThat(document.containsProperty("aiwcorpo.mcp.platform.internal-jwt.jwks-ssl-bundle")).isFalse();
        });
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
    void testKeysAndTheLocalJwksAreConfiguredInDevOnly() throws IOException {
        Map<String, Object> dev = (Map<String, Object>) new YamlPropertySourceLoader()
                .load("application-dev.yml", new ClassPathResource("application-dev.yml")).getFirst().getSource();

        assertThat(String.valueOf(dev.get("aiwcorpo.mcp.platform.internal-jwt.allow-test-keys"))).isEqualTo("true");
        assertThat(String.valueOf(dev.get("aiwcorpo.mcp.platform.internal-jwt.jwks-uri"))).contains("http://127.0.0.1:")
                .contains("/.well-known/mcp-jwks.json");
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

    @Test
    void theEmbeddedTomcatAndJacksonIncludeTheirSecurityFixes() {
        // The Boot parent stays on the starter's version, so the pom overrides these two; a parent bump must not lower them.
        int[] tomcat = Arrays.stream(ServerInfo.getServerNumber().split("\\.")).mapToInt(Integer::parseInt).toArray();
        assertThat(Arrays.compare(tomcat, new int[] {11, 0, 27})).as("Tomcat %s", ServerInfo.getServerNumber()).isNotNegative();
        assertThat(PackageVersion.VERSION).isGreaterThanOrEqualTo(new Version(3, 1, 7, null, null, null));
    }

    private static MockEnvironment environment() {
        return new MockEnvironment().withProperty("aiwcorpo.mcp.platform.server-id", "mcp-example");
    }

    private static ExampleBackendClient adapter(String baseUrl, MockEnvironment environment) {
        return new ExampleBackendClient(new ExampleBackendProperties(baseUrl, null, null, null, null, false),
                new DefaultListableBeanFactory().getBeanProvider(SslBundles.class), environment);
    }
}
