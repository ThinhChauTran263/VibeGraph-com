package com.vibegraph.graph.importer.github;

import java.lang.reflect.Constructor;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;

import com.vibegraph.common.exception.GithubImportException;
import com.vibegraph.graph.importer.config.ArchiveImportProperties;
import com.vibegraph.graph.importer.config.GitHubImportProperties;

@ExtendWith(MockitoExtension.class)
@DisplayName("GitHubPreFlightService")
class GitHubPreFlightServiceTest {

    @Mock
    HttpClient httpClient;

    @Mock
    HttpResponse<String> response;

    private final ArchiveImportProperties properties = new ArchiveImportProperties();

    private GitHubPreFlightService service;

    @BeforeEach
    void setUp() {
        // Use the package-private test constructor to inject the mocked HttpClient.
        service = new GitHubPreFlightService(properties, httpClient);
    }

    @Nested
    @DisplayName("Spring bean wiring")
    class BeanWiring {

        /**
         * Regression guard for the DI bug: the class has two constructors (a production
         * 2-arg one and a package-private 3-arg test seam). Spring cannot choose between
         * multiple constructors unless exactly one is annotated with {@code @Autowired};
         * otherwise it fails with "No default constructor found" at boot.
         */
        @Test
        @DisplayName("declares exactly one @Autowired constructor for injection")
        void hasSingleAutowiredConstructor() {
            long autowiredConstructors = Arrays.stream(GitHubPreFlightService.class.getDeclaredConstructors())
                    .filter(ctor -> ctor.isAnnotationPresent(Autowired.class))
                    .count();

            assertThat(autowiredConstructors)
                    .as("Spring needs exactly one @Autowired constructor when multiple constructors exist")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("the @Autowired constructor is the production constructor")
        void autowiredConstructorIsTheProductionOne() throws NoSuchMethodException {
            Constructor<GitHubPreFlightService> ctor = GitHubPreFlightService.class.getDeclaredConstructor(
                    ArchiveImportProperties.class, GitHubImportProperties.class);

            assertThat(ctor.isAnnotationPresent(Autowired.class)).isTrue();
        }
    }

    @Nested
    @DisplayName("validatePublicRepository")
    class ValidatePublicRepository {

        @Test
        @DisplayName("resolves the default branch for a public repository")
        void resolvesDefaultBranch() throws Exception {
            when(response.statusCode()).thenReturn(200);
            when(response.body()).thenReturn("{\"private\":false,\"size\":1024,\"default_branch\":\"main\"}");
            doReturn(response).when(httpClient).send(any(), any());

            GitHubRepositoryRef resolved = service.validatePublicRepository(
                    new GitHubRepositoryRef("spring-projects", "spring-petclinic", null));

            assertThat(resolved.ref()).isEqualTo("main");
            assertThat(resolved.displayName()).isEqualTo("spring-projects/spring-petclinic");
        }

        @Test
        @DisplayName("rejects a private repository")
        void rejectsPrivateRepository() throws Exception {
            when(response.statusCode()).thenReturn(200);
            when(response.body()).thenReturn("{\"private\":true,\"size\":1024,\"default_branch\":\"main\"}");
            doReturn(response).when(httpClient).send(any(), any());

            assertThatThrownBy(() -> service.validatePublicRepository(
                    new GitHubRepositoryRef("acme", "secret", null)))
                    .isInstanceOf(GithubImportException.class)
                    .hasMessageContaining("Private");
        }

        @Test
        @DisplayName("rejects a repository that returns 404")
        void rejectsMissingRepository() throws Exception {
            when(response.statusCode()).thenReturn(404);
            doReturn(response).when(httpClient).send(any(), any());

            assertThatThrownBy(() -> service.validatePublicRepository(
                    new GitHubRepositoryRef("acme", "missing", null)))
                    .isInstanceOf(GithubImportException.class)
                    .hasMessageContaining("not found");
        }
    }
}
