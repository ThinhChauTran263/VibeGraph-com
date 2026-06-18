package com.vibegraph.mcp.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for {@code explain_failure_path}: maps a stack trace / test failure to in-project
 * code, likely root-cause locations, and debugging steps. Paths are relative; nothing is invented.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailureExplanationResponse {
    private String projectId;
    private String inputType;
    private int parsedFrames;
    private int projectFrameCount;
    private List<Frame> projectFrames;
    private TestTarget testTarget;
    private List<RootCause> likelyRootCauses;
    private List<String> debuggingSteps;
    private List<String> warnings;
    private List<String> notes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Frame {
        private int index;
        private String declaringClass;
        private String methodName;
        private String fileName;
        private Integer lineNumber;
        private boolean inProject;
        private String nodeId;
        private String relativePath;
        private List<String> calls;
        private boolean handlesRoute;
        private SourceSnippet source;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestTarget {
        private String testId;
        private String testName;
        private String testRelativePath;
        private List<String> productionTargets;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RootCause {
        private String fullName;
        private String relativePath;
        private Integer lineNumber;
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceSnippet {
        private String relativePath;
        private Integer startLine;
        private Integer endLine;
        private String content;
        private boolean truncated;
    }
}
