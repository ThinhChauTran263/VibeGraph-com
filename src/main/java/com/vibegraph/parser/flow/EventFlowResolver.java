package com.vibegraph.parser.flow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.vibegraph.parser.node.EdgeData;
import com.vibegraph.parser.node.NodeData;

/**
 * Joins Spring event publication/listener facts after the full project graph is known.
 */
public final class EventFlowResolver {

    private EventFlowResolver() {
    }

    public static List<EdgeData> inferTriggers(List<NodeData> nodes, List<EdgeData> edges) {
        if (nodes == null || edges == null) {
            return List.of();
        }

        Set<String> nodeIds = new LinkedHashSet<>();
        Set<String> methodIds = new LinkedHashSet<>();
        for (NodeData node : nodes) {
            if (node == null) {
                continue;
            }
            nodeIds.add(node.fullName());
            if ("Method".equals(node.type()) || "Constructor".equals(node.type())) {
                methodIds.add(node.fullName());
            }
        }

        Map<String, List<String>> publishersByEvent = new LinkedHashMap<>();
        Map<String, List<String>> listenersByEvent = new LinkedHashMap<>();
        for (EdgeData edge : edges) {
            if (edge == null || !nodeIds.contains(edge.targetFullName())) {
                continue;
            }
            if ("PUBLISHES_EVENT".equals(edge.type()) && methodIds.contains(edge.sourceFullName())) {
                publishersByEvent.computeIfAbsent(edge.targetFullName(), ignored -> new ArrayList<>())
                        .add(edge.sourceFullName());
            } else if ("LISTENS_EVENT".equals(edge.type()) && methodIds.contains(edge.sourceFullName())) {
                listenersByEvent.computeIfAbsent(edge.targetFullName(), ignored -> new ArrayList<>())
                        .add(edge.sourceFullName());
            }
        }

        List<EdgeData> result = new ArrayList<>();
        Set<String> emitted = new LinkedHashSet<>();
        for (Map.Entry<String, List<String>> entry : publishersByEvent.entrySet()) {
            List<String> listeners = listenersByEvent.getOrDefault(entry.getKey(), List.of());
            for (String publisher : entry.getValue()) {
                for (String listener : listeners) {
                    if (publisher.equals(listener)) {
                        continue;
                    }
                    String key = publisher + "|TRIGGERS|" + listener;
                    if (emitted.add(key)) {
                        result.add(EdgeData.of("TRIGGERS", publisher, listener, Map.of(
                                "inferred", true,
                                "confidence", 0.95,
                                "ambiguous", false,
                                "reason", "SPRING_EVENT_MATCH",
                                "eventType", entry.getKey()
                        )));
                    }
                }
            }
        }
        return result;
    }
}
