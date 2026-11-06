package pl.aiwcorpo.mcp.example.integration;

import java.util.List;
import java.util.Map;
import pl.aiwcorpo.mcp.cedar.entities.ResolvedResource;
import pl.aiwcorpo.mcp.cedar.entities.Scopes;
import pl.aiwcorpo.mcp.example.core.domain.ExampleRecord;
import pl.aiwcorpo.mcp.example.core.domain.ExampleResourceModel;

/**
 * The record as the policy sees it (C7, entity-builder.md): the resource type, its canonical id, the container as its
 * parent and the scopes, with the attributes {@code mcp/policies/resource-types.json} declares for them.
 *
 * <p>Id and parent come from the backend's answer, never from the tool argument: a record moved to a restricted
 * project is judged by the project it is in now. {@code Example::Project.category} is the classification, required,
 * {@code ""} meaning "no category"; a project whose category the backend did not report gets no attribute, and the
 * builder refuses it (fail closed).
 *
 * <p>Scopes are what a group can be granted on: the project and, because a record lies in it, the same project for
 * the record. A system with a deeper container tree lists every container of the path here, nearest last.
 */
final class ExampleResourceDescription {

    private ExampleResourceDescription() {
    }

    static ResolvedResource of(ExampleRecord record) {
        List<String> scopes = List.of(Scopes.id(ExampleResourceModel.PARENT_TYPE, record.project()));
        Map<String, Object> project = record.projectClassification() == null
                ? Map.of("key", record.project())
                : Map.of("key", record.project(), "category", record.projectClassification());
        ResolvedResource container = ResolvedResource.of(ExampleResourceModel.PARENT_TYPE, record.project(), project)
                .withScopes(scopes);
        return ResolvedResource.of(ExampleResourceModel.RECORD_TYPE, record.id(), Map.of("key", record.id()))
                .withParent(container).withScopes(scopes);
    }
}
