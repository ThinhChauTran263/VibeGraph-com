package com.vibegraph.graph.repository.impl.neo4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.neo4j.driver.exceptions.ClientException;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;
import org.springframework.stereotype.Repository;

import com.vibegraph.common.exception.NodeNotFoundException;
import com.vibegraph.common.util.HashUtils;
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

    /**
     * Minimal run-shape shared by {@link Session} and transaction contexts — lets the upsert
     * helpers serve both the autocommit single-file path and the transactional analysis path
     * (B-M11) without depending on the driver's exact type hierarchy.
     */
    @FunctionalInterface
    private interface CypherRunner {
        Result run(String query, Map<String, Object> params);
    }

    /**
     * Shared label added to every node by {@code V2__symbol_label.cypher} so the hot queries can
     * use an index. It is infrastructure, never a node's domain type.
     */
    private static final String SYMBOL_LABEL = "Symbol";

    private final Driver neo4jDriver;

    @Override
    public void upsertProject(String projectId, String name, String path) {
        try (Session session = neo4jDriver.session()) {
            runProjectUpsert(session::run, projectId, name, path);
        }
    }

    @Override
    public ProjectMetadata findProject(String projectId) {
        try (Session session = neo4jDriver.session()) {
            var result = session.run(
                    projectMetadataCypher("MATCH (p:Project {id: $projectId})"),
                    Map.of("projectId", projectId));
            if (!result.hasNext()) {
                return null;
            }
            return mapProjectMetadata(result.next());
        }
    }

    @Override
    public List<ProjectMetadata> findAllProjects() {
        try (Session session = neo4jDriver.session()) {
            var result = session.run(
                    projectMetadataCypher("MATCH (p:Project)") + " ORDER BY name, id");
            List<ProjectMetadata> projects = new ArrayList<>();
            while (result.hasNext()) {
                ProjectMetadata metadata = mapProjectMetadata(result.next());
                if (metadata.id() == null) {
                    continue;
                }
                projects.add(metadata);
            }
            return projects;
        }
    }

    private String projectMetadataCypher(String matchProject) {
        return matchProject + " " +
                "OPTIONAL MATCH (n:Symbol {projectId: p.id}) " +
                "WHERE n IS NULL OR NOT n:Project " +
                "WITH p, count(n) AS totalNodes, " +
                "count(DISTINCT CASE WHEN n.filePath IS NULL OR n.filePath = '' THEN null ELSE n.filePath END) AS totalFiles " +
                "OPTIONAL MATCH (a:Symbol {projectId: p.id})-[r]->(b:Symbol {projectId: p.id}) " +
                "RETURN p.id AS id, p.name AS name, p.path AS path, " +
                "p.createdAt AS createdAt, p.lastAnalyzedAt AS lastAnalyzedAt, " +
                "totalFiles, totalNodes, count(r) AS totalEdges";
    }

    private ProjectMetadata mapProjectMetadata(Record record) {
        return new ProjectMetadata(
                record.get("id").isNull() ? null : record.get("id").asString(),
                record.get("name").isNull() ? null : record.get("name").asString(),
                record.get("path").isNull() ? null : record.get("path").asString(),
                instantOrNull(record.get("createdAt"), "createdAt"),
                instantOrNull(record.get("lastAnalyzedAt"), "lastAnalyzedAt"),
                record.get("totalFiles").isNull() ? 0 : record.get("totalFiles").asInt(),
                record.get("totalNodes").isNull() ? 0 : record.get("totalNodes").asInt(),
                record.get("totalEdges").isNull() ? 0 : record.get("totalEdges").asInt());
    }

    /**
     * Parse a temporal value tolerantly (ZonedDateTime first, ISO string fallback). Unparseable
     * values still degrade to {@code null} — but now loudly (B-M1), so corrupt Neo4j data is
     * diagnosable instead of silently swallowed.
     */
    private Instant instantOrNull(Value value, String field) {
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            return value.asZonedDateTime().toInstant();
        } catch (RuntimeException primaryFailure) {
            try {
                return Instant.parse(value.asString());
            } catch (RuntimeException fallbackFailure) {
                log.warn("Failed to parse instant for project metadata field '{}' (value: {}): {}",
                        field, value, fallbackFailure.getMessage());
                return null;
            }
        }
    }

    @Override
    public void upsertNodes(String projectId, List<NodeData> nodes) {
        if (nodes == null || nodes.isEmpty()) return;

        try (Session session = neo4jDriver.session()) {
            for (Map.Entry<String, List<Map<String, Object>>> group : groupNodesByLabel(nodes).entrySet()) {
                runNodeGroupUpsert(session::run, projectId, group);
            }
        }
    }

    @Override
    public int upsertEdges(String projectId, List<EdgeData> edges) {
        if (edges == null || edges.isEmpty()) return 0;

        int persisted = 0;
        try (Session session = neo4jDriver.session()) {
            for (Map.Entry<String, List<Map<String, Object>>> group : groupEdgesByType(edges).entrySet()) {
                persisted += runEdgeGroupUpsert(session::run, projectId, group);
            }
        }
        return persisted;
    }

    /**
     * B-M11: the full graph write of one analysis in ONE write transaction. Any step that
     * fails rolls the whole upsert back, so Neo4j can never hold a half-written project
     * graph (project without its nodes, or nodes without their edges).
     */
    @Override
    public int upsertAnalysis(String projectId, String name, String path,
            List<NodeData> nodes, List<EdgeData> edges) {
        Map<String, List<Map<String, Object>>> nodesByLabel =
                nodes == null ? Map.of() : groupNodesByLabel(nodes);
        Map<String, List<Map<String, Object>>> edgesByType =
                edges == null ? Map.of() : groupEdgesByType(edges);
        try (Session session = neo4jDriver.session()) {
            return session.executeWrite(tx -> {
                runProjectUpsert(tx::run, projectId, name, path);
                for (Map.Entry<String, List<Map<String, Object>>> group : nodesByLabel.entrySet()) {
                    runNodeGroupUpsert(tx::run, projectId, group);
                }
                int persisted = 0;
                for (Map.Entry<String, List<Map<String, Object>>> group : edgesByType.entrySet()) {
                    persisted += runEdgeGroupUpsert(tx::run, projectId, group);
                }
                return persisted;
            });
        }
    }

    private void runProjectUpsert(CypherRunner runner, String projectId, String name, String path) {
        // fullName is the stable graph-wide identity used by getFullGraph/DTOs.
        // The Project node gets fullName = projectId so it participates in the
        // same stable-id scheme as every other node.
        runner.run(
                "MERGE (p:Project {id: $projectId}) " +
                "SET p:Symbol, p.name = $name, p.path = $path, p.projectId = $projectId, p.fullName = $projectId, " +
                "p.createdAt = coalesce(p.createdAt, datetime()), p.lastAnalyzedAt = datetime()",
                Map.of("projectId", projectId, "name", name, "path", path)
        );
    }

    /**
     * Group node payloads by label — Neo4j cannot parameterize labels, so one UNWIND batch
     * runs per label; the validated label is interpolated into {@code SET n:%s}.
     *
     * <p>Identity is {projectId, fullName} — label-agnostic — so upserting a real parsed node
     * can enrich any pre-existing placeholder node with the same fullName instead of creating
     * a duplicate. Dynamic properties are bulk-applied with {@code SET n += item.props}.
     */
    private Map<String, List<Map<String, Object>>> groupNodesByLabel(List<NodeData> nodes) {
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
        return byLabel;
    }

    private void runNodeGroupUpsert(CypherRunner runner, String projectId,
            Map.Entry<String, List<Map<String, Object>>> group) {
        // MERGE is :Symbol-scoped so it hits the (projectId, fullName) composite
        // index instead of an all-nodes scan; V2's backfill guarantees every
        // pre-existing node (including :External placeholders) carries :Symbol.
        String cypher = String.format(
                "UNWIND $batch AS item " +
                "MERGE (n:Symbol {projectId: $projectId, fullName: item.fullName}) " +
                "SET n:%s " +
                "%s" +
                "SET n.name = item.name, n.filePath = item.filePath, " +
                "n.lineNumber = item.lineNumber, n.endLine = item.endLine " +
                "SET n += item.props",
                group.getKey(),
                GraphSchema.EXTERNAL_LABEL.equals(group.getKey()) ? "" : "REMOVE n:External "
        );
        runner.run(cypher, Map.of("projectId", projectId, "batch", group.getValue()));
    }

    /**
     * Group edge payloads by relationship type — Neo4j cannot parameterize rel types, so one
     * UNWIND batch runs per type. Missing endpoints are skipped in baseline mode instead of
     * creating placeholder nodes.
     */
    private Map<String, List<Map<String, Object>>> groupEdgesByType(List<EdgeData> edges) {
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
        return byRelType;
    }

    private int runEdgeGroupUpsert(CypherRunner runner, String projectId,
            Map.Entry<String, List<Map<String, Object>>> group) {
        String cypher = String.format(
                "UNWIND $batch AS item " +
                "MATCH (a:Symbol {projectId: $projectId, fullName: item.sourceFullName}) " +
                "MATCH (b:Symbol {projectId: $projectId, fullName: item.targetFullName}) " +
                "MERGE (a)-[r:%s]->(b) " +
                "SET r += item.props " +
                "RETURN count(r) AS persisted",
                group.getKey()
        );
        return runner.run(cypher, Map.of("projectId", projectId, "batch", group.getValue()))
                .single()
                .get("persisted")
                .asInt();
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
    public GraphDataResponse getFileSlice(String projectId, String filePath) {
        try (Session session = neo4jDriver.session()) {
            // One file's slice: its own nodes plus edges touching them in either direction.
            // Inbound edges from other files are part of the slice so the per-file diff (B-M5)
            // still reports their removal — at a fraction of a full-graph round-trip.
            var result = session.run(
                    "MATCH (f:Symbol {projectId: $projectId, filePath: $filePath}) " +
                    "OPTIONAL MATCH (a:Symbol {projectId: $projectId})-[r]->(b:Symbol {projectId: $projectId}) " +
                    "WHERE a = f OR b = f " +
                    "RETURN f, a, r, b",
                    Map.of("projectId", projectId, "filePath", filePath)
            );

            Map<String, NodeDto> nodeMap = new LinkedHashMap<>();
            List<EdgeDto> edges = new ArrayList<>();
            Map<String, Integer> statsSink = new HashMap<>(); // addNodeToMap needs a stats target

            while (result.hasNext()) {
                Record record = result.next();
                addNodeToMap(nodeMap, record.get("f").asNode(), statsSink);

                Value rVal = record.get("r");
                Value aVal = record.get("a");
                Value bVal = record.get("b");
                if (!rVal.isNull() && !aVal.isNull() && !bVal.isNull()) {
                    Node a = aVal.asNode();
                    Node b = bVal.asNode();
                    addNodeToMap(nodeMap, a, statsSink);
                    addNodeToMap(nodeMap, b, statsSink);

                    Relationship r = rVal.asRelationship();
                    String edgeType = r.type();
                    String sourceId = stableNodeId(a);
                    String targetId = stableNodeId(b);
                    edges.add(EdgeDto.builder()
                            .id(stableEdgeId(sourceId, edgeType, targetId))
                            .source(sourceId)
                            .target(targetId)
                            .type(edgeType)
                            .confidence(r.get("confidence").isNull() ? null : r.get("confidence").asDouble())
                            .lineNumber(r.get("lineNumber").isNull() ? null : r.get("lineNumber").asInt())
                            .weight(r.get("weight").isNull() ? 1 : r.get("weight").asInt())
                            .occurrences(readOccurrences(r))
                            .properties(edgeProperties(r))
                            .build());
                }
            }

            return GraphDataResponse.builder()
                    .nodes(new ArrayList<>(nodeMap.values()))
                    .edges(edges)
                    .build();
        }
    }

    @Override
    public GraphDataResponse getFullGraph(String projectId) {
        try (Session session = neo4jDriver.session()) {
            // :Symbol-scoped so the (projectId) index applies — the label-less form
            // degraded to an all-nodes scan across every tenant.
            var result = session.run(
                    "MATCH (n:Symbol {projectId: $projectId}) " +
                    "OPTIONAL MATCH (n)-[r]->(m:Symbol {projectId: $projectId}) " +
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
                    String sourceId = stableNodeId(n);
                    String targetId = stableNodeId(m);
                    String edgeId = stableEdgeId(sourceId, edgeType, targetId);

                    EdgeDto edgeDto = EdgeDto.builder()
                            .id(edgeId)
                            .source(sourceId)
                            .target(targetId)
                            .type(edgeType)
                            .confidence(r.get("confidence").isNull() ? null : r.get("confidence").asDouble())
                            .lineNumber(r.get("lineNumber").isNull() ? null : r.get("lineNumber").asInt())
                            .weight(r.get("weight").isNull() ? 1 : r.get("weight").asInt())
                            .occurrences(readOccurrences(r))
                            .properties(edgeProperties(r))
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
    public NodeDetailResponse getNodeDetail(String projectId, String nodeId, int hops) {
        try (Session session = neo4jDriver.session()) {
            var nodeResult = session.run(
                    "MATCH (n:Symbol {projectId: $projectId, fullName: $nodeId}) RETURN n",
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
                    Map.of("projectId", projectId, "query", escapeLucene(query))
            );

            List<NodeDto> nodes = new ArrayList<>();
            while (result.hasNext()) {
                Record record = result.next();
                Node node = record.get("node").asNode();
                nodes.add(mapNodeToDto(node));
            }
            return nodes;
        } catch (ClientException ex) {
            // S-M4: a malformed Lucene expression surfaces as QueryParseException (a
            // ClientException). Escape above makes this near-unreachable; keep the catch as
            // defense-in-depth and map to 400 instead of an opaque 500.
            log.warn("Fulltext node search rejected for project {}: {}", projectId, ex.getMessage());
            throw new IllegalArgumentException("Invalid search query");
        }
    }

    /**
     * S-M4: Lucene query syntax characters in raw user input would otherwise be parsed as
     * operators; escaping turns every character literal. Wildcard search is intentionally
     * not exposed through this endpoint.
     */
    static String escapeLucene(String raw) {
        StringBuilder escaped = new StringBuilder(raw.length() + 8);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if ("+-!(){}[]^\"~*?:/\\&|".indexOf(c) >= 0) {
                escaped.append('\\');
            }
            escaped.append(c);
        }
        return escaped.toString();
    }

    @Override
    public ImpactAnalysisResponse getImpact(String projectId, String targetFullName, int maxDepth, ImpactProfile profile) {
        int boundedDepth = validateImpactDepth(maxDepth);
        ImpactProfile boundedProfile = profile == null ? ImpactProfile.DEPENDENCY : profile;
        try (Session session = neo4jDriver.session()) {
            var targetResult = session.run(
                    "MATCH (target:Symbol {projectId: $projectId, fullName: $targetFullName}) RETURN target",
                    Map.of("projectId", projectId, "targetFullName", targetFullName)
            );
            if (!targetResult.hasNext()) {
                throw new NodeNotFoundException("Node not found");
            }

            NodeDto target = mapNodeToDto(targetResult.single().get("target").asNode());
            ImpactByDepth impactByDepth = getImpactByDepth(session, projectId, targetFullName, boundedDepth, boundedProfile);
            Map<Integer, List<NodeDto>> byDepth = impactByDepth.nodes();
            Map<Integer, Integer> countsByDepth = impactByDepth.counts();
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

    /** Nodes (bounded per depth) and total counts per depth from a single traversal. */
    private record ImpactByDepth(Map<Integer, List<NodeDto>> nodes, Map<Integer, Integer> counts) {
    }

    /**
     * Runs the variable-length impact traversal ONCE and projects both the per-depth
     * dependent sample and the per-depth total count from the same expansion — previously
     * two identical traversals ran back to back (nodes + counts).
     */
    private ImpactByDepth getImpactByDepth(
            Session session,
            String projectId,
            String targetFullName,
            int maxDepth,
            ImpactProfile profile) {
        var result = session.run(impactTraversalCypher(maxDepth, profile,
                        "WITH dependent, min(length(path)) AS depth " +
                        "ORDER BY depth, dependent.fullName " +
                        "WITH depth, count(dependent) AS dependentCount, collect(dependent)[..$limit] AS dependents " +
                        "RETURN depth, dependentCount, dependents ORDER BY depth"),
                Map.of(
                        "projectId", projectId,
                        "targetFullName", targetFullName,
                        "limit", MAX_IMPACT_NODES_PER_DEPTH));

        Map<Integer, List<NodeDto>> byDepth = new LinkedHashMap<>();
        Map<Integer, Integer> countsByDepth = new LinkedHashMap<>();
        while (result.hasNext()) {
            Record record = result.next();
            int depth = record.get("depth").asInt();
            byDepth.put(depth, record.get("dependents").asList(value -> mapNodeToDto(value.asNode())));
            countsByDepth.put(depth, record.get("dependentCount").asInt());
        }
        return new ImpactByDepth(byDepth, countsByDepth);
    }

    private String impactTraversalCypher(int maxDepth, ImpactProfile profile, String projection) {
        String relationship = String.format("[:%s*1..%d]", profile.relationshipPattern(), maxDepth);
        String pathPattern = profile.directedToTarget()
                ? "(dependent:Symbol)-" + relationship + "->(target:Symbol {projectId: $projectId, fullName: $targetFullName}) "
                : "(dependent:Symbol)-" + relationship + "-(target:Symbol {projectId: $projectId, fullName: $targetFullName}) ";
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
                ? String.format("MATCH path = (other)-[rels*1..%d]->(n:Symbol {projectId: $projectId, fullName: $nodeId}) ", boundedHops)
                : String.format("MATCH path = (n:Symbol {projectId: $projectId, fullName: $nodeId})-[rels*1..%d]->(other) ", boundedHops);
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
        Map<String, Object> properties = new HashMap<>(node.asMap());
        properties.remove("projectId");
        properties.remove("fullName");
        properties.remove("name");
        properties.remove("filePath");
        properties.remove("lineNumber");

        return NodeDto.builder()
                .id(stableNodeId(node))
                .type(domainType(node))
                .name(node.get("name").asString(""))
                .fullName(node.get("fullName").asString(""))
                .filePath(node.get("filePath").asString(""))
                .lineNumber(node.get("lineNumber").isNull() ? null : node.get("lineNumber").asInt())
                .properties(properties)
                .build();
    }

    /**
     * Resolves a node's domain type from its labels.
     *
     * <p>Since V2 every node also carries the shared {@code :Symbol} label, and Neo4j gives no
     * ordering guarantee for {@code labels()}. Taking the first label would therefore return
     * "Symbol" for an arbitrary subset of nodes, which breaks type-based filtering and the
     * per-type statistics. The shared label is skipped, and only used as the type when a node has
     * nothing else.
     */
    private String domainType(Node node) {
        String fallback = "Unknown";
        for (String label : node.labels()) {
            if (!SYMBOL_LABEL.equals(label)) {
                return label;
            }
            fallback = SYMBOL_LABEL;
        }
        return fallback;
    }

    private String stableEdgeId(String sourceId, String edgeType, String targetId) {
        return HashUtils.sha256(sourceId + "|" + edgeType + "|" + targetId);
    }

    private List<Integer> readOccurrences(Relationship relationship) {
        if (relationship.get("occurrences").isNull()) {
            if (relationship.get("lineNumber").isNull()) {
                return List.of();
            }
            return List.of(relationship.get("lineNumber").asInt());
        }
        return relationship.get("occurrences").asList(value -> value.asInt());
    }

    private Map<String, Object> edgeProperties(Relationship relationship) {
        Map<String, Object> properties = new HashMap<>(relationship.asMap());
        properties.remove("confidence");
        properties.remove("lineNumber");
        properties.remove("weight");
        properties.remove("occurrences");
        return properties;
    }
}
