package com.vibegraph.diagram.service;

/**
 * Mermaid syntax generator helpers.
 *
 * Centralises the escaping/sanitising rules so every diagram service produces
 * syntactically valid Mermaid regardless of how exotic the source identifiers
 * are (paths, generics, spaces, quotes, etc.).
 */
public interface MermaidGeneratorService {

    /**
     * Convert an arbitrary name into a safe Mermaid node identifier.
     *
     * <p>The result only contains {@code [A-Za-z0-9_]}, never starts with a
     * digit, and is never blank — so it can be interpolated directly as a node
     * id without breaking Mermaid syntax. {@code null}/blank input yields a
     * stable {@code "n"} placeholder.
     */
    String sanitizeId(String raw);

    /**
     * Escape arbitrary text for safe use inside a double-quoted Mermaid label
     * (e.g. {@code id["<escaped>"]}).
     *
     * <p>Double quotes become {@code #quot;}, control characters and line
     * breaks collapse to spaces. {@code null} input yields an empty string.
     */
    String escapeLabel(String raw);
}
