package pl.aiwcorpo.mcp.example.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import pl.aiwcorpo.mcp.example.core.domain.ExampleRecord;
import pl.aiwcorpo.mcp.example.core.domain.ExampleRecordHit;
import pl.aiwcorpo.mcp.example.core.domain.ServerInfo;
import pl.aiwcorpo.mcp.example.core.port.out.ExampleResourcePort;
import pl.aiwcorpo.mcp.platform.resource.QueryScope;

/**
 * The paths and answers of the backend API. Credentials, timeouts, redirects, size limits and the mapping of failures
 * are in {@link ExampleBackendClient}; here a 404 and a 403 both become "not visible" (an empty result, one
 * client-facing code {@code not_found_or_denied}) and every answer is read into the fields the domain needs.
 */
@Component
public class ExampleResourceAdapter implements ExampleResourcePort {

    /** The search parameters of the starter for a request to a list of index names: open indices, no error for a vanished name. */
    private static final String SEARCH_PARAMETERS = QueryScope.OPENSEARCH_SEARCH_PARAMETERS.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(parameter -> "&" + parameter.getKey() + "=" + parameter.getValue())
            .collect(Collectors.joining());

    private static final String RESOLVE_PARAMETERS = QueryScope.OPENSEARCH_RESOLVE_PARAMETERS.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(parameter -> parameter.getKey() + "=" + parameter.getValue())
            .collect(Collectors.joining("&", "?", ""));

    private final ExampleBackendClient backend;

    public ExampleResourceAdapter(ExampleBackendClient backend) {
        this.backend = backend;
    }

    @Override
    public Optional<ExampleRecord> findById(String recordId) {
        RecordResponse response;
        try {
            response = backend.get(RecordResponse.class, "/api/records/{id}", recordId);
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.Forbidden hidden) {
            return Optional.empty();
        }
        if (response == null) {
            throw new BackendResponseException("empty body");
        }
        return Optional.of(response.toDomain());
    }

    @Override
    public Map<String, ExampleRecord> findAllById(Collection<String> recordIds) {
        SearchResponse response = backend.get(SearchResponse.class, "/api/records?ids={ids}", String.join(",", recordIds));
        if (response == null || response.records() == null) {
            throw new BackendResponseException("empty body");
        }
        Map<String, ExampleRecord> found = new LinkedHashMap<>();
        for (RecordResponse record : response.records()) {
            ExampleRecord domain = record.toDomain();
            found.put(domain.id(), domain);
        }
        return found;
    }

    /** Patterns go here and nowhere else: a search names indices, so an alias that matches a pattern is never queried. */
    @Override
    public String resolveIndices(String expression) {
        String answer;
        try {
            answer = backend.get(String.class, "/_resolve/index/{expression}" + RESOLVE_PARAMETERS, expression);
        } catch (HttpClientErrorException.NotFound noIndex) {
            // The plain name of the pair "p,p-*" need not exist; a 404 means no index matches (not confirmed on a
            // live cluster, so the answer is not cached and every search resolves again).
            return QueryScope.EMPTY_RESOLVE_RESPONSE;
        }
        if (answer == null) {
            throw new BackendResponseException("empty body");
        }
        return answer;
    }

    @Override
    public List<ExampleRecordHit> search(List<String> indices, String text, int limit) {
        SearchResponse response = backend.get(SearchResponse.class,
                "/api/records?query={query}&indices={indices}&limit={limit}" + SEARCH_PARAMETERS,
                text, String.join(",", indices), limit);
        if (response == null || response.records() == null) {
            throw new BackendResponseException("empty body");
        }
        return response.records().stream().map(RecordResponse::toHit).toList();
    }

    @Override
    public ServerInfo serverInfo() {
        ServerInfoResponse response = backend.getWithoutCredential(ServerInfoResponse.class, "/api/server-info");
        if (response == null) {
            throw new BackendResponseException("empty body");
        }
        return new ServerInfo(response.version());
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ServerInfoResponse(String version) {
    }
}
