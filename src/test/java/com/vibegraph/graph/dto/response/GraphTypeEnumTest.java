package com.vibegraph.graph.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Graph type enum contracts")
class GraphTypeEnumTest {

    @Test
    @DisplayName("NodeTypeEnum exposes the backend/frontend node labels")
    void nodeTypeLabelsMatchGraphContract() {
        assertThat(Arrays.stream(NodeTypeEnum.values()).map(NodeTypeEnum::label))
                .containsExactly(
                        "Project", "Package", "File", "Class", "Interface", "Enum",
                        "Record", "DBModel", "Method", "Constructor", "Field", "Annotation",
                        "Route", "APIEndpoint", "External")
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("EdgeTypeEnum exposes the backend/frontend relationship types")
    void edgeTypeLabelsMatchGraphContract() {
        assertThat(Arrays.stream(EdgeTypeEnum.values()).map(EdgeTypeEnum::label))
                .containsExactly(
                        "OWNS", "CONTAINS", "DEFINES", "HAS_METHOD", "HAS_FIELD", "HAS_INNER",
                        "EXTENDS", "IMPLEMENTS", "OVERRIDES", "IMPORTS", "TYPE_OF", "RETURNS",
                        "PARAMETER_TYPE", "THROWS", "CALLS", "INJECTS", "HANDLES_ROUTE", "ANNOTATED_BY")
                .doesNotHaveDuplicates();
    }
}
