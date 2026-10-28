package pl.aiwcorpo.mcp.example.integration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import pl.aiwcorpo.mcp.cedar.entities.ResolvedResource;
import pl.aiwcorpo.mcp.example.core.domain.ExampleRecord;
import pl.aiwcorpo.mcp.example.core.domain.ExampleResourceModel;

/**
 * The record as the policy sees it (C7, entity-builder.md): an OpenSearch index. {@code new-server.sh} replaces this
 * file with the one of the generated system.
 *
 * <p>The sample record is an index and its classification the index tags ({@code OpenSearch::Index.tags} is required,
 * {@code []} meaning none; the OpenSearch server takes them from its own configuration, OpenSearch has no tags). An
 * index whose tags are not known gets no attribute and the builder refuses it (fail closed). Reading is granted by
 * index prefix, so the test caller carries the groups of the sample prefixes.
 */
final class ExampleResourceDescription {

    private ExampleResourceDescription() {
    }

    static ResolvedResource of(ExampleRecord record) {
        Map<String, Object> index = new LinkedHashMap<>();
        index.put("name", record.id());
        if (record.projectClassification() != null) {
            index.put("tags", record.projectClassification().isBlank() ? List.of() : List.of(record.projectClassification()));
        }
        return ResolvedResource.of(ExampleResourceModel.RECORD_TYPE, record.id(), index);
    }
}
