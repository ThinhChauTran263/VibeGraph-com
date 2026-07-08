package com.vibegraph.graph.importer.github;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.vibegraph.common.exception.GithubImportException;

/** Parses supported GitHub repository URLs into owner/repo references. */
@Component
public class GitHubUrlParser {

    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9_.-]+");

    public GitHubRepositoryRef parse(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new GithubImportException("GitHub URL is required");
        }

        URI uri;
        try {
            uri = new URI(rawUrl.trim());
        } catch (URISyntaxException e) {
            throw new GithubImportException("Invalid GitHub URL", e);
        }

        if (!"https".equalsIgnoreCase(uri.getScheme()) || !"github.com".equalsIgnoreCase(uri.getHost())) {
            throw new GithubImportException("URL must match https://github.com/{owner}/{repo}");
        }

        String[] parts = uri.getPath() == null ? new String[0] : uri.getPath().replaceAll("^/+|/+$", "").split("/");
        if (parts.length != 2) {
            throw new GithubImportException("URL must point to a GitHub repository root");
        }

        String owner = parts[0];
        String repo = parts[1].endsWith(".git") ? parts[1].substring(0, parts[1].length() - 4) : parts[1];
        if (!SEGMENT.matcher(owner).matches() || !SEGMENT.matcher(repo).matches()) {
            throw new GithubImportException("GitHub owner or repository name contains unsupported characters");
        }
        return new GitHubRepositoryRef(owner, repo, null);
    }
}
