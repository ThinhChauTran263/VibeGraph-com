package com.vibegraph.parser.node;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Internal parse result containing all extracted nodes and edges from a single file.
 * Used internally between parser and graph builder.
 */
@Data
@Builder
public class ParseResult {
    private String filePath;
    private String fileChecksum;

    @Builder.Default
    private List<Object> nodes = new ArrayList<>();

    @Builder.Default
    private List<Object> edges = new ArrayList<>();

    @Builder.Default
    private List<String> warnings = new ArrayList<>();
}
