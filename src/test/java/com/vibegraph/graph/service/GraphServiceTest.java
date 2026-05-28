package com.vibegraph.graph.service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for GraphService - graph query operations.
 *
 * Setup with @DataNeo4jTest hoặc @SpringBootTest khi GraphServiceImpl ready.
 *
 * Run: mvn test -Dtest=GraphServiceTest
 */
@DisplayName("GraphService")
class GraphServiceTest {

    @Nested
    @DisplayName("getGraph")
    class GetGraph {

        @Test
        @Disabled("Chờ GraphServiceImpl + @DataNeo4jTest setup")
        @DisplayName("should return all nodes and edges for project")
        void shouldReturnAllNodesAndEdges() {
            // Arrange: Setup test project with sample nodes
            // graphService.saveProject("test-project", samplePath);
            // graphService.saveNodes(testNodes);
            // graphService.saveEdges(testEdges);

            // Act
            // GraphDataResponse result = graphService.getGraph("test-project", null);

            // Assert
            // assertThat(result).isNotNull();
            // assertThat(result.getNodes()).isNotEmpty();
            // assertThat(result.getEdges()).isNotEmpty();
        }

        @Test
        @Disabled("Chờ GraphServiceImpl + @DataNeo4jTest setup")
        @DisplayName("should filter by node type")
        void shouldFilterByNodeType() {
            // GraphFilterRequest filter = new GraphFilterRequest();
            // filter.setNodeTypes(List.of("CLASS"));
            // GraphDataResponse result = graphService.getGraph("test-project", filter);
            // assertThat(result.getNodes()).allMatch(n -> n.getType().equals("CLASS"));
        }

        @Test
        @Disabled("Chờ GraphServiceImpl + @DataNeo4jTest setup")
        @DisplayName("should filter by package")
        void shouldFilterByPackage() {
            // GraphFilterRequest filter = new GraphFilterRequest();
            // filter.setPackages(List.of("com.example.service"));
            // GraphDataResponse result = graphService.getGraph("test-project", filter);
            // assertThat(result.getNodes()).allMatch(n -> n.getFullName().startsWith("com.example.service"));
        }
    }

    @Nested
    @DisplayName("getNeighbors")
    class GetNeighbors {

        @Test
        @Disabled("Chờ GraphServiceImpl + @DataNeo4jTest setup")
        @DisplayName("should return 1-hop neighbors")
        void shouldReturnOneHopNeighbors() {
            // GraphDataResponse result = graphService.getNeighbors("node-id", 1);
            // assertThat(result).isNotNull();
        }

        @Test
        @Disabled("Chờ GraphServiceImpl + @DataNeo4jTest setup")
        @DisplayName("should return N-hop neighbors")
        void shouldReturnNHopNeighbors() {
            // GraphDataResponse result = graphService.getNeighbors("node-id", 3);
            // assertThat(result).isNotNull();
        }

        @Test
        @Disabled("Chờ GraphServiceImpl + @DataNeo4jTest setup")
        @DisplayName("should throw NodeNotFoundException for invalid id")
        void shouldThrowForInvalidNodeId() {
            // assertThatThrownBy(() -> graphService.getNeighbors("non-existent", 1))
            //     .isInstanceOf(NodeNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getNodeDetail")
    class GetNodeDetail {

        @Test
        @Disabled("Chờ GraphServiceImpl + @DataNeo4jTest setup")
        @DisplayName("should return INCOMING and OUTGOING connections")
        void shouldReturnIncomingAndOutgoing() {
            // NodeDetailResponse result = graphService.getNodeDetail("node-id");
            // assertThat(result.getIncoming()).isNotNull();
            // assertThat(result.getOutgoing()).isNotNull();
        }
    }
}
