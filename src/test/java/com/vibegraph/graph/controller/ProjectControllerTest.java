package com.vibegraph.graph.controller;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for ProjectController REST endpoints.
 *
 * Setup with @SpringBootTest + @AutoConfigureMockMvc when ready:
 *
 *   @SpringBootTest
 *   @AutoConfigureMockMvc
 *   class ProjectControllerTest {
 *       @Autowired MockMvc mockMvc;
 *       ...
 *   }
 *
 * Run: mvn test -Dtest=ProjectControllerTest
 */
@DisplayName("ProjectController")
class ProjectControllerTest {

    @Test
    @Disabled("Chờ ProjectController + MockMvc setup")
    @DisplayName("POST /api/projects should create project and return 201")
    void shouldCreateProject() {
        // mockMvc.perform(post("/api/projects")
        //     .contentType(MediaType.APPLICATION_JSON)
        //     .content("{\"name\":\"test\",\"path\":\"/tmp/test\"}"))
        //     .andExpect(status().isCreated())
        //     .andExpect(jsonPath("$.data.name").value("test"));
    }

    @Test
    @Disabled("Chờ ProjectController + MockMvc setup")
    @DisplayName("GET /api/projects should return list of projects")
    void shouldListProjects() {
        // mockMvc.perform(get("/api/projects"))
        //     .andExpect(status().isOk())
        //     .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @Disabled("Chờ ProjectController + MockMvc setup")
    @DisplayName("GET /api/projects/{id} should return 404 for unknown id")
    void shouldReturn404ForUnknownProject() {
        // mockMvc.perform(get("/api/projects/non-existent"))
        //     .andExpect(status().isNotFound());
    }

    @Test
    @Disabled("Chờ ProjectController + MockMvc setup")
    @DisplayName("POST /api/projects with invalid path should return 400")
    void shouldReturn400ForInvalidPath() {
        // mockMvc.perform(post("/api/projects")
        //     .contentType(MediaType.APPLICATION_JSON)
        //     .content("{\"name\":\"test\",\"path\":\"\"}"))
        //     .andExpect(status().isBadRequest());
    }

    @Test
    @Disabled("Chờ ProjectController + MockMvc setup")
    @DisplayName("POST /api/projects/{id}/analyze should trigger analysis")
    void shouldTriggerAnalysis() {
        // 1. Create project
        // 2. Trigger analyze
        // 3. Assert 202 Accepted (async) or 200 OK (sync)
    }
}
