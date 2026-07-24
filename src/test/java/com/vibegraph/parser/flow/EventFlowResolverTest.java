package com.vibegraph.parser.flow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.vibegraph.parser.node.EdgeData;
import com.vibegraph.parser.node.NodeData;

@DisplayName("EventFlowResolver")
class EventFlowResolverTest {

    @Test
    @DisplayName("joins exact PUBLISHES_EVENT and LISTENS_EVENT facts into TRIGGERS")
    void joinsPublisherToListener() {
        List<NodeData> nodes = List.of(
                node("Method", "create", "com.example.UserService.create()"),
                node("Method", "onUserCreated", "com.example.UserListener.onUserCreated(UserCreatedEvent)"),
                node("Class", "UserCreatedEvent", "com.example.UserCreatedEvent"));
        List<EdgeData> edges = List.of(
                EdgeData.of("PUBLISHES_EVENT", "com.example.UserService.create()", "com.example.UserCreatedEvent"),
                EdgeData.of("LISTENS_EVENT", "com.example.UserListener.onUserCreated(UserCreatedEvent)",
                        "com.example.UserCreatedEvent"));

        List<EdgeData> inferred = EventFlowResolver.inferTriggers(nodes, edges);

        assertThat(inferred)
                .singleElement()
                .satisfies(edge -> {
                    assertThat(edge.type()).isEqualTo("TRIGGERS");
                    assertThat(edge.sourceFullName()).isEqualTo("com.example.UserService.create()");
                    assertThat(edge.targetFullName()).isEqualTo("com.example.UserListener.onUserCreated(UserCreatedEvent)");
                    assertThat(edge.properties())
                            .containsEntry("inferred", true)
                            .containsEntry("reason", "SPRING_EVENT_MATCH")
                            .containsEntry("eventType", "com.example.UserCreatedEvent");
                });
    }

    private NodeData node(String type, String name, String fullName) {
        return NodeData.of(type, name, fullName, "Example.java", 1, 1, Map.of());
    }
}
