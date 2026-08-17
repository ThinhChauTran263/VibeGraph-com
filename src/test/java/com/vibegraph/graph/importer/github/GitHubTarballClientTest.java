package com.vibegraph.graph.importer.github;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vibegraph.common.exception.GithubImportException;
import com.vibegraph.graph.importer.config.GitHubImportProperties;

@ExtendWith(MockitoExtension.class)
class GitHubTarballClientTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<InputStream> response;

    @TempDir
    private Path tempDir;

    private GitHubImportProperties properties;

    @BeforeEach
    void setUp() {
        properties = new GitHubImportProperties();
        properties.setMaxAttempts(3);
        properties.setRetryInitialDelay(Duration.ZERO);
    }

    @Test
    void downloadTarball_ConnectTimeoutThenSuccess_RetriesAndStoresTarball() throws Exception {
        byte[] tarball = "tarball-data".getBytes(StandardCharsets.UTF_8);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(new ByteArrayInputStream(tarball));
        doThrow(new HttpConnectTimeoutException("HTTP connect timed out"))
                .doReturn(response)
                .when(httpClient)
                .send(any(HttpRequest.class), anyInputStreamHandler());

        Path target = tempDir.resolve("repo.tar.gz");
        new GitHubTarballClient(properties, httpClient)
                .downloadTarball(repository(), target, 1024);

        assertThat(Files.readAllBytes(target)).isEqualTo(tarball);
        verify(httpClient, times(2)).send(any(HttpRequest.class), anyInputStreamHandler());
    }

    @Test
    void downloadTarball_ConnectTimeoutPersists_StopsAtConfiguredAttemptLimit() throws Exception {
        doThrow(new HttpConnectTimeoutException("HTTP connect timed out"))
                .when(httpClient)
                .send(any(HttpRequest.class), anyInputStreamHandler());

        GitHubTarballClient client = new GitHubTarballClient(properties, httpClient);

        assertThatThrownBy(() -> client.downloadTarball(repository(), tempDir.resolve("repo.tar.gz"), 1024))
                .isInstanceOf(GithubImportException.class)
                .hasMessageContaining("after 3 attempts")
                .hasMessageContaining("HTTP connect timed out");
        verify(httpClient, times(3)).send(any(HttpRequest.class), anyInputStreamHandler());
    }

    @Test
    void downloadTarball_MixedTransientFailures_NeverExceedsConfiguredAttemptLimit() throws Exception {
        when(response.statusCode()).thenReturn(503);
        when(response.body()).thenReturn(InputStream.nullInputStream());
        doThrow(new HttpConnectTimeoutException("first timeout"))
                .doReturn(response)
                .doThrow(new HttpConnectTimeoutException("final timeout"))
                .when(httpClient)
                .send(any(HttpRequest.class), anyInputStreamHandler());

        GitHubTarballClient client = new GitHubTarballClient(properties, httpClient);

        assertThatThrownBy(() -> client.downloadTarball(repository(), tempDir.resolve("repo.tar.gz"), 1024))
                .isInstanceOf(GithubImportException.class)
                .hasMessageContaining("after 3 attempts")
                .hasMessageContaining("final timeout");
        verify(httpClient, times(3)).send(any(HttpRequest.class), anyInputStreamHandler());
    }

    @Test
    void downloadTarball_NotFound_DoesNotRetry() throws Exception {
        when(response.statusCode()).thenReturn(404);
        when(response.body()).thenReturn(InputStream.nullInputStream());
        doReturn(response)
                .when(httpClient)
                .send(any(HttpRequest.class), anyInputStreamHandler());

        GitHubTarballClient client = new GitHubTarballClient(properties, httpClient);

        assertThatThrownBy(() -> client.downloadTarball(repository(), tempDir.resolve("repo.tar.gz"), 1024))
                .isInstanceOf(GithubImportException.class)
                .hasMessageContaining("HTTP 404");
        verify(httpClient).send(any(HttpRequest.class), anyInputStreamHandler());
    }

    @SuppressWarnings("unchecked")
    private HttpResponse.BodyHandler<InputStream> anyInputStreamHandler() {
        return any(HttpResponse.BodyHandler.class);
    }

    private GitHubRepositoryRef repository() {
        return new GitHubRepositoryRef("owner", "repo", "main");
    }
}
