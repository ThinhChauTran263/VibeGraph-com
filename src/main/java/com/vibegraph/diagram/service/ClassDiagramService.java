package com.vibegraph.diagram.service;

import com.vibegraph.diagram.dto.response.DiagramResponse;

/**
 * Class diagram generator.
 *
 * <p>Derives a Mermaid {@code classDiagram} from the project's knowledge graph:
 * <ul>
 *   <li><b>Classifiers</b> — Class / Interface / Enum nodes, with their
 *       {@code HAS_METHOD} methods and {@code HAS_FIELD} fields rendered with
 *       visibility markers ({@code +} public, {@code -} private, {@code #}
 *       protected, {@code ~} package).</li>
 *   <li><b>Relationships</b> — {@code EXTENDS} ({@code --|>}),
 *       {@code IMPLEMENTS} ({@code ..|>}) and {@code INJECTS} ({@code -->})
 *       between classifiers that survive the package filter.</li>
 * </ul>
 *
 * <p>Association via field type ({@code TYPE_OF}) is intentionally omitted to
 * keep the diagram readable; injection dependencies already capture the
 * meaningful collaborations.
 */
public interface ClassDiagramService {

    /**
     * Build the class diagram for a project, optionally restricted to a package.
     *
     * @param projectId     the project identifier
     * @param packageFilter package prefix to include (e.g. {@code com.app.web});
     *                      {@code null}/blank includes every classifier. A class
     *                      matches when its package equals the filter or is a
     *                      sub-package of it.
     * @return a {@link DiagramResponse} with a valid Mermaid {@code classDiagram};
     *         an empty-but-valid diagram when nothing matches.
     */
    DiagramResponse generateClassDiagram(String projectId, String packageFilter);
}
