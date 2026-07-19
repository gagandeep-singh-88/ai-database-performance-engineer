package com.dbperf.ai;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.List;

/**
 * Derives a JSON Schema from a record class, for providers that support
 * grammar-constrained structured output (e.g. Ollama's format field).
 * Constrained decoding matters most for small local models, which cannot
 * be trusted to follow a schema from prompt text alone.
 */
public final class JsonSchemaGenerator {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private JsonSchemaGenerator() {
    }

    public static ObjectNode schemaFor(Class<?> type) {
        return (ObjectNode) schema(type);
    }

    private static Object nullableString() {
        ArrayNode types = NODES.arrayNode();
        types.add("string");
        types.add("null");
        return types;
    }

    private static com.fasterxml.jackson.databind.JsonNode schema(Type type) {
        if (type instanceof ParameterizedType parameterized
                && parameterized.getRawType() == List.class) {
            ObjectNode array = NODES.objectNode();
            array.put("type", "array");
            array.set("items", schema(parameterized.getActualTypeArguments()[0]));
            return array;
        }
        if (type instanceof Class<?> clazz) {
            if (clazz == String.class) {
                ObjectNode string = NODES.objectNode();
                string.set("type", (com.fasterxml.jackson.databind.JsonNode) nullableString());
                return string;
            }
            if (clazz == int.class || clazz == long.class || clazz == Integer.class || clazz == Long.class) {
                return NODES.objectNode().put("type", "integer");
            }
            if (clazz == double.class || clazz == Double.class || clazz == float.class || clazz == Float.class) {
                return NODES.objectNode().put("type", "number");
            }
            if (clazz == boolean.class || clazz == Boolean.class) {
                return NODES.objectNode().put("type", "boolean");
            }
            if (clazz.isRecord()) {
                ObjectNode object = NODES.objectNode();
                object.put("type", "object");
                ObjectNode properties = object.putObject("properties");
                ArrayNode required = object.putArray("required");
                for (RecordComponent component : clazz.getRecordComponents()) {
                    com.fasterxml.jackson.databind.JsonNode propertySchema = schema(component.getGenericType());
                    String description = RecordDescriptions.describe(component);
                    if (description != null && propertySchema instanceof ObjectNode objectSchema) {
                        objectSchema.put("description", description);
                    }
                    properties.set(component.getName(), propertySchema);
                    required.add(component.getName());
                }
                return object;
            }
        }
        return NODES.objectNode().put("type", "string");
    }
}
