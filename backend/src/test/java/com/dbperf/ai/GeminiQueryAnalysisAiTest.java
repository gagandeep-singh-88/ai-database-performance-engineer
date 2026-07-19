package com.dbperf.ai;

import com.dbperf.common.exception.AiUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiQueryAnalysisAiTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void shapeDescriberRendersNestedRecordsWithDescriptions() {
        String shape = JsonShapeDescriber.describe(AiQueryAnalysis.class);

        assertThat(shape)
                .contains("\"summary\": string | null")
                .contains("\"issues\": [")
                .contains("\"severity\": string | null")
                .contains("// One of: HIGH, MEDIUM, LOW, INFO")
                .contains("\"recommendations\": [")
                .contains("\"optimizedSql\": string | null");
    }

    @Test
    void parsesPlainJson() {
        String json = """
                {"summary":"s","issues":[{"severity":"HIGH","type":"SEQ_SCAN","description":"d"}],
                 "recommendations":[],"optimizedSql":null,"estimatedImprovement":"2x","planExplanation":null}""";

        AiQueryAnalysis result = GeminiQueryAnalysisAi.parseJson(mapper, json, AiQueryAnalysis.class);

        assertThat(result.summary()).isEqualTo("s");
        assertThat(result.issues()).hasSize(1);
        assertThat(result.estimatedImprovement()).isEqualTo("2x");
    }

    @Test
    void stripsMarkdownFencesBeforeParsing() {
        String fenced = """
                ```json
                {"summary":"fenced","issues":[],"recommendations":[],
                 "optimizedSql":null,"estimatedImprovement":null,"planExplanation":null}
                ```""";

        AiQueryAnalysis result = GeminiQueryAnalysisAi.parseJson(mapper, fenced, AiQueryAnalysis.class);

        assertThat(result.summary()).isEqualTo("fenced");
    }

    @Test
    void unparseableOutputRaisesAiUnavailable() {
        assertThatThrownBy(() -> GeminiQueryAnalysisAi.parseJson(mapper, "not json at all", AiQueryAnalysis.class))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("unparseable");
    }

    @Test
    void toleratesExtraFieldsFromTheModel() {
        String withExtras = """
                {"summary":"s","issues":[],"recommendations":[],"optimizedSql":null,
                 "estimatedImprovement":null,"planExplanation":null,"confidence":"high"}""";
        ObjectMapper lenient = mapper.copy()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        AiQueryAnalysis result = GeminiQueryAnalysisAi.parseJson(lenient, withExtras, AiQueryAnalysis.class);

        assertThat(result.summary()).isEqualTo("s");
    }
}
