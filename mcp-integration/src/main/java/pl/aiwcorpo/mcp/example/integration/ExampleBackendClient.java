package pl.aiwcorpo.mcp.example.integration;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import pl.aiwcorpo.mcp.platform.security.BackendCredential;
import pl.aiwcorpo.mcp.platform.security.CredentialContext;
import pl.aiwcorpo.mcp.platform.security.CredentialRequiredException;

/**
 * Calls the backend API as the caller: with the credential the gateway resolved for this request (C3), whatever its
 * type (the user's own, a token exchanged for the user, or the account of the server where the system is configured
 * that way), never with a secret of the server itself.
 *
 * <p>Error mapping matters for security: 404 and 403 pass through as they are (the adapter turns them into "not
 * visible", one client-facing code, {@code not_found_or_denied}), and 401 means the credential no longer works, so the
 * user is sent to the portal to reconnect ({@code credential_required}). A failed call becomes an exception with the
 * status alone, an unusable answer (redirect, unparsable or oversized body) a {@link BackendResponseException}, so no
 * response body reaches a message or a log line.
 */
@Component
@EnableConfigurationProperties(ExampleBackendProperties.class)
public class ExampleBackendClient {

    private static final String DEV_PROFILE = "dev";
    private static final String SERVER_ID_PROPERTY = "aiwcorpo.mcp.platform.server-id";
    private static final Logger LOG = LoggerFactory.getLogger(ExampleBackendClient.class);

    private final RestClient client;
    private final String system;

    public ExampleBackendClient(ExampleBackendProperties properties, ObjectProvider<SslBundles> sslBundles,
            Environment environment) {
        // The credential of the request is bound to the system of the server id (mcp-<system>), so the client asks
        // for exactly that one; a constant here would drift from the configuration.
        this.system = environment.getRequiredProperty(SERVER_ID_PROPERTY).substring("mcp-".length());
        this.client = RestClient.builder()
                .baseUrl(requireBaseUrl(properties.baseUrl(),
                        environment.matchesProfiles(DEV_PROFILE) || properties.allowPlainHttp()))
                .requestFactory(requestFactory(properties, sslBundles))
                .requestInterceptor(new BoundedBodyInterceptor(properties.maxResponseSize().toBytes()))
                .defaultStatusHandler(status -> status.isError() || status.is3xxRedirection(), (request, response) -> {
                    // Status only: the body and headers of a failed call may echo the credential or carry backend
                    // detail, and the exception message ends up in the starter's tool_failed log line.
                    HttpStatusCode status = response.getStatusCode();
                    if (status.is3xxRedirection()) {
                        // Redirects are not followed (the credential must not go elsewhere) and an empty body
                        // must not read as "no record": an SSO login page would turn into not_found_or_denied.
                        throw new BackendResponseException("redirect " + status.value());
                    }
                    throw status.is4xxClientError()
                            ? HttpClientErrorException.create(status, "", HttpHeaders.EMPTY, new byte[0], null)
                            : HttpServerErrorException.create(status, "", HttpHeaders.EMPTY, new byte[0], null);
                })
                .build();
    }

    /** One GET with the credential of the request; {@code uri} is a template, so the variables are encoded as values. */
    <T> T get(Class<T> type, String uri, Object... variables) {
        BackendCredential credential = CredentialContext.require(system);
        return call(() -> client.get()
                .uri(uri, variables)
                .headers(credential::applyTo)
                .retrieve()
                .body(type));
    }

    /**
     * Runs one backend call and maps its failures. 404 and 403 pass through as they are: for a single record they
     * mean "not visible" (the caller turns them into an empty result), for a search they are plain failures.
     */
    private <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (HttpClientErrorException.Unauthorized expired) {
            throw new CredentialRequiredException(system);
        } catch (RestClientResponseException | ResourceAccessException statusOrIo) {
            // Status-only exceptions (see the status handler) and I/O failures: the starter maps 502-504 and
            // unreachable backends to upstream_unavailable.
            throw statusOrIo;
        } catch (RestClientException unreadable) {
            // A 2xx body that does not parse: the converter's message quotes the body.
            throw new BackendResponseException("body not readable");
        }
    }

    private static String requireBaseUrl(String baseUrl, boolean plainHttpAllowed) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("example.backend.base-url (MCP_EXAMPLE_URL) must point at the backend API");
        }
        URI uri = URI.create(baseUrl);
        if (uri.getHost() == null || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))) {
            throw new IllegalStateException("example.backend.base-url must be an absolute http(s) URL");
        }
        if (!"https".equals(uri.getScheme())) {
            if (!plainHttpAllowed) {
                // The user's credential travels in a header; over http it would cross the network in clear text.
                throw new IllegalStateException("example.backend.base-url must use https outside the 'dev' profile "
                        + "(example.backend.allow-plain-http exists for the mocks of mcp-platform-dev only)");
            }
            LOG.warn("event=backend_plain_http detail=\"credentials go to the backend unencrypted\"");
        }
        return baseUrl;
    }

    private static JdkClientHttpRequestFactory requestFactory(ExampleBackendProperties properties,
            ObjectProvider<SslBundles> sslBundles) {
        // Redirects are not followed: the credential header must not travel to wherever a backend points.
        HttpClient.Builder http = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER);
        if (properties.sslBundle() != null && !properties.sslBundle().isBlank()) {
            http.sslContext(sslBundles.getObject().getBundle(properties.sslBundle()).createSslContext());
        }
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http.build());
        factory.setReadTimeout(properties.readTimeout());
        return factory;
    }
}
