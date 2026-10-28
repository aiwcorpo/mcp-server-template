package pl.aiwcorpo.mcp.example.integration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import pl.aiwcorpo.mcp.cedar.entities.ResolvedResource;
import pl.aiwcorpo.mcp.example.core.domain.ExampleRecord;
import pl.aiwcorpo.mcp.example.core.domain.ExampleResourceModel;

/**
 * The record as the policy sees it (C7, entity-builder.md): a service desk ticket and its queue. {@code new-server.sh}
 * replaces this file with the one of the generated system.
 *
 * <p>The queue classification is optional here: a queue whose classification the backend did not report is described
 * without it, and the ticket is classified through its queue only when there is one.
 */
final class ExampleResourceDescription {

    private ExampleResourceDescription() {
    }

    static ResolvedResource of(ExampleRecord record) {
        Map<String, Object> queue = new LinkedHashMap<>();
        queue.put("id", record.project());
        if (record.projectClassification() != null) {
            queue.put("classification", record.projectClassification().isBlank() ? List.of() : List.of(record.projectClassification()));
        }
        return ResolvedResource.of(ExampleResourceModel.RECORD_TYPE, record.id(), Map.of("id", record.id()))
                .withParent(ResolvedResource.of(ExampleResourceModel.PARENT_TYPE, record.project(), queue));
    }
}
