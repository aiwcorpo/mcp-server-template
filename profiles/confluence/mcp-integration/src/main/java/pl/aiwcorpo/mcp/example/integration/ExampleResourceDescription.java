package pl.aiwcorpo.mcp.example.integration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import pl.aiwcorpo.mcp.cedar.entities.ResolvedResource;
import pl.aiwcorpo.mcp.example.core.domain.ExampleRecord;
import pl.aiwcorpo.mcp.example.core.domain.ExampleResourceModel;

/**
 * The record as the policy sees it (C7, entity-builder.md): a Confluence page and its space. {@code new-server.sh}
 * replaces this file with the one of the generated system.
 *
 * <p>{@code Confluence::Space.labels} is required; a space whose labels the backend did not report gets no attribute
 * and the builder refuses it (fail closed). {@code ancestorIds} are the ids of the ancestor pages as Confluence reports
 * them (they decide which page-tree grants apply); the sample page is a root page.
 */
final class ExampleResourceDescription {

    private ExampleResourceDescription() {
    }

    static ResolvedResource of(ExampleRecord record) {
        Map<String, Object> space = new LinkedHashMap<>();
        space.put("key", record.project());
        if (record.projectClassification() != null) {
            space.put("labels", record.projectClassification().isBlank() ? List.of() : List.of(record.projectClassification()));
        }
        return ResolvedResource.of(ExampleResourceModel.RECORD_TYPE, record.id(),
                        Map.of("id", record.id(), "ancestorIds", List.of()))
                .withParent(ResolvedResource.of(ExampleResourceModel.PARENT_TYPE, record.project(), space));
    }
}
