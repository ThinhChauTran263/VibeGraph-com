package com.vibegraph.graph.repository.impl.neo4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("GraphSchema (Cypher whitelist)")
class GraphSchemaTest {

    @Nested
    @DisplayName("node labels")
    class NodeLabels {

        @Test
        @DisplayName("accepts known labels including the External stub")
        void acceptsKnownLabels() {
            assertThat(GraphSchema.nodeLabel("Class")).isEqualTo("Class");
            assertThat(GraphSchema.nodeLabel("Route")).isEqualTo("Route");
            assertThat(GraphSchema.nodeLabel("External")).isEqualTo("External");
        }

        @Test
        @DisplayName("rejects unknown / injected labels")
        void rejectsUnknownLabels() {
            assertThatThrownBy(() -> GraphSchema.nodeLabel("Class) DETACH DELETE n //"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> GraphSchema.nodeLabel(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("relationship types")
    class RelationshipTypes {

        @Test
        @DisplayName("accepts known relationship types")
        void acceptsKnownTypes() {
            assertThat(GraphSchema.relationshipType("HANDLES_ROUTE")).isEqualTo("HANDLES_ROUTE");
            assertThat(GraphSchema.relationshipType("CALLS")).isEqualTo("CALLS");
        }

        @Test
        @DisplayName("rejects unknown relationship types")
        void rejectsUnknownTypes() {
            assertThatThrownBy(() -> GraphSchema.relationshipType("FOO"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("property keys")
    class PropertyKeys {

        @Test
        @DisplayName("accepts safe identifiers")
        void acceptsSafeKeys() {
            assertThat(GraphSchema.propertyKey("httpMethod")).isEqualTo("httpMethod");
            assertThat(GraphSchema.propertyKey("line_number")).isEqualTo("line_number");
        }

        @Test
        @DisplayName("rejects keys with Cypher-breaking characters")
        void rejectsUnsafeKeys() {
            assertThatThrownBy(() -> GraphSchema.propertyKey("a = 1, n.b"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> GraphSchema.propertyKey("1bad"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
