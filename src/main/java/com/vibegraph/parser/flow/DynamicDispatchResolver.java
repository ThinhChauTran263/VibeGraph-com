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
 * Resolves interface method dispatch only when the project graph makes it deterministic.
 */
public final class DynamicDispatchResolver {

    private DynamicDispatchResolver() {
    }

    public static List<EdgeData> inferDispatch(List<NodeData> nodes, List<EdgeData> edges) {
        if (nodes == null || edges == null) {
            return List.of();
        }

        Map<String, NodeData> nodesById = new LinkedHashMap<>();
        Set<String> interfaceTypes = new LinkedHashSet<>();
        Set<String> methodIds = new LinkedHashSet<>();
        for (NodeData node : nodes) {
            if (node == null) {
                continue;
            }
            nodesById.put(node.fullName(), node);
            if ("Interface".equals(node.type())) {
                interfaceTypes.add(node.fullName());
            }
            if ("Method".equals(node.type()) || "Constructor".equals(node.type())) {
                methodIds.add(node.fullName());
            }
        }

        Map<String, Set<String>> implementationsByInterface = implementationsByInterface(edges, nodesById);
        Set<String> calledInterfaceMethods = new LinkedHashSet<>();
        for (EdgeData edge : edges) {
            if (edge != null && "CALLS".equals(edge.type()) && methodIds.contains(edge.targetFullName())) {
                String owner = ownerOfMethod(edge.targetFullName());
                if (interfaceTypes.contains(owner)) {
                    calledInterfaceMethods.add(edge.targetFullName());
                }
            }
        }

        List<EdgeData> result = new ArrayList<>();
        Set<String> emitted = new LinkedHashSet<>();
        for (String interfaceMethod : calledInterfaceMethods) {
            String interfaceType = ownerOfMethod(interfaceMethod);
            String methodSuffix = interfaceMethod.substring(interfaceType.length());
            List<String> candidates = implementationsByInterface.getOrDefault(interfaceType, Set.of()).stream()
                    .map(impl -> impl + methodSuffix)
                    .filter(methodIds::contains)
                    .sorted()
                    .toList();

            if (candidates.size() == 1) {
                String target = candidates.get(0);
                String key = interfaceMethod + "|RESOLVES_TO|" + target;
                if (emitted.add(key)) {
                    result.add(EdgeData.of("RESOLVES_TO", interfaceMethod, target, Map.of(
                            "inferred", true,
                            "confidence", 1.0,
                            "ambiguous", false,
                            "reason", "SINGLE_IMPLEMENTATION_METHOD_MATCH"
                    )));
                }
            } else if (candidates.size() > 1) {
                double confidence = 1.0 / candidates.size();
                for (String target : candidates) {
                    String key = interfaceMethod + "|DISPATCH_CANDIDATES|" + target;
                    if (emitted.add(key)) {
                        result.add(EdgeData.of("DISPATCH_CANDIDATES", interfaceMethod, target, Map.of(
                                "inferred", true,
                                "confidence", confidence,
                                "ambiguous", true,
                                "reason", "AMBIGUOUS_INTERFACE_DISPATCH"
                        )));
                    }
                }
            }
        }
        return result;
    }

    private static Map<String, Set<String>> implementationsByInterface(
            List<EdgeData> edges, Map<String, NodeData> nodesById) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (EdgeData edge : edges) {
            if (edge == null || !"IMPLEMENTS".equals(edge.type())) {
                continue;
            }
            NodeData source = nodesById.get(edge.sourceFullName());
            NodeData target = nodesById.get(edge.targetFullName());
            if (source == null || target == null
                    || !"Class".equals(source.type())
                    || !"Interface".equals(target.type())) {
                continue;
            }
            result.computeIfAbsent(edge.targetFullName(), ignored -> new LinkedHashSet<>())
                    .add(edge.sourceFullName());
        }
        return result;
    }

    private static String ownerOfMethod(String methodFullName) {
        if (methodFullName == null) {
            return "";
        }
        int paren = methodFullName.indexOf('(');
        int dot = paren > 0 ? methodFullName.lastIndexOf('.', paren) : methodFullName.lastIndexOf('.');
        return dot > 0 ? methodFullName.substring(0, dot) : "";
    }
}
