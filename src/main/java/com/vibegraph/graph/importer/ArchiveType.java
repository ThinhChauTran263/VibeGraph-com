package com.vibegraph.graph.importer;

/**
 * Supported project-archive kinds for upload import.
 * {@code .tgz} is treated as an alias of {@code .tar.gz} and resolves to {@link #TAR_GZ}.
 */
public enum ArchiveType {
    ZIP,
    TAR,
    TAR_GZ
}
