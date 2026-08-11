package com.vibegraph.graph.repository.impl.neo4j;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;

class Neo4jGraphRepositoryDeleteFileTest {

    @Test
    @DisplayName("deleteFile deletes file nodes then prunes legacy placeholder nodes")
    void deleteFilePrunesOrphanExternalStubs() {
        Driver driver = mock(Driver.class);
        Session session = mock(Session.class);
        Result result = mock(Result.class);
        when(driver.session()).thenReturn(session);
        when(session.run(anyString(), anyMap())).thenReturn(result);
        Neo4jGraphRepository repository = new Neo4jGraphRepository(driver);

        repository.deleteFile("p1", "src/Changed.java");

        InOrder ordered = inOrder(session);
        ordered.verify(session).run(
                "MATCH (n {projectId: $projectId, filePath: $filePath}) DETACH DELETE n",
                java.util.Map.of("projectId", "p1", "filePath", "src/Changed.java"));
        ordered.verify(session).run(
                "MATCH (n:External {projectId: $projectId}) " +
                "WHERE NOT (n)--() " +
                "DETACH DELETE n",
                java.util.Map.of("projectId", "p1"));
        ordered.verify(session).close();
        ordered.verifyNoMoreInteractions();
    }
}
