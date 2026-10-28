package pl.aiwcorpo.mcp.example.integration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import pl.aiwcorpo.mcp.cedar.entities.ResolvedResource;
import pl.aiwcorpo.mcp.example.core.domain.ExampleRecord;
import pl.aiwcorpo.mcp.example.core.domain.ExampleResourceModel;

/**
 * The record as the policy sees it (C7, entity-builder.md): a GitLab project and its group. {@code new-server.sh}
 * replaces this file with the one of the generated system.
 *
 * <p>Topics are not inherited in GitLab, so a group classified {@code restricted} protects its subtree only through
 * {@code ancestorClassifications} (CC-129): a map from the path of every ancestor group to its topics, exactly the
 * groups above the resource, the parent group included for a project, and {@code {}} written out for a top-level
 * group. The sample record lives in a top-level group, whose topics the backend reports; a real resolver asks GitLab
 * for the topics of each group on the path (once per call) and builds the map from them. A map that is missing or
 * incomplete makes the builder refuse the resource (fail closed), which is also what happens here when the backend
 * did not report the classification.
 */
final class ExampleResourceDescription {

    private ExampleResourceDescription() {
    }

    static ResolvedResource of(ExampleRecord record) {
        String group = record.project();
        Map<String, Object> groupAttributes = new LinkedHashMap<>();
        groupAttributes.put("path", group);
        Map<String, Object> projectAttributes = new LinkedHashMap<>();
        projectAttributes.put("path", record.id());
        projectAttributes.put("classification", List.of());
        if (record.projectClassification() != null) {
            List<String> topics = record.projectClassification().isBlank() ? List.of() : List.of(record.projectClassification());
            groupAttributes.put("classification", topics);
            groupAttributes.put("ancestorClassifications", Map.of());
            projectAttributes.put("ancestorClassifications", Map.of(group, topics));
        }
        return ResolvedResource.of(ExampleResourceModel.RECORD_TYPE, record.id(), projectAttributes)
                .withParent(ResolvedResource.of(ExampleResourceModel.PARENT_TYPE, group, groupAttributes));
    }
}
