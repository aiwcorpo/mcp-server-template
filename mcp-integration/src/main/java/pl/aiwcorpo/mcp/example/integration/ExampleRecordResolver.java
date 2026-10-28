package pl.aiwcorpo.mcp.example.integration;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import pl.aiwcorpo.mcp.cedar.entities.ResolvedResource;
import pl.aiwcorpo.mcp.example.core.domain.ExampleRecord;
import pl.aiwcorpo.mcp.example.core.domain.ExampleResourceModel;
import pl.aiwcorpo.mcp.example.core.port.out.ExampleResourcePort;
import pl.aiwcorpo.mcp.platform.resource.ResourceResolver;

/**
 * Tells the starter what a record id is, before the tool runs: it asks the backend with the caller's own credential
 * and describes the answer for the policy. The same resolver judges the elements of a search result.
 *
 * <p>{@link #fetch} is empty for "not there" and "not allowed for this user" alike (the adapter maps 404 and 403 to
 * it) and throws for a backend that fails, which the client sees as {@code upstream_unavailable}, not as a denial.
 */
@Component
public class ExampleRecordResolver implements ResourceResolver<ExampleRecord> {

    private final ExampleResourcePort resource;

    public ExampleRecordResolver(ExampleResourcePort resource) {
        this.resource = resource;
    }

    @Override
    public String resourceType() {
        return ExampleResourceModel.RECORD_TYPE;
    }

    @Override
    public Optional<ExampleRecord> fetch(String recordId) {
        return resource.findById(recordId);
    }

    /**
     * One backend call for all the hits of a search (the starter resolves every element of a result). A record the
     * backend answers under another id than the one asked (a moved record) is looked up on its own.
     */
    @Override
    public Map<String, ExampleRecord> fetchAll(Collection<String> recordIds) {
        Map<String, ExampleRecord> byId = resource.findAllById(recordIds);
        Map<String, ExampleRecord> fetched = new LinkedHashMap<>();
        for (String id : recordIds) {
            ExampleRecord record = byId.get(id);
            if (record != null) {
                fetched.put(id, record);
            } else {
                resource.findById(id).ifPresent(single -> fetched.put(id, single));
            }
        }
        return fetched;
    }

    @Override
    public ResolvedResource describe(ExampleRecord record) {
        return ExampleResourceDescription.of(record);
    }
}
