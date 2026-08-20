package com.vibegraph.graph.importer.github;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vibegraph.common.exception.GithubImportException;
import com.vibegraph.graph.importer.config.GitHubImportProperties;

/** Downloads GitHub repository tarballs to a server-owned workspace file. */
@Component
public class GitHubTarballClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubTarballClient.class);
    private static final Set<Integer> RETRYABLE_STATUS_CODES = Set.of(408, 429, 500, 502, 503, 504);
    /**
     * F10 audit fix: the client follows redirects ({@code Redirect.NORMAL}), so the final
     * host is decided by the redirect chain, not by the request URL. Every response must
     * therefore land on a GitHub host we trust; anything else is refused outright.
     */
    private static final Set<String> ALLOWED_RESPONSE_HOSTS = Set.of("api.github.com", "codeload.github.com");

    private final Duration tarballRequestTimeout;
    private final Duration retryInitialDelay;
    private final int maxAttempts;
    private final HttpClient httpClient;

    @Autowired
    public GitHubTarballClient(GitHubImportProperties github) {
        this(github, HttpClient.newBuilder()
                .connectTimeout(github.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    // Test seam (keeps the legacy single-HttpClient signature working): uses default timeouts.
    GitHubTarballClient(HttpClient httpClient) {
        this(new GitHubImportProperties(), httpClient);
    }

    GitHubTarballClient(GitHubImportProperties github, HttpClient httpClient) {
        this.tarballRequestTimeout = github.getTarballRequestTimeout();
        this.retryInitialDelay = nonNegative(github.getRetryInitialDelay());
        this.maxAttempts = Math.min(5, Math.max(1, github.getMaxAttempts()));
        this.httpClient = httpClient;
    }

    public void downloadTarball(GitHubRepositoryRef ref, Path target, long maxBytes) {
        HttpRequest request = HttpRequest.newBuilder(tarballUri(ref))
                .timeout(tarballRequestTimeout)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "VibeGraph")
                .GET()
                .build();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            HttpResponse<InputStream> response;
            try {
                response = sendOnce(request);
            } catch (IOException e) {
                if (attempt == maxAttempts) {
                    throw exhausted(e);
                }
                pauseBeforeRetry(attempt, e.getMessage());
                continue;
            }
            if (!isAllowedHost(response.uri())) {
                closeQuietly(response.body());
                throw new GithubImportException("GitHub redirected the tarball request to an unexpected host: "
                        + (response.uri() == null ? null : response.uri().getHost()));
            }
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                storeTarball(response, target, maxBytes);
                return;
            }
            closeQuietly(response.body());
            if (!RETRYABLE_STATUS_CODES.contains(status)) {
                throw new GithubImportException("GitHub tarball download failed with HTTP " + status);
            }
            if (attempt == maxAttempts) {
                throw new GithubImportException(
                        "GitHub tarball download failed with HTTP " + status + " after " + maxAttempts + " attempts");
            }
            pauseBeforeRetry(attempt, "HTTP " + status);
        }
    }

    private HttpResponse<InputStream> sendOnce(HttpRequest request) throws IOException {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GithubImportException("GitHub tarball download was interrupted", e);
        }
    }

    private GithubImportException exhausted(IOException cause) {
        return new GithubImportException(
                "Failed to download GitHub tarball after " + maxAttempts + " attempts: " + cause.getMessage(),
                cause);
    }

    private void storeTarball(HttpResponse<InputStream> response, Path target, long maxBytes) {
        try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(target)) {
            copyCapped(in, out, maxBytes);
        } catch (IOException e) {
            throw new GithubImportException("Failed to store GitHub tarball: " + e.getMessage(), e);
        }
    }

    private void pauseBeforeRetry(int failedAttempt, String reason) {
        Duration delay = retryInitialDelay.multipliedBy(1L << Math.min(failedAttempt - 1, 20));
        log.warn("GitHub tarball request failed on attempt {}/{}; retrying in {} ms: {}",
                failedAttempt, maxAttempts, delay.toMillis(), reason);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GithubImportException("GitHub tarball retry was interrupted", e);
        }
    }

    private Duration nonNegative(Duration value) {
        if (value == null || value.isNegative()) {
            return Duration.ZERO;
        }
        return value;
    }

    private void closeQuietly(InputStream body) {
        if (body == null) {
            return;
        }
        try {
            body.close();
        } catch (IOException ignored) {
            // The response is already unusable; preserve the original HTTP failure.
        }
    }

    private URI tarballUri(GitHubRepositoryRef ref) {
        String encodedRef = URLEncoder.encode(ref.ref(), StandardCharsets.UTF_8).replace("+", "%20");
        return URI.create("https://api.github.com/repos/" + ref.owner() + "/" + ref.repo() + "/tarball/" + encodedRef);
    }

    private boolean isAllowedHost(URI finalUri) {
        String host = finalUri == null ? null : finalUri.getHost();
        return host != null && ALLOWED_RESPONSE_HOSTS.contains(host.toLowerCase(Locale.ROOT));
    }

    private void copyCapped(InputStream in, OutputStream out, long maxBytes) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0L;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new GithubImportException("Downloaded GitHub tarball exceeds the configured maximum size");
            }
            out.write(buffer, 0, read);
        }
    }
}
