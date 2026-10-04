package pl.aiwcorpo.mcp.integration;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import pl.aiwcorpo.mcp.core.domain.ExampleRecord;
import pl.aiwcorpo.mcp.core.port.out.ExampleResourcePort;
import pl.aiwcorpo.mcp.platform.security.CredentialContext;

/**
 * Outbound adapter — calls the backend REST API on behalf of the caller, with THEIR credential.
 * Active unless {@code example.backend.mode=mock} (see {@link InMemoryExampleResourceAdapter}).
 *
 * <p>There is no service-account token: every user acts as themselves in the backend. Which
 * credential that is follows the server's {@code auth-mode} — the caller's access token (oauth) or
 * their {@code X-PAT-Example} token (pat). The adapter does not need to know which;
 * {@link CredentialContext#requireBackendToken} returns the right one.
 *
 * <p>Harden before production: connect/read timeouts, retry with backoff, circuit breaker, bounded
 * pools, and an internal-CA truststore for outbound TLS. Never log the PAT.
 */
@Component
@ConditionalOnProperty(name = "example.backend.mode", havingValue = "rest", matchIfMissing = true)
public class ExampleResourceAdapter implements ExampleResourcePort {

    /** Backend key; in PAT mode it matches the inbound header {@code X-PAT-Example}. */
    private static final String SYSTEM = "example";

    private final RestClient client;

    public ExampleResourceAdapter(@Value("${example.backend.base-url:}") String baseUrl) {
        this.client = RestClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public Optional<ExampleRecord> findById(String id) {
        ExampleRecord record = client.get()
                .uri("/records/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(ExampleRecord.class);
        return Optional.ofNullable(record);
    }

    @Override
    public ExampleRecord save(ExampleRecord record) {
        ExampleRecord saved = client.put()
                .uri("/records/{id}", record.id())
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .body(record)
                .retrieve()
                .body(ExampleRecord.class);
        return saved == null ? record : saved;
    }

    @Override
    public int deleteAll() {
        Integer removed = client.delete()
                .uri("/records")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(Integer.class);
        return removed == null ? 0 : removed;
    }

    /** The caller's own credential: their PAT in PAT mode, their access token in OAuth mode. */
    private static String bearer() {
        return "Bearer " + CredentialContext.requireBackendToken(SYSTEM);
    }
}
