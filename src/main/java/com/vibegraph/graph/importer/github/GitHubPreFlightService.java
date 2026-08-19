package com.vibegraph.graph.importer.github;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibegraph.common.exception.GithubImportException;
import com.vibegraph.graph.importer.config.ArchiveImportProperties;
import com.vibegraph.graph.importer.config.GitHubImportProperties;

import lombok.extern.slf4j.Slf4j;

/** Performs cheap GitHub metadata checks before downloading the tarball. */
@Service
@Slf4j
public class GitHubPreFlightService {

    /**
     * F10 audit fix: with {@code Redirect.NORMAL} the final host comes from the redirect
     * chain, so accept a metadata response only when it actually arrived from GitHub's API.
     */
    private static final Set<String> ALLOWED_RESPONSE_HOSTS = Set.of("api.github.com");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ArchiveImportProperties properties;
    private final Duration preflightRequestTimeout;
    private final HttpClient httpClient;

    @Autowired
    public GitHubPreFlightService(ArchiveImportProperties properties, GitHubImportProperties github) {
        this(properties, github, HttpClient.newBuilder()
                .connectTimeout(github.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    // Test seam (keeps the legacy 2-arg signature working): uses default GitHub timeouts.
    GitHubPreFlightService(ArchiveImportProperties properties, HttpClient httpClient) {
        this(properties, new GitHubImportProperties(), httpClient);
    }

    GitHubPreFlightService(ArchiveImportProperties properties, GitHubImportProperties github, HttpClient httpClient) {
        this.properties = properties;
        this.preflightRequestTimeout = github.getPreflightRequestTimeout();
        this.httpClient = httpClient;
    }

    public GitHubRepositoryRef validatePublicRepository(GitHubRepositoryRef ref) {
        return validatePublicRepository(ref, properties.getMaxSize().toBytes());
    }

    public GitHubRepositoryRef validatePublicRepository(GitHubRepositoryRef ref, long maxBytes) {
        HttpRequest request = HttpRequest.newBuilder(repositoryApiUri(ref))
                .timeout(preflightRequestTimeout)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "VibeGraph")
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new GithubImportException("Failed to contact GitHub: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GithubImportException("GitHub pre-flight check was interrupted", e);
        }

        String host = response.uri() == null ? null : response.uri().getHost();
        if (host == null || !ALLOWED_RESPONSE_HOSTS.contains(host.toLowerCase(Locale.ROOT))) {
            throw new GithubImportException(
                    "GitHub redirected the pre-flight request to an unexpected host: " + host);
        }

        int status = response.statusCode();
        if (status == 404) {
            throw new GithubImportException("GitHub repository not found or not public: " + ref.displayName());
        }
        if (status == 403) {
            throw new GithubImportException("GitHub rejected the request, possibly due to rate limiting");
        }
        if (status < 200 || status >= 300) {
            throw new GithubImportException("GitHub pre-flight check failed with HTTP " + status);
        }

        JsonNode metadata = parse(response.body());
        if (metadata.path("private").asBoolean(false)) {
            throw new GithubImportException("Private GitHub repositories are not supported");
        }

        long repoSizeKb = metadata.path("size").asLong(0L);
        long maxKb = Math.max(0L, maxBytes / 1024L);
        if (repoSizeKb > maxKb) {
            DataSize maxSize = DataSize.ofBytes(maxBytes);
            throw new GithubImportException(
                    "GitHub repository is larger than the server's maximum import size (" + maxSize + ")");
        }

        String defaultBranch = metadata.path("default_branch").asText(null);
        if (defaultBranch == null || defaultBranch.isBlank()) {
            throw new GithubImportException("GitHub repository does not expose a default branch");
        }
        return ref.withRef(defaultBranch).withCommitSha(fetchHeadSha(ref, defaultBranch));
    }

    /**
     * Best-effort HEAD commit SHA of the default branch, used by re-imports to tell
     * "nothing changed" (block) from "new commits" (refresh the existing project).
     *
     * <p>A failure here must never break the import itself: returning {@code null}
     * simply disables the up-to-date short-circuit for this import.
     */
    private String fetchHeadSha(GitHubRepositoryRef ref, String branch) {
        HttpRequest request = HttpRequest.newBuilder(headCommitUri(ref, branch))
                .timeout(preflightRequestTimeout)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "VibeGraph")
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String host = response.uri() == null ? null : response.uri().getHost();
            if (response.statusCode() != 200 || host == null
                    || !ALLOWED_RESPONSE_HOSTS.contains(host.toLowerCase(Locale.ROOT))) {
                log.debug("HEAD SHA lookup for {}@{} returned HTTP {} from host {}; skipping up-to-date check",
                        ref.displayName(), branch, response.statusCode(), host);
                return null;
            }
            JsonNode body;
            try {
                body = parse(response.body());
            } catch (GithubImportException unreadable) {
                log.debug("HEAD SHA lookup for {}@{} returned an unreadable body; skipping up-to-date check",
                        ref.displayName(), branch);
                return null;
            }
            String sha = body.path("sha").asText(null);
            return sha == null || sha.isBlank() ? null : sha.trim();
        } catch (IOException e) {
            log.debug("HEAD SHA lookup for {}@{} failed: {}; skipping up-to-date check",
                    ref.displayName(), branch, e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private URI repositoryApiUri(GitHubRepositoryRef ref) {
        return URI.create("https://api.github.com/repos/" + ref.owner() + "/" + ref.repo());
    }

    private URI headCommitUri(GitHubRepositoryRef ref, String branch) {
        return URI.create("https://api.github.com/repos/" + ref.owner() + "/" + ref.repo()
                + "/commits/" + branch);
    }

    private JsonNode parse(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (IOException e) {
            throw new GithubImportException("GitHub returned an unreadable metadata response", e);
        }
    }
}
