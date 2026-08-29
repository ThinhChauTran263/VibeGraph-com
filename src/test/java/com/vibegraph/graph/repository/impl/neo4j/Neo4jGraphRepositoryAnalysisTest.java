package com.vibegraph.graph.repository.impl.neo4j;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.TransactionCallback;
import org.neo4j.driver.TransactionContext;

import com.vibegraph.parser.node.NodeData;

class Neo4jGraphRepositoryAnalysisTest {

    @Test
    @DisplayName("full analysis clears stale project symbols before writing the replacement graph")
    void upsertAnalysisClearsStaleSymbolsBeforeWritingReplacement() {
        Driver driver = mock(Driver.class);
        Session session = mock(Session.class);
        TransactionContext transaction = mock(TransactionContext.class);
        Result result = mock(Result.class);
        when(driver.session()).thenReturn(session);
        when(transaction.run(anyString(), anyMap())).thenReturn(result);
        when(session.executeWrite(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<Integer> callback = invocation.getArgument(0);
            return callback.execute(transaction);
        });
        Neo4jGraphRepository repository = new Neo4jGraphRepository(driver);
        NodeData remaining = NodeData.of(
                "Class", "UserService", "com.example.UserService",
                "src/UserService.java", 1, 30, Map.of());

        repository.upsertAnalysis(
                "p1", "Demo", "/tmp/demo", List.of(remaining), List.of());

        InOrder ordered = inOrder(transaction);
        ordered.verify(transaction).run(
                "MATCH (n {projectId: $projectId}) WHERE NOT n:Project DETACH DELETE n",
                Map.of("projectId", "p1"));
        ordered.verify(transaction).run(
                "MERGE (p:Project {id: $projectId}) "
                        + "SET p:Symbol, p.name = $name, p.path = $path, p.projectId = $projectId, p.fullName = $projectId, "
                        + "p.createdAt = coalesce(p.createdAt, datetime()), p.lastAnalyzedAt = datetime()",
                Map.of("projectId", "p1", "name", "Demo", "path", "/tmp/demo"));
        ordered.verify(transaction).run(anyString(), anyMap());
    }
}
