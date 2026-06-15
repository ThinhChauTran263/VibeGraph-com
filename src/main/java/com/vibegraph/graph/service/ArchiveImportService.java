package com.vibegraph.graph.service;

import org.springframework.web.multipart.MultipartFile;

import com.vibegraph.graph.dto.response.ProjectResponse;

/**
 * Archive-upload onboarding: import a Java project from an uploaded
 * {@code .zip}/{@code .tar}/{@code .tar.gz} into a server-owned workspace, analyze it,
 * and return the registered project. The caller never supplies a {@code rootPath}.
 */
public interface ArchiveImportService {

    /**
     * @param name display name for the project
     * @param file the uploaded archive (multipart)
     * @return the analyzed project
     */
    ProjectResponse importArchive(String name, MultipartFile file);

    /**
     * Asynchronous variant. Validation, extraction, and project registration run synchronously
     * (so archive errors still surface immediately to the caller), then the project is marked
     * {@code ANALYZING} and analysis is submitted to a background executor. Returns the freshly
     * registered project in {@code ANALYZING}/progress 0 without waiting for analysis to finish;
     * subsequent status ({@code ANALYZED}/{@code FAILED}) is published over WebSocket.
     *
     * @param name display name for the project
     * @param file the uploaded archive (multipart)
     * @return the registered project, status {@code ANALYZING}, progress 0
     */
    ProjectResponse importArchiveAsync(String name, MultipartFile file);
}
