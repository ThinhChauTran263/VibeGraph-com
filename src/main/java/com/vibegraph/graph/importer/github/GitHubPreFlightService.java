package com.vibegraph.graph.importer.github;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibegraph.common.exception.GithubImportException;
import com.vibegraph.graph.importer.config.ArchiveImportProperties;

/** Performs cheap GitHub metadata checks before downloading the tarball. */
@Service
public class GitHubPreFlightService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ArchiveImportProperties properties;
    private final HttpClient httpClient;

    @Autowired
    public GitHubPreFlightService(ArchiveImportProperties properties) {
        this(properties, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    GitHubPreFlightService(ArchiveImportProperties properties, HttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    public GitHubRepositoryRef validatePublicRepository(GitHubRepositoryRef ref) {
        HttpRequest request = HttpRequest.newBuilder(repositoryApiUri(ref))
                .timeout(Duration.ofSeconds(20))
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
        long maxKb = properties.getMaxSize().toKilobytes();
        if (repoSizeKb > maxKb) {
            DataSize maxSize = properties.getMaxSize();
            throw new GithubImportException("GitHub repository is larger than the configured limit (" + maxSize + ")");
        }

        String defaultBranch = metadata.path("default_branch").asText(null);
        if (defaultBranch == null || defaultBranch.isBlank()) {
            throw new GithubImportException("GitHub repository does not expose a default branch");
        }
        return ref.withRef(defaultBranch);
    }

    private URI repositoryApiUri(GitHubRepositoryRef ref) {
        return URI.create("https://api.github.com/repos/" + ref.owner() + "/" + ref.repo());
    }

    private JsonNode parse(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (IOException e) {
            throw new GithubImportException("GitHub returned an unreadable metadata response", e);
        }
    }
}
