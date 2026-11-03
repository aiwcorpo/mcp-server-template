package pl.aiwcorpo.mcp.example.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import pl.aiwcorpo.mcp.example.core.domain.ExampleRecord;
import pl.aiwcorpo.mcp.example.core.domain.ExampleRecordHit;
import pl.aiwcorpo.mcp.example.core.domain.ServerInfo;
import pl.aiwcorpo.mcp.example.core.domain.CommentPage;
import pl.aiwcorpo.mcp.example.core.domain.RecordComment;
import pl.aiwcorpo.mcp.example.core.port.out.ExampleResourcePort;

/**
 * The paths and answers of the backend API. Credentials, timeouts, redirects, size limits and the mapping of failures
 * are in {@link ExampleBackendClient}; here a 404 and a 403 both become "not visible" (an empty result, one
 * client-facing code {@code not_found_or_denied}) and every answer is read into the fields the domain needs.
 */
@Component
public class ExampleResourceAdapter implements ExampleResourcePort {

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
        // Ids were checked against the pattern of their type by the starter; template variables encode them anyway.
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

    @Override
    public List<ExampleRecordHit> search(String query, int limit) {
        // The query is a value of a template variable, so it is encoded as one parameter whatever it contains.
        SearchResponse response = backend.get(SearchResponse.class, "/api/records?query={query}&limit={limit}", query, limit);
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

    @Override
    public CommentPage listComments(String recordId, int start, int limit) {
        CommentsResponse response;
        try {
            response = backend.get(CommentsResponse.class, "/api/records/{id}/comments?start={start}&limit={limit}",
                    recordId, start, limit);
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.Forbidden hidden) {
            return new CommentPage(List.of(), null);
        }
        if (response == null || response.comments() == null) {
            throw new BackendResponseException("empty body");
        }
        int returned = response.comments().size();
        Integer next = response.hasMore() && returned > 0 ? start + returned : null;
        return new CommentPage(response.comments().stream().map(CommentResponse::toDomain).toList(), next);
    }

    @Override
    public Optional<RecordComment> findComment(String recordId, String commentId) {
        CommentResponse response;
        try {
            response = backend.get(CommentResponse.class, "/api/records/{id}/comments/{commentId}", recordId, commentId);
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.Forbidden hidden) {
            return Optional.empty();
        }
        if (response == null) {
            throw new BackendResponseException("empty body");
        }
        return Optional.of(response.toDomain());
    }

    @Override
    public Optional<ExampleRecord> updateTitle(String recordId, String title) {
        RecordResponse response;
        try {
            response = backend.put(RecordResponse.class, "/api/records/{id}", Map.of("title", title), recordId);
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.Forbidden hidden) {
            return Optional.empty();
        }
        if (response == null) {
            throw new BackendResponseException("empty body");
        }
        return Optional.of(response.toDomain());
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CommentResponse(String id, String recordId, String author, String body) {

        RecordComment toDomain() {
            if (id == null || id.isBlank()) {
                throw new BackendResponseException("comment without id");
            }
            return new RecordComment(id, recordId, author, body);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CommentsResponse(List<CommentResponse> comments, boolean hasMore) {
    }
}
