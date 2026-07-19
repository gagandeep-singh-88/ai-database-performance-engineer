package com.dbperf.ai;


import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.List;

/**
 * Renders a record class as a JSON "shape" for providers without native
 * schema-constrained output (e.g. Gemini's responseSchema subset). The shape
 * plus responseMimeType=application/json reliably yields parseable JSON.
 */
public final class JsonShapeDescriber {

    private JsonShapeDescriber() {
    }

    public static String describe(Class<?> type) {
        StringBuilder out = new StringBuilder();
        append(out, type, 0);
        return out.toString();
    }

    private static void append(StringBuilder out, Type type, int indent) {
        if (type instanceof ParameterizedType parameterized
                && parameterized.getRawType() == List.class) {
            out.append("[\n").append("  ".repeat(indent + 1));
            append(out, parameterized.getActualTypeArguments()[0], indent + 1);
            out.append("\n").append("  ".repeat(indent)).append("]");
            return;
        }
        if (type instanceof Class<?> clazz) {
            if (clazz == String.class) {
                out.append("string | null");
            } else if (clazz == int.class || clazz == long.class
                    || clazz == Integer.class || clazz == Long.class) {
                out.append("integer");
            } else if (clazz == double.class || clazz == Double.class
                    || clazz == float.class || clazz == Float.class) {
                out.append("number");
            } else if (clazz == boolean.class || clazz == Boolean.class) {
                out.append("boolean");
            } else if (clazz.isRecord()) {
                appendRecord(out, clazz, indent);
            } else {
                out.append("string");
            }
            return;
        }
        out.append("string");
    }

    private static void appendRecord(StringBuilder out, Class<?> record, int indent) {
        out.append("{\n");
        RecordComponent[] components = record.getRecordComponents();
        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];
            out.append("  ".repeat(indent + 1))
                    .append('"').append(component.getName()).append("\": ");
            append(out, component.getGenericType(), indent + 1);
            if (i < components.length - 1) {
                out.append(',');
            }
            String description = RecordDescriptions.describe(component);
            if (description != null) {
                out.append("  // ").append(description);
            }
            out.append('\n');
        }
        out.append("  ".repeat(indent)).append("}");
    }
}
