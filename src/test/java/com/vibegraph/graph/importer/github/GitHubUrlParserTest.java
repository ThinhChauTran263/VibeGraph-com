package com.vibegraph.graph.importer.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.vibegraph.common.exception.GithubImportException;

@DisplayName("GitHubUrlParser")
class GitHubUrlParserTest {

    private final GitHubUrlParser parser = new GitHubUrlParser();

    @Test
    @DisplayName("parses a normal GitHub repository URL")
    void parsesRepositoryUrl() {
        GitHubRepositoryRef ref = parser.parse("https://github.com/spring-projects/spring-petclinic");

        assertThat(ref.owner()).isEqualTo("spring-projects");
        assertThat(ref.repo()).isEqualTo("spring-petclinic");
        assertThat(ref.ref()).isNull();
        assertThat(ref.displayName()).isEqualTo("spring-projects/spring-petclinic");
    }

    @Test
    @DisplayName("accepts .git suffix and trailing slash")
    void acceptsGitSuffixAndTrailingSlash() {
        GitHubRepositoryRef ref = parser.parse("https://github.com/acme/demo.git/");

        assertThat(ref.owner()).isEqualTo("acme");
        assertThat(ref.repo()).isEqualTo("demo");
    }

    @Test
    @DisplayName("rejects non-repository GitHub paths")
    void rejectsNonRepositoryPaths() {
        assertThatThrownBy(() -> parser.parse("https://github.com/acme/demo/tree/main"))
                .isInstanceOf(GithubImportException.class)
                .hasMessageContaining("repository root");
    }

    @Test
    @DisplayName("rejects non-GitHub HTTPS URLs")
    void rejectsNonGithubUrls() {
        assertThatThrownBy(() -> parser.parse("https://gitlab.com/acme/demo"))
                .isInstanceOf(GithubImportException.class)
                .hasMessageContaining("github.com");
    }
}
