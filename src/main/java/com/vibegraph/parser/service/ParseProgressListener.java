package com.vibegraph.parser.service;

/**
 * Receives per-file parse progress from {@link ParserService#parseProject}.
 *
 * <p>Lets callers translate raw file counts into an overall analysis percentage
 * so the UI can show smooth progress during the (usually dominant) parse phase.
 */
@FunctionalInterface
public interface ParseProgressListener {

    /** A listener that discards every update. Used by the backward-compatible overload. */
    ParseProgressListener NOOP = (filesParsed, totalFiles) -> { };

    /**
     * @param filesParsed number of .java files parsed so far (0..totalFiles)
     * @param totalFiles  total number of .java files discovered in the project
     */
    void onFileParsed(int filesParsed, int totalFiles);
}
