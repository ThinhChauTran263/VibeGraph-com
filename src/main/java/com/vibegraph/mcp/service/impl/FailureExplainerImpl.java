package com.vibegraph.mcp.service.impl;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.vibegraph.common.exception.ProjectNotFoundException;
import com.vibegraph.graph.dto.response.EdgeDto;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.mcp.dto.response.FailureExplanationResponse;
import com.vibegraph.mcp.dto.response.FailureExplanationResponse.Frame;
import com.vibegraph.mcp.dto.response.FailureExplanationResponse.RootCause;
import com.vibegraph.mcp.dto.response.FailureExplanationResponse.SourceSnippet;
import com.vibegraph.mcp.dto.response.FailureExplanationResponse.TestTarget;
import com.vibegraph.mcp.service.FailureExplainer;
import com.vibegraph.mcp.source.GraphView;
import com.vibegraph.mcp.source.SourceFileService;
import com.vibegraph.mcp.source.SourceFileService.SourceContent;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FailureExplainerImpl implements FailureExplainer {

    private static final int DEFAULT_MAX_FRAMES = 20;
    private static final int HARD_CAP_FRAMES = 100;
    private static final int MAX_CALLS = 15;
    private static final int SNIPPET_RADIUS = 12;
    private static final Pattern FRAME = Pattern.compile("(?m)^\\s*at\\s+([\\w.$]+)\\(([^)]*)\\)");
    private static final java.util.Set<String> CLASSLIKE = java.util.Set.of("Class", "Interface", "Enum", "Record");
    private static final List<String> TEST_SUFFIXES = List.of("Test", "Tests", "IT", "ITCase", "IntegrationTest");

    private final com.vibegraph.mcp.source.SourceGraphSupport graphSupport;
    private final SourceFileService sourceFileService;

    @Override
    public FailureExplanationResponse explainFailure(String projectId, String stackTrace, String testName,
            String errorMessage, String failingFile, boolean includeSource, Integer maxFrames) {
        String normalizedProjectId = validate(projectId, "projectId", 512);
        if (blank(stackTrace) && blank(testName) && blank(errorMessage) && blank(failingFile)) {
            throw new IllegalArgumentException("Provide one of: stackTrace, testName, errorMessage, failingFile");
        }
        int frameCap = boundFrames(maxFrames);
        String inputType = blank(stackTrace) ? (blank(testName) ? (blank(failingFile) ? "errorMessage" : "failingFile") : "testName") : "stackTrace";

        GraphView graph = safeLoad(normalizedProjectId);
        if (graph == null) {
            return warning(normalizedProjectId, inputType, "Failure explanation is temporarily unavailable.");
        }
        Path root = safeRoot(normalizedProjectId);

        List<String> warnings = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        List<Frame> projectFrames = new ArrayList<>();
        List<RootCause> rootCauses = new ArrayList<>();
        int parsedCount = 0;

        if (!blank(stackTrace)) {
            List<ParsedFrame> parsed = parseFrames(stackTrace, frameCap);
            parsedCount = parsed.size();
            if (parsed.isEmpty()) {
                warnings.add("No stack frames could be parsed; ensure the trace contains 'at package.Class.method(File.java:line)' lines.");
            }
            int index = 0;
            for (ParsedFrame pf : parsed) {
                index++;
                NodeDto classNode = findClass(graph, pf.declaringClass);
                if (classNode == null) {
                    continue;
                }
                NodeDto methodNode = findMethod(graph, pf.declaringClass, pf.methodName);
                Frame frame = toFrame(index, pf, classNode, methodNode, graph, root, includeSource, normalizedProjectId);
                projectFrames.add(frame);
                if (rootCauses.size() < 2) {
                    rootCauses.add(RootCause.builder()
                            .fullName(methodNode != null ? methodNode.getFullName() : classNode.getFullName())
                            .relativePath(frame.getRelativePath())
                            .lineNumber(pf.lineNumber)
                            .reason(rootCauses.isEmpty()
                                    ? "Topmost in-project frame — most likely the throw site or nearest handler."
                                    : "Caller of the throw site; inspect arguments/state passed downstream.")
                            .build());
                }
            }
            if (parsedCount > 0 && projectFrames.isEmpty()) {
                warnings.add("No project frames found; the stack trace is entirely external (library/JDK). "
                        + "Inspect the nearest call from your own code that reaches this library.");
            }
        }

        TestTarget testTarget = blank(testName) ? null : resolveTest(graph, testName, root);
        if (!blank(testName) && testTarget == null) {
            warnings.add("Test not found in the graph: " + testName.trim());
        }

        if (!blank(failingFile)) {
            NodeDto classNode = findClassByPath(graph, root, failingFile.trim());
            if (classNode != null && rootCauses.isEmpty()) {
                rootCauses.add(RootCause.builder()
                        .fullName(classNode.getFullName())
                        .relativePath(relativize(root, classNode.getFilePath()))
                        .reason("Reported failing file.")
                        .build());
            } else if (classNode == null) {
                warnings.add("Failing file not matched to a project class: " + failingFile.trim());
            }
        }

        if (!blank(errorMessage)) {
            notes.add("Error message provided; for precise mapping include the full stack trace. "
                    + "Message: " + errorMessage.trim().replaceAll("\\s+", " "));
        }

        List<String> steps = debuggingSteps(projectFrames, testTarget, rootCauses);

        return FailureExplanationResponse.builder()
                .projectId(normalizedProjectId)
                .inputType(inputType)
                .parsedFrames(parsedCount)
                .projectFrameCount(projectFrames.size())
                .projectFrames(projectFrames)
                .testTarget(testTarget)
                .likelyRootCauses(rootCauses)
                .debuggingSteps(steps)
                .warnings(warnings)
                .notes(notes)
                .build();
    }

    private Frame toFrame(int index, ParsedFrame pf, NodeDto classNode, NodeDto methodNode, GraphView graph,
            Path root, boolean includeSource, String projectId) {
        List<String> calls = List.of();
        boolean handlesRoute = false;
        if (methodNode != null) {
            List<EdgeDto> out = graph.outgoing(methodNode.getId());
            calls = out.stream().filter(e -> "CALLS".equals(e.getType()))
                    .map(e -> graph.byId(e.getTarget()))
                    .filter(java.util.Objects::nonNull)
                    .map(NodeDto::getName)
                    .filter(java.util.Objects::nonNull)
                    .distinct().limit(MAX_CALLS).toList();
            handlesRoute = out.stream().anyMatch(e -> "HANDLES_ROUTE".equals(e.getType()));
        }
        String relativePath = relativize(root, classNode.getFilePath());
        SourceSnippet snippet = null;
        if (includeSource && pf.lineNumber != null && relativePath != null) {
            snippet = snippet(projectId, classNode.getFilePath(), pf.lineNumber);
        }
        return Frame.builder()
                .index(index)
                .declaringClass(pf.declaringClass)
                .methodName(pf.methodName)
                .fileName(pf.fileName)
                .lineNumber(pf.lineNumber)
                .inProject(true)
                .nodeId(methodNode != null ? methodNode.getId() : classNode.getId())
                .relativePath(relativePath)
                .calls(calls)
                .handlesRoute(handlesRoute)
                .source(snippet)
                .build();
    }

    private TestTarget resolveTest(GraphView graph, String testName, Path root) {
        String name = testName.trim();
        String simple = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : name;
        String bareSimple = GraphView.stripParens(simple);
        NodeDto testNode = graph.nodes().stream()
                .filter(n -> CLASSLIKE.contains(n.getType()))
                .filter(n -> bareSimple.equals(n.getName()) || name.equals(n.getFullName()))
                .findFirst()
                .orElseGet(() -> graph.nodes().stream()
                        .filter(n -> "Method".equals(n.getType()))
                        .filter(n -> bareSimple.equals(n.getName()))
                        .findFirst().orElse(null));
        if (testNode == null) {
            return null;
        }
        NodeDto testClass = CLASSLIKE.contains(testNode.getType()) ? testNode : owningClass(graph, testNode);
        List<String> production = new ArrayList<>();
        if (testClass != null) {
            java.util.Set<String> classMemberIds = new java.util.LinkedHashSet<>();
            classMemberIds.add(testClass.getId());
            String prefix = testClass.getFullName() + ".";
            graph.nodes().stream().filter(n -> n.getFullName() != null && n.getFullName().startsWith(prefix))
                    .forEach(n -> classMemberIds.add(n.getId()));
            for (EdgeDto edge : graph.edges()) {
                if (!classMemberIds.contains(edge.getSource())) {
                    continue;
                }
                if (!java.util.Set.of("CALLS", "TYPE_OF", "IMPORTS", "INSTANTIATES").contains(edge.getType())) {
                    continue;
                }
                NodeDto target = graph.byId(edge.getTarget());
                if (target != null && CLASSLIKE.contains(target.getType()) && !isTestName(target.getName())
                        && target.getFullName() != null && !production.contains(target.getFullName())) {
                    production.add(target.getFullName());
                }
                if (production.size() >= MAX_CALLS) {
                    break;
                }
            }
        }
        return TestTarget.builder()
                .testId(testNode.getId())
                .testName(testNode.getFullName())
                .testRelativePath(relativize(root, (testClass != null ? testClass : testNode).getFilePath()))
                .productionTargets(production)
                .build();
    }

    private List<String> debuggingSteps(List<Frame> frames, TestTarget testTarget, List<RootCause> rootCauses) {
        List<String> steps = new ArrayList<>();
        if (!rootCauses.isEmpty()) {
            RootCause primary = rootCauses.get(0);
            steps.add("Open " + primary.getRelativePath()
                    + (primary.getLineNumber() != null ? ":" + primary.getLineNumber() : "") + " and inspect the failing statement.");
        }
        if (!frames.isEmpty()) {
            steps.add("Walk the in-project frames top-down; verify arguments/state at each call boundary.");
            if (frames.get(0).isHandlesRoute()) {
                steps.add("The top frame handles an HTTP route; reproduce with the exact request payload.");
            }
        }
        if (testTarget != null) {
            steps.add("Re-run the failing test in isolation and add assertions around the production targets: "
                    + testTarget.getProductionTargets());
        }
        if (frames.isEmpty() && testTarget == null) {
            steps.add("No in-project frames mapped. Find the nearest call from your code into the failing library "
                    + "and add logging/breakpoints there.");
        }
        steps.add("Use get_method_cpg_context on the suspect method to see what it reads/writes/calls.");
        return steps;
    }

    private List<ParsedFrame> parseFrames(String stackTrace, int cap) {
        List<ParsedFrame> frames = new ArrayList<>();
        Matcher matcher = FRAME.matcher(stackTrace);
        while (matcher.find() && frames.size() < cap) {
            String full = matcher.group(1);
            String loc = matcher.group(2);
            int dot = full.lastIndexOf('.');
            if (dot <= 0 || dot == full.length() - 1) {
                continue;
            }
            String declaringClass = full.substring(0, dot);
            String method = full.substring(dot + 1);
            String fileName = loc;
            Integer line = null;
            int colon = loc.lastIndexOf(':');
            if (colon >= 0) {
                fileName = loc.substring(0, colon);
                try {
                    line = Integer.parseInt(loc.substring(colon + 1).trim());
                } catch (NumberFormatException ignored) {
                    line = null;
                }
            }
            frames.add(new ParsedFrame(declaringClass, method, fileName, line));
        }
        return frames;
    }

    private NodeDto findClass(GraphView graph, String fqcn) {
        String outer = fqcn.contains("$") ? fqcn.substring(0, fqcn.indexOf('$')) : fqcn;
        return graph.nodes().stream()
                .filter(n -> CLASSLIKE.contains(n.getType()))
                .filter(n -> fqcn.equals(n.getFullName()) || outer.equals(n.getFullName()))
                .findFirst().orElse(null);
    }

    private NodeDto findMethod(GraphView graph, String fqcn, String method) {
        String prefix = fqcn + "." + method + "(";
        return graph.nodes().stream()
                .filter(n -> "Method".equals(n.getType()) || "Constructor".equals(n.getType()))
                .filter(n -> n.getFullName() != null && n.getFullName().startsWith(prefix))
                .findFirst().orElse(null);
    }

    private NodeDto findClassByPath(GraphView graph, Path root, String relativePath) {
        String rp = relativePath.replace('\\', '/');
        return graph.nodes().stream()
                .filter(n -> CLASSLIKE.contains(n.getType()))
                .filter(n -> rp.equals(relativize(root, n.getFilePath())) || (n.getFilePath() != null && n.getFilePath().replace('\\', '/').endsWith(rp)))
                .findFirst().orElse(null);
    }

    private NodeDto owningClass(GraphView graph, NodeDto node) {
        String bare = GraphView.stripParens(node.getFullName());
        String owner = bare.contains(".") ? bare.substring(0, bare.lastIndexOf('.')) : null;
        if (owner == null) {
            return null;
        }
        return graph.nodes().stream().filter(n -> CLASSLIKE.contains(n.getType()))
                .filter(n -> owner.equals(n.getFullName())).findFirst().orElse(null);
    }

    private SourceSnippet snippet(String projectId, String absoluteFilePath, int line) {
        int start = Math.max(1, line - SNIPPET_RADIUS);
        int end = line + SNIPPET_RADIUS;
        try {
            SourceContent content = sourceFileService.readRange(projectId, absoluteFilePath, start, end);
            if (!content.found()) {
                return null;
            }
            return SourceSnippet.builder()
                    .relativePath(content.relativePath())
                    .startLine(content.startLine())
                    .endLine(content.endLine())
                    .content(content.content())
                    .truncated(content.truncated())
                    .build();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private boolean isTestName(String name) {
        return name != null && TEST_SUFFIXES.stream().anyMatch(name::endsWith);
    }

    private Path safeRoot(String projectId) {
        try {
            return sourceFileService.resolveProjectRoot(projectId);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String relativize(Path root, String absoluteFilePath) {
        if (root == null || absoluteFilePath == null || absoluteFilePath.isBlank()) {
            return null;
        }
        try {
            Path filePath = Path.of(absoluteFilePath).normalize();
            if (!filePath.startsWith(root)) {
                return null;
            }
            return root.relativize(filePath).toString().replace('\\', '/');
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private FailureExplanationResponse warning(String projectId, String inputType, String message) {
        return FailureExplanationResponse.builder()
                .projectId(projectId).inputType(inputType)
                .parsedFrames(0).projectFrameCount(0)
                .projectFrames(List.of()).likelyRootCauses(List.of()).debuggingSteps(List.of())
                .warnings(List.of(message)).notes(List.of())
                .build();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private int boundFrames(Integer maxFrames) {
        if (maxFrames == null || maxFrames <= 0) {
            return DEFAULT_MAX_FRAMES;
        }
        return Math.min(maxFrames, HARD_CAP_FRAMES);
    }

    private GraphView safeLoad(String projectId) {
        try {
            return graphSupport.load(projectId);
        } catch (ProjectNotFoundException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String validate(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength
                || value.chars().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t')) {
            throw new IllegalArgumentException(field + " must be non-blank and at most " + maxLength + " characters");
        }
        return value.trim();
    }

    private record ParsedFrame(String declaringClass, String methodName, String fileName, Integer lineNumber) {
    }
}
