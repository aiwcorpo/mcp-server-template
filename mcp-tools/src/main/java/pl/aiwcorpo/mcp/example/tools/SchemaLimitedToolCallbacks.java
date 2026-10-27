package pl.aiwcorpo.mcp.example.tools;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.ai.tool.support.ToolDefinitions;
import org.springframework.aop.support.AopUtils;
import org.springframework.util.ReflectionUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Builds the tool callbacks the way {@code MethodToolCallbackProvider} does, then adds the {@link ArgumentLimits}
 * of each parameter to the generated {@code inputSchema}: Spring AI 2.0 generates only type and description for
 * method parameters. The callbacks stay {@link MethodToolCallback}s, so the starter finds {@code @ToolRisk} on the
 * method and wraps them as usual.
 */
public final class SchemaLimitedToolCallbacks {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private SchemaLimitedToolCallbacks() {
    }

    public static ToolCallbackProvider of(Object... toolObjects) {
        List<ToolCallback> callbacks = new ArrayList<>();
        for (Object toolObject : toolObjects) {
            for (Method method : ReflectionUtils.getUniqueDeclaredMethods(AopUtils.getTargetClass(toolObject))) {
                if (method.isAnnotationPresent(Tool.class)) {
                    callbacks.add(callback(toolObject, method));
                }
            }
        }
        if (callbacks.isEmpty()) {
            throw new IllegalStateException("no @Tool methods in the tool objects");
        }
        callbacks.sort(Comparator.comparing(callback -> callback.getToolDefinition().name()));
        return ToolCallbackProvider.from(callbacks);
    }

    private static MethodToolCallback callback(Object toolObject, Method method) {
        String schema = withLimits(ToolDefinitions.from(method).inputSchema(), method);
        return MethodToolCallback.builder()
                .toolDefinition(ToolDefinitions.builder(method).inputSchema(schema).build())
                .toolMetadata(ToolMetadata.from(method))
                .toolMethod(method)
                .toolObject(toolObject)
                .build();
    }

    static String withLimits(String inputSchema, Method method) {
        JsonNode schema = JSON.readTree(inputSchema);
        for (Parameter parameter : method.getParameters()) {
            ArgumentLimits limits = parameter.getAnnotation(ArgumentLimits.class);
            if (limits == null) {
                continue;
            }
            JsonNode property = schema.path("properties").path(parameter.getName());
            if (!(property instanceof ObjectNode node) || !"string".equals(node.path("type").asString(null))) {
                throw new IllegalStateException("@ArgumentLimits on " + method.getName() + "(" + parameter.getName()
                        + "): only string arguments present in the schema (compile with -parameters)");
            }
            if (limits.maxLength() > 0) {
                node.put("maxLength", limits.maxLength());
            }
            if (!limits.pattern().isEmpty()) {
                node.put("pattern", limits.pattern());
            }
        }
        return JSON.writeValueAsString(schema);
    }
}
