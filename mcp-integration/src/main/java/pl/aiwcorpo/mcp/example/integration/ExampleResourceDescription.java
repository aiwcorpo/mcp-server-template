package pl.aiwcorpo.mcp.example.integration;

import java.util.Map;
import pl.aiwcorpo.mcp.cedar.entities.ResolvedResource;
import pl.aiwcorpo.mcp.example.core.domain.ExampleRecord;
import pl.aiwcorpo.mcp.example.core.domain.ExampleResourceModel;

/**
 * The record as the policy sees it (C7, entity-builder.md): the resource type, its canonical id and the container as
 * its parent with the attributes the policies read. {@code new-server.sh} replaces this file with the one of the
 * generated system; the Jira variant is the template's own.
 *
 * <p>Id and parent come from the backend's answer, never from the tool argument: a record moved to a restricted
 * project is judged by the project it is in now. {@code Jira::Project.category} is required, {@code ""} meaning "no
 * category"; a project whose category the backend did not report gets no attribute, and the builder refuses it
 * (fail closed).
 */
final class ExampleResourceDescription {

    private ExampleResourceDescription() {
    }

    static ResolvedResource of(ExampleRecord record) {
        Map<String, Object> project = record.projectClassification() == null
                ? Map.of("key", record.project())
                : Map.of("key", record.project(), "category", record.projectClassification());
        return ResolvedResource.of(ExampleResourceModel.RECORD_TYPE, record.id(), Map.of("key", record.id()))
                .withParent(ResolvedResource.of(ExampleResourceModel.PARENT_TYPE, record.project(), project));
    }
}
