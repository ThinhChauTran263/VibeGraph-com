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

import com.vibegraph.common.exception.NodeNotFoundException;
import com.vibegraph.graph.dto.response.EdgeDto;
import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.ImpactAnalysisResponse;
import com.vibegraph.graph.dto.response.NodeDetailResponse;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.graph.model.ImpactProfile;
import com.vibegraph.graph.repository.GraphRepository;
import com.vibegraph.graph.repository.ProjectMetadata;
import com.vibegraph.parser.node.EdgeData;
import com.vibegraph.parser.node.NodeData;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Repository
@RequiredArgsConstructor
@Slf4j
public class Neo4jGraphRepository implements GraphRepository {

    private static final int MAX_DETAIL_CONNECTIONS = 50;
    private static final int MAX_IMPACT_NODES_PER_DEPTH = 50;

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
    public ProjectMetadata findProject(String projectId) {
        try (Session session = neo4jDriver.session()) {
            var result = session.run(
                    "MATCH (p:Project {id: $projectId}) RETURN p.id AS id, p.name AS name, p.path AS path",
                    Map.of("projectId", projectId));
            if (!result.hasNext()) {
                return null;
            }
            var record = result.next();
            return new ProjectMetadata(
                    record.get("id").isNull() ? null : record.get("id").asString(),
                    record.get("name").isNull() ? null : record.get("name").asString(),
                    record.get("path").isNull() ? null : record.get("path").asString());
        }
    }

    @Override
    public List<ProjectMetadata> findAllProjects() {
        try (Session session = neo4jDriver.session()) {
            var result = session.run(
                    "MATCH (p:Project) RETURN p.id AS id, p.name AS name, p.path AS path");
            List<ProjectMetadata> projects = new ArrayList<>();
            while (result.hasNext()) {
                var record = result.next();
                if (record.get("id").isNull()) {
                    continue;
                }
                projects.add(new ProjectMetadata(
                        record.get("id").asString(),
                        record.get("name").isNull() ? null : record.get("name").asString(),
                        record.get("path").isNull() ? null : record.get("path").asString()));
            }
            return projects;
        }
    }

    @Override
    public void upsertNodes(String projectId, List<NodeData> nodes) {
        if (nodes == null || nodes.isEmpty()) return;

        // Identity is {projectId, fullName} — label-agnostic — so upserting a node
        // ENRICHES any pre-existing `External` stub with the same fullName (created
        // on demand by upsertEdges for unparsed targets) instead of creating a
        // duplicate: MERGE without a label, then SET the real label and REMOVE
        // :External. Neo4j cannot parameterize labels, so we group by label and run
        // one UNWIND batch per label; the validated label is interpolated into SET n:%s.
        // Dynamic properties are bulk-applied with `SET n += item.props`.
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
                        "MERGE (n {projectId: $projectId, fullName: item.fullName}) " +
                        "SET n:%s " +
                        "REMOVE n:External " +
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
    public void deleteProject(String projectId) {
        try (Session session = neo4jDriver.session()) {
            session.run(
                    "MATCH (n {projectId: $projectId}) DETACH DELETE n",
                    Map.of("projectId", projectId)
            );
        }
    }

    @Override
    public void deleteFile(String projectId, String filePath) {
        try (Session session = neo4jDriver.session()) {
            session.run(
                    "MATCH (n {projectId: $projectId, filePath: $filePath}) DETACH DELETE n",
                    Map.of("projectId", projectId, "filePath", filePath)
            );
            session.run(
                    "MATCH (n:External {projectId: $projectId}) " +
                    "WHERE NOT (n)--() " +
                    "DETACH DELETE n",
                    Map.of("projectId", projectId)
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
    public NodeDetailResponse getNodeDetail(String projectId, String nodeId, int hops) {
        try (Session session = neo4jDriver.session()) {
            var nodeResult = session.run(
                    "MATCH (n {projectId: $projectId, fullName: $nodeId}) RETURN n",
                    Map.of("projectId", projectId, "nodeId", nodeId)
            );
            if (!nodeResult.hasNext()) {
                throw new NodeNotFoundException("Node not found");
            }

            Node node = nodeResult.single().get("n").asNode();
            return NodeDetailResponse.builder()
                    .node(mapNodeToDto(node))
                    .incoming(getNodeConnections(session, projectId, nodeId, hops, "INCOMING"))
                    .outgoing(getNodeConnections(session, projectId, nodeId, hops, "OUTGOING"))
                    .build();
        }
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
    public ImpactAnalysisResponse getImpact(String projectId, String targetFullName, int maxDepth, ImpactProfile profile) {
        int boundedDepth = validateImpactDepth(maxDepth);
        ImpactProfile boundedProfile = profile == null ? ImpactProfile.DEPENDENCY : profile;
        try (Session session = neo4jDriver.session()) {
            var targetResult = session.run(
                    "MATCH (target {projectId: $projectId, fullName: $targetFullName}) RETURN target",
                    Map.of("projectId", projectId, "targetFullName", targetFullName)
            );
            if (!targetResult.hasNext()) {
                throw new NodeNotFoundException("Node not found");
            }

            NodeDto target = mapNodeToDto(targetResult.single().get("target").asNode());
            Map<Integer, List<NodeDto>> byDepth = getImpactNodesByDepth(session, projectId, targetFullName, boundedDepth, boundedProfile);
            Map<Integer, Integer> countsByDepth = getImpactCountsByDepth(session, projectId, targetFullName, boundedDepth, boundedProfile);
            List<NodeDto> willBreak = byDepth.getOrDefault(1, List.of());
            List<NodeDto> likelyAffected = byDepth.getOrDefault(2, List.of());
            List<NodeDto> mayNeedTesting = impactNodesAtOrAfter(byDepth, 3);
            int directDependents = countsByDepth.getOrDefault(1, 0);
            int totalDependents = countsByDepth.values().stream().mapToInt(Integer::intValue).sum();

            return ImpactAnalysisResponse.builder()
                    .target(target)
                    .riskLevel(riskLevel(directDependents))
                    .directDependents(directDependents)
                    .totalDependents(totalDependents)
                    .willBreak(willBreak)
                    .likelyAffected(likelyAffected)
                    .mayNeedTesting(mayNeedTesting)
                    .build();
        }
    }

    private int validateImpactDepth(int depth) {
        if (depth == 1 || depth == 2 || depth == 3 || depth == 5) {
            return depth;
        }
        throw new IllegalArgumentException("depth must be one of 1, 2, 3, 5");
    }

    private List<NodeDto> impactNodesAtOrAfter(Map<Integer, List<NodeDto>> byDepth, int minDepth) {
        return byDepth.entrySet().stream()
                .filter(entry -> entry.getKey() >= minDepth)
                .flatMap(entry -> entry.getValue().stream())
                .limit(MAX_IMPACT_NODES_PER_DEPTH)
                .toList();
    }

    private Map<Integer, List<NodeDto>> getImpactNodesByDepth(
            Session session,
            String projectId,
            String targetFullName,
            int maxDepth,
            ImpactProfile profile) {
        var result = session.run(impactTraversalCypher(maxDepth, profile,
                        "WITH dependent, min(length(path)) AS depth " +
                        "ORDER BY depth, dependent.fullName " +
                        "WITH depth, collect(dependent)[..$limit] AS dependents " +
                        "RETURN depth, dependents ORDER BY depth"),
                Map.of(
                        "projectId", projectId,
                        "targetFullName", targetFullName,
                        "limit", MAX_IMPACT_NODES_PER_DEPTH));

        Map<Integer, List<NodeDto>> byDepth = new LinkedHashMap<>();
        while (result.hasNext()) {
            Record record = result.next();
            int depth = record.get("depth").asInt();
            List<NodeDto> nodes = record.get("dependents").asList(value -> mapNodeToDto(value.asNode()));
            byDepth.put(depth, nodes);
        }
        return byDepth;
    }

    private Map<Integer, Integer> getImpactCountsByDepth(
            Session session,
            String projectId,
            String targetFullName,
            int maxDepth,
            ImpactProfile profile) {
        var result = session.run(impactTraversalCypher(maxDepth, profile,
                        "WITH dependent, min(length(path)) AS depth " +
                        "RETURN depth, count(dependent) AS dependentCount ORDER BY depth"),
                Map.of("projectId", projectId, "targetFullName", targetFullName));

        Map<Integer, Integer> countsByDepth = new LinkedHashMap<>();
        while (result.hasNext()) {
            Record record = result.next();
            countsByDepth.put(record.get("depth").asInt(), record.get("dependentCount").asInt());
        }
        return countsByDepth;
    }

    private String impactTraversalCypher(int maxDepth, ImpactProfile profile, String projection) {
        String relationship = String.format("[:%s*1..%d]", profile.relationshipPattern(), maxDepth);
        String pathPattern = profile.directedToTarget()
                ? "(dependent)-" + relationship + "->(target {projectId: $projectId, fullName: $targetFullName}) "
                : "(dependent)-" + relationship + "-(target {projectId: $projectId, fullName: $targetFullName}) ";
        return String.format(
                "MATCH path = %s" +
                "WHERE dependent.projectId = $projectId " +
                "AND dependent.fullName <> $targetFullName " +
                "AND all(node IN nodes(path) WHERE node.projectId = $projectId) " +
                "%s",
                pathPattern,
                projection);
    }

    private String riskLevel(int directDependents) {
        if (directDependents >= 50) {
            return "CRITICAL";
        }
        if (directDependents >= 15) {
            return "HIGH";
        }
        if (directDependents >= 5) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private int validateDetailHops(int hops) {
        if (hops == 1 || hops == 2 || hops == 3 || hops == 5) {
            return hops;
        }
        throw new IllegalArgumentException("hops must be one of 0, 1, 2, 3, 5");
    }

    private List<NodeDetailResponse.ConnectionDto> getNodeConnections(
            Session session,
            String projectId,
            String nodeId,
            int hops,
            String direction) {
        if (hops == 0) {
            return List.of();
        }

        int boundedHops = validateDetailHops(hops);
        String cypher = "INCOMING".equals(direction)
                ? String.format("MATCH path = (other)-[rels*1..%d]->(n {projectId: $projectId, fullName: $nodeId}) ", boundedHops)
                : String.format("MATCH path = (n {projectId: $projectId, fullName: $nodeId})-[rels*1..%d]->(other) ", boundedHops);
        var result = session.run(
                cypher +
                "WHERE other.projectId = $projectId " +
                "AND length(path) <= $hops " +
                "AND all(node IN nodes(path) WHERE node.projectId = $projectId) " +
                "WITH other, head(collect(rels[0])) AS rel " +
                "RETURN other, type(rel) AS relationshipType " +
                "ORDER BY other.fullName LIMIT $limit",
                Map.of(
                        "projectId", projectId,
                        "nodeId", nodeId,
                        "hops", hops,
                        "limit", MAX_DETAIL_CONNECTIONS)
        );

        List<NodeDetailResponse.ConnectionDto> connections = new ArrayList<>();
        while (result.hasNext()) {
            Record record = result.next();
            connections.add(NodeDetailResponse.ConnectionDto.builder()
                    .otherNode(mapNodeToDto(record.get("other").asNode()))
                    .relationshipType(record.get("relationshipType").asString())
                    .direction(direction)
                    .build());
        }
        return connections;
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
