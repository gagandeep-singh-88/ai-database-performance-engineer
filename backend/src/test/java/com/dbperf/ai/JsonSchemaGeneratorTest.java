package com.dbperf.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonSchemaGeneratorTest {

    @Test
    void generatesObjectSchemaWithAllFieldsRequired() {
        ObjectNode schema = JsonSchemaGenerator.schemaFor(AiQueryAnalysis.class);

        assertThat(schema.path("type").asText()).isEqualTo("object");
        assertThat(schema.path("properties").has("summary")).isTrue();
        assertThat(schema.path("properties").has("issues")).isTrue();
        assertThat(schema.path("required")).hasSize(6);
    }

    @Test
    void nestedListsBecomeArraysOfObjectSchemas() {
        ObjectNode schema = JsonSchemaGenerator.schemaFor(AiQueryAnalysis.class);
        JsonNode issues = schema.path("properties").path("issues");

        assertThat(issues.path("type").asText()).isEqualTo("array");
        assertThat(issues.path("items").path("type").asText()).isEqualTo("object");
        assertThat(issues.path("items").path("properties").has("severity")).isTrue();
    }

    @Test
    void stringsAreNullableAndDescriptionsPropagate() {
        ObjectNode schema = JsonSchemaGenerator.schemaFor(AiQueryAnalysis.class);
        JsonNode optimizedSql = schema.path("properties").path("optimizedSql");

        assertThat(optimizedSql.path("type").isArray()).isTrue();
        assertThat(optimizedSql.path("type").toString()).contains("string").contains("null");
        assertThat(optimizedSql.path("description").asText()).contains("optimized");
    }
}
