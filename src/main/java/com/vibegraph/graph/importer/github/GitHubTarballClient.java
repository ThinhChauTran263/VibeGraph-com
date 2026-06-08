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

import org.springframework.stereotype.Component;

import com.vibegraph.common.exception.GithubImportException;

/** Downloads GitHub repository tarballs to a server-owned workspace file. */
@Component
public class GitHubTarballClient {

    private final HttpClient httpClient;

    public GitHubTarballClient() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    GitHubTarballClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public void downloadTarball(GitHubRepositoryRef ref, Path target, long maxBytes) {
        HttpRequest request = HttpRequest.newBuilder(tarballUri(ref))
                .timeout(Duration.ofSeconds(60))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "VibeGraph")
                .GET()
                .build();

        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException e) {
            throw new GithubImportException("Failed to download GitHub tarball: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GithubImportException("GitHub tarball download was interrupted", e);
        }

        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new GithubImportException("GitHub tarball download failed with HTTP " + status);
        }

        try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(target)) {
            copyCapped(in, out, maxBytes);
        } catch (IOException e) {
            throw new GithubImportException("Failed to store GitHub tarball: " + e.getMessage(), e);
        }
    }

    private URI tarballUri(GitHubRepositoryRef ref) {
        String encodedRef = URLEncoder.encode(ref.ref(), StandardCharsets.UTF_8).replace("+", "%20");
        return URI.create("https://api.github.com/repos/" + ref.owner() + "/" + ref.repo() + "/tarball/" + encodedRef);
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
