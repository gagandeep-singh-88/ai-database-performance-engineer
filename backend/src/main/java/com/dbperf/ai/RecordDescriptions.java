package com.dbperf.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.lang.reflect.RecordComponent;

/**
 * {@code @JsonPropertyDescription} does not include RECORD_COMPONENT in its
 * {@code @Target}, so javac propagates it to the accessor method (and backing
 * field) rather than the component itself — {@code RecordComponent.getAnnotation}
 * always returns null for it. Read it from the accessor instead.
 */
final class RecordDescriptions {

    private RecordDescriptions() {
    }

    static String describe(RecordComponent component) {
        JsonPropertyDescription annotation =
                component.getAccessor().getAnnotation(JsonPropertyDescription.class);
        return annotation == null ? null : annotation.value();
    }
}
