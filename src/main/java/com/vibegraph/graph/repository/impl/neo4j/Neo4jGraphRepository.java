package com.vibegraph.graph.repository.impl.neo4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;
import org.springframework.stereotype.Repository;

import com.vibegraph.graph.dto.response.EdgeDto;
import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.graph.repository.GraphRepository;
import com.vibegraph.parser.node.EdgeData;
import com.vibegraph.parser.node.NodeData;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Repository
@RequiredArgsConstructor
@Slf4j
public class Neo4jGraphRepository implements GraphRepository {

    private final Driver neo4jDriver;

    @Override
    public void upsertProject(String projectId, String name, String path) {
        try (Session session = neo4jDriver.session()) {
            // fullName is the stable graph-wide identity used by getFullGraph/DTOs.
            // The Project node gets fullName = projectId so it participates in the
            // same stable-id scheme as every other node.
            session.run(
                    "MERGE (p:Project {id: $projectId}) " +
                    "SET p.name = $name, p.path = $path, p.projectId = $projectId, p.fullName = $projectId",
                    Map.of("projectId", projectId, "name", name, "path", path)
            );
        }
    }

    @Override
    public void upsertNodes(String projectId, List<NodeData> nodes) {
        if (nodes == null || nodes.isEmpty()) return;

        // Neo4j cannot parameterize labels, so we group by label and run one
        // UNWIND batch per label instead of one round-trip per node. Dynamic
        // properties are bulk-applied with `SET n += item.props`, keeping the
        // per-key schema validation as the safety boundary.
        Map<String, List<Map<String, Object>>> byLabel = new LinkedHashMap<>();
        for (NodeData node : nodes) {
            String label = GraphSchema.nodeLabel(node.type());

            Map<String, Object> props = new HashMap<>();
            if (node.properties() != null) {
                for (Map.Entry<String, Object> entry : node.properties().entrySet()) {
                    props.put(GraphSchema.propertyKey(entry.getKey()), entry.getValue());
                }
            }

            Map<String, Object> item = new HashMap<>();
            item.put("fullName", node.fullName());
            item.put("name", node.name());
            item.put("filePath", node.filePath());
            item.put("lineNumber", node.lineNumber());
            item.put("endLine", node.endLine());
            item.put("props", props);

            byLabel.computeIfAbsent(label, k -> new ArrayList<>()).add(item);
        }

        try (Session session = neo4jDriver.session()) {
            for (Map.Entry<String, List<Map<String, Object>>> group : byLabel.entrySet()) {
                String cypher = String.format(
                        "UNWIND $batch AS item " +
                        "MERGE (n:%s {projectId: $projectId, fullName: item.fullName}) " +
                        "SET n.name = item.name, n.filePath = item.filePath, " +
                        "n.lineNumber = item.lineNumber, n.endLine = item.endLine " +
                        "SET n += item.props",
                        group.getKey()
                );
                session.run(cypher, Map.of("projectId", projectId, "batch", group.getValue()));
            }
        }
    }

    @Override
    public int upsertEdges(String projectId, List<EdgeData> edges) {
        if (edges == null || edges.isEmpty()) return 0;

        // Group by relationship type — Neo4j cannot parameterize rel types, so
        // we run one UNWIND batch per type. Endpoints get an External stub on
        // creation if they don't exist yet (library/JDK refs, unresolved targets),
        // so edges are never silently dropped.
        Map<String, List<Map<String, Object>>> byRelType = new LinkedHashMap<>();
        for (EdgeData edge : edges) {
            String relType = GraphSchema.relationshipType(edge.type());

            Map<String, Object> props = new HashMap<>();
            if (edge.properties() != null) {
                for (Map.Entry<String, Object> entry : edge.properties().entrySet()) {
                    props.put(GraphSchema.propertyKey(entry.getKey()), entry.getValue());
                }
            }

            Map<String, Object> item = new HashMap<>();
            item.put("sourceFullName", edge.sourceFullName());
            item.put("targetFullName", edge.targetFullName());
            item.put("props", props);

            byRelType.computeIfAbsent(relType, k -> new ArrayList<>()).add(item);
        }

        int persisted = 0;
        try (Session session = neo4jDriver.session()) {
            for (Map.Entry<String, List<Map<String, Object>>> group : byRelType.entrySet()) {
                String cypher = String.format(
                        "UNWIND $batch AS item " +
                        "MERGE (a {projectId: $projectId, fullName: item.sourceFullName}) " +
                        "ON CREATE SET a:%s, a.name = item.sourceFullName " +
                        "MERGE (b {projectId: $projectId, fullName: item.targetFullName}) " +
                        "ON CREATE SET b:%s, b.name = item.targetFullName " +
                        "MERGE (a)-[r:%s]->(b) " +
                        "SET r += item.props",
                        GraphSchema.EXTERNAL_LABEL, GraphSchema.EXTERNAL_LABEL, group.getKey()
                );
                session.run(cypher, Map.of("projectId", projectId, "batch", group.getValue()));
                persisted += group.getValue().size();
            }
        }
        return persisted;
    }

    @Override
    public void deleteFile(String projectId, String filePath) {
        try (Session session = neo4jDriver.session()) {
            session.run(
                    "MATCH (n {projectId: $projectId, filePath: $filePath}) DETACH DELETE n",
                    Map.of("projectId", projectId, "filePath", filePath)
            );
        }
    }

    @Override
    public GraphDataResponse getFullGraph(String projectId) {
        try (Session session = neo4jDriver.session()) {
            var result = session.run(
                    "MATCH (n {projectId: $projectId}) " +
                    "OPTIONAL MATCH (n)-[r]->(m {projectId: $projectId}) " +
                    "RETURN n, r, m",
                    Map.of("projectId", projectId)
            );

            Map<String, NodeDto> nodeMap = new LinkedHashMap<>();
            List<EdgeDto> edges = new ArrayList<>();
            Map<String, Integer> nodeStats = new HashMap<>();
            Map<String, Integer> edgeStats = new HashMap<>();

            while (result.hasNext()) {
                Record record = result.next();

                Node n = record.get("n").asNode();
                addNodeToMap(nodeMap, n, nodeStats);

                Value rVal = record.get("r");
                Value mVal = record.get("m");

                if (!rVal.isNull() && !mVal.isNull()) {
                    Node m = mVal.asNode();
                    addNodeToMap(nodeMap, m, nodeStats);

                    Relationship r = rVal.asRelationship();
                    String edgeType = r.type();
                    // Stable, deterministic identity instead of Neo4j's internal id()
                    // (which is reused after deletes and changes across re-analysis).
                    String sourceId = stableNodeId(n);
                    String targetId = stableNodeId(m);
                    String edgeId = sourceId + "|" + edgeType + "|" + targetId;

                    EdgeDto edgeDto = EdgeDto.builder()
                            .id(edgeId)
                            .source(sourceId)
                            .target(targetId)
                            .type(edgeType)
                            .confidence(r.get("confidence").isNull() ? null : r.get("confidence").asDouble())
                            .lineNumber(r.get("lineNumber").isNull() ? null : r.get("lineNumber").asInt())
                            .build();
                    edges.add(edgeDto);
                    edgeStats.merge(edgeType, 1, Integer::sum);
                }
            }

            return GraphDataResponse.builder()
                    .nodes(new ArrayList<>(nodeMap.values()))
                    .edges(edges)
                    .nodeStats(nodeStats)
                    .edgeStats(edgeStats)
                    .build();
        }
    }

    @Override
    public GraphDataResponse getNeighborhood(String projectId, String nodeId, int hops) {
        throw new UnsupportedOperationException("Not implemented yet — Sprint 2");
    }

    @Override
    public List<NodeDto> searchNodes(String projectId, String query) {
        try (Session session = neo4jDriver.session()) {
            var result = session.run(
                    "CALL db.index.fulltext.queryNodes('node_search', $query) YIELD node, score " +
                    "WHERE node.projectId = $projectId " +
                    "RETURN node ORDER BY score DESC LIMIT 50",
                    Map.of("projectId", projectId, "query", query)
            );

            List<NodeDto> nodes = new ArrayList<>();
            while (result.hasNext()) {
                Record record = result.next();
                Node node = record.get("node").asNode();
                nodes.add(mapNodeToDto(node));
            }
            return nodes;
        }
    }

    @Override
    public List<NodeDto> getImpact(String projectId, String targetFullName, int maxDepth) {
        throw new UnsupportedOperationException("Not implemented yet — Sprint 2");
    }

    private void addNodeToMap(Map<String, NodeDto> nodeMap, Node node, Map<String, Integer> nodeStats) {
        String id = stableNodeId(node);
        if (nodeMap.containsKey(id)) return;

        NodeDto dto = mapNodeToDto(node);
        nodeMap.put(id, dto);
        nodeStats.merge(dto.getType(), 1, Integer::sum);
    }

    /**
     * Stable, deterministic node identity for the BE/FE contract.
     * Uses fullName (graph-wide unique within a project) rather than Neo4j's
     * internal id(), which is reused after deletes and is not stable across
     * re-analysis. Falls back to the internal id only if fullName is absent.
     */
    private String stableNodeId(Node node) {
        String fullName = node.get("fullName").asString("");
        return fullName.isEmpty() ? String.valueOf(node.id()) : fullName;
    }

    private NodeDto mapNodeToDto(Node node) {
        String type = node.labels().iterator().next();
        Map<String, Object> properties = new HashMap<>(node.asMap());
        properties.remove("projectId");
        properties.remove("fullName");
        properties.remove("name");
        properties.remove("filePath");
        properties.remove("lineNumber");

        return NodeDto.builder()
                .id(stableNodeId(node))
                .type(type)
                .name(node.get("name").asString(""))
                .fullName(node.get("fullName").asString(""))
                .filePath(node.get("filePath").asString(""))
                .lineNumber(node.get("lineNumber").isNull() ? null : node.get("lineNumber").asInt())
                .properties(properties)
                .build();
    }
}
