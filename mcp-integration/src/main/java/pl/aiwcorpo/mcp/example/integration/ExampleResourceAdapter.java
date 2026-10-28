package pl.aiwcorpo.mcp.example.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Optional;
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
import pl.aiwcorpo.mcp.example.core.domain.ExampleRecord;
import pl.aiwcorpo.mcp.example.core.domain.ExampleRecordHit;
import pl.aiwcorpo.mcp.example.core.port.out.ExampleResourcePort;
import pl.aiwcorpo.mcp.platform.security.BackendCredential;
import pl.aiwcorpo.mcp.platform.security.CredentialContext;
import pl.aiwcorpo.mcp.platform.security.CredentialRequiredException;

/**
 * Calls the backend API as the user: with the credential the gateway resolved for this request (C3), never
 * with an account of the server.
 *
 * <p>Error mapping matters for security: 404 and 403 both become "not visible" (one client-facing code,
 * {@code not_found_or_denied}), and 401 means the stored credential no longer works, so the user is sent to
 * the portal to reconnect ({@code credential_required}). A failed call becomes an exception with the status
 * alone, an unusable answer (redirect, unparsable or oversized body) a {@link BackendResponseException}, so no
 * response body reaches a message or a log line.
 */
@Component
@EnableConfigurationProperties(ExampleBackendProperties.class)
public class ExampleResourceAdapter implements ExampleResourcePort {

    private static final String DEV_PROFILE = "dev";
    private static final String SERVER_ID_PROPERTY = "aiwcorpo.mcp.platform.server-id";
    private static final Logger LOG = LoggerFactory.getLogger(ExampleResourceAdapter.class);

    private final RestClient client;
    private final String system;

    public ExampleResourceAdapter(ExampleBackendProperties properties, ObjectProvider<SslBundles> sslBundles,
            Environment environment) {
        // The credential of the request is bound to the system of the server id (mcp-<system>), so the adapter asks
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

    @Override
    public Optional<ExampleRecord> findById(String recordId) {
        BackendCredential credential = CredentialContext.require(system);
        RecordResponse response;
        try {
            response = call(() -> client.get()
                    .uri("/api/records/{id}", recordId)
                    .headers(credential::applyTo)
                    .retrieve()
                    .body(RecordResponse.class));
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.Forbidden hidden) {
            return Optional.empty();
        }
        if (response == null) {
            throw new BackendResponseException("empty body");
        }
        return Optional.of(response.toDomain());
    }

    @Override
    public List<ExampleRecordHit> search(String project, String text, int limit) {
        BackendCredential credential = CredentialContext.require(system);
        // Values go through URI template variables, which encode them: a text with "&project=HR" stays one value.
        SearchResponse response = call(() -> client.get()
                .uri("/api/records?project={project}&q={text}&limit={limit}", project, text, limit)
                .headers(credential::applyTo)
                .retrieve()
                .body(SearchResponse.class));
        if (response == null || response.records() == null) {
            throw new BackendResponseException("empty body");
        }
        return response.records().stream().map(RecordResponse::toHit).toList();
    }

    /**
     * Runs one backend call and maps its failures. 404 and 403 pass through as they are: for a single record they
     * mean "not visible" (the caller turns them into an empty result, one client-facing code
     * {@code not_found_or_denied}), for a search they are plain failures.
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

    /** The backend's representation; unknown fields are dropped here and never reach the model. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RecordResponse(String id, String project, String projectClassification, String title, String status) {

        ExampleRecord toDomain() {
            if (id == null || id.isBlank()) {
                throw new BackendResponseException("record without id");
            }
            if (project == null || project.isBlank()) {
                throw new BackendResponseException("record without project");
            }
            return new ExampleRecord(id, project, projectClassification, title, status);
        }

        ExampleRecordHit toHit() {
            if (id == null || id.isBlank()) {
                throw new BackendResponseException("hit without id");
            }
            return new ExampleRecordHit(id, title);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SearchResponse(List<RecordResponse> records) {
    }
}
