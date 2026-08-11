package com.vibegraph.mcp.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response of the {@code explain_compile_error} MCP tool: javac/Maven compiler output mapped
 * back to graph symbols with actionable hints — closing the compile-fix loop for agents.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompileErrorExplanationResponse {
    private String projectId;
    private int parsedErrors;
    private List<CompileError> errors;
    private List<String> warnings;
    private List<String> notes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompileError {
        private String relativePath;
        private Integer lineNumber;
        private String message;
        /** Enclosing graph symbol at the error line, when the file is in the analyzed graph. */
        private SymbolRef symbol;
        /** Incoming CALLS on the enclosing symbol — callers that may need the same fix. */
        private Integer callersCount;
        private List<String> hints;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SymbolRef {
        private String id;
        private String type;
        private String name;
        private String fullName;
    }
}
