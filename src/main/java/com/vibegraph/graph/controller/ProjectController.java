package com.vibegraph.graph.controller;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.web.ApiKeyRequestContextAccessor;
import com.vibegraph.auth.service.AccountSettingsService;
import com.vibegraph.auth.service.CreditBalanceService;
import com.vibegraph.auth.service.CreditPricingService;
import com.vibegraph.auth.service.FeatureGateService;
import com.vibegraph.auth.service.ProjectUsageService;
import com.vibegraph.common.dto.response.ApiResponse;
import com.vibegraph.common.exception.ForbiddenException;
import com.vibegraph.common.ownership.ProjectDeletionOrchestrator;
import com.vibegraph.common.ownership.ProjectTrashService;
import com.vibegraph.common.ownership.ProjectOwnershipGuard;
import com.vibegraph.common.ownership.ProjectOwnershipQuery;
import com.vibegraph.common.ownership.ProjectOwnershipRegistrar;
import com.vibegraph.graph.dto.TrashedProjectResponse;
import com.vibegraph.graph.dto.request.CreateProjectRequest;
import com.vibegraph.graph.dto.request.CliRepositoryCreateRequest;
import com.vibegraph.graph.dto.response.CliRepositorySetupResponse;
import com.vibegraph.graph.dto.response.ProjectResponse;
import com.vibegraph.graph.importer.JavaFileCounter;
import com.vibegraph.graph.service.CliRepositoryService;
import com.vibegraph.graph.service.ProjectAnalysisScheduler;
import com.vibegraph.graph.service.ProjectService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor(onConstructor_ = @org.springframework.beans.factory.annotation.Autowired)
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectAnalysisScheduler projectAnalysisScheduler;
    private final ProjectOwnershipRegistrar ownershipRegistrar;
    private final ProjectOwnershipGuard ownershipGuard;
    private final ProjectOwnershipQuery ownershipQuery;
    private final ProjectDeletionOrchestrator deletionOrchestrator;
    private final ProjectTrashService trashService;
    private final CurrentUser currentUser;
    private final AccountSettingsService accountSettingsService;
    private final FeatureGateService featureGateService;
    private final ProjectUsageService projectUsageService;
    private final CliRepositoryService cliRepositoryService;
    private final CreditPricingService creditPricingService;
    private final CreditBalanceService creditBalanceService;
    private final ApiKeyRequestContextAccessor apiKeyRequestContextAccessor;

    @PostMapping
    public ResponseEntity<ApiResponse<ProjectResponse>> create(@Valid @RequestBody CreateProjectRequest request) {
        // Project is created first; ownership is recorded synchronously before returning success,
        // so a validation failure inside createProject leaves no ownership row.
        ProjectResponse project = projectService.createProject(request);
        ownershipRegistrar.registerLocal(project.getId(), project.getName());
        try {
            projectUsageService.recordImport(project.getId(), currentUser.id(), 0L);
        } catch (RuntimeException ex) {
            try {
                // Rollback of a half-finished create must remove the project outright: trashing it
                // would leave a project the user never successfully created sitting in their bin.
                deletionOrchestrator.purge(project.getId());
            } catch (RuntimeException cleanupFailure) {
                ex.addSuppressed(cleanupFailure);
            }
            throw ex;
        }
        return ResponseEntity.ok(ApiResponse.success(project));
    }

    @PostMapping("/cli-setup")
    public ResponseEntity<ApiResponse<CliRepositorySetupResponse>> createCliRepository(
            @Valid @RequestBody(required = false) CliRepositoryCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(cliRepositoryService.create(request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProjectResponse>>> list() {
        // Owner-scoped: Postgres (projects.owner_id) is the source of truth for which projects the
        // caller may see. We take the underlying ProjectService listing (in-memory + Neo4j metadata)
        // unchanged and keep only those whose id is owned by the current user.
        Set<String> ownedIds = Set.copyOf(ownershipQuery.ownedProjectIds());
        List<ProjectResponse> owned = projectService.listProjects().stream()
                .filter(project -> ownedIds.contains(project.getId()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(owned));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProjectResponse>> get(@PathVariable String id) {
        ownershipGuard.assertOwner(id);
        return ResponseEntity.ok(ApiResponse.success(projectService.getProject(id)));
    }

    /** Returns the project bound to the caller's project API key. */
    @GetMapping("/current")
    public ResponseEntity<ApiResponse<ProjectResponse>> getCurrent() {
        return ResponseEntity.ok(ApiResponse.success(getOwnedProject(currentApiKeyProjectId())));
    }

    /**
     * Accepts the analysis and runs it in the background (H8): the heavy parse + graph upsert
     * no longer occupies a Tomcat thread (minutes on large repos, reverse-proxy timeouts, no
     * cancellation). Progress arrives over WebSocket {@code /topic/projects/{id}/status}.
     */
    @PostMapping("/{id}/analyze")
    public ResponseEntity<Void> analyze(@PathVariable String id) {
        return analyzeProject(id, "WEB");
    }

    /** Queues analysis for the project bound to the caller's project API key. */
    @PostMapping("/current/analyze")
    public ResponseEntity<Void> analyzeCurrent() {
        return analyzeProject(currentApiKeyProjectId(), "CLI");
    }

    private ResponseEntity<Void> analyzeProject(String id, String source) {
        ownershipGuard.assertOwner(id);
        featureGateService.assertEnabled(FeatureGateService.PROJECT_ANALYZE);
        UUID userId = currentUser.id();
        accountSettingsService.assertNotBlocked(userId);

        // Existence check up front so an unknown id still gets a 404, not an accepted no-op.
        ProjectResponse project = projectService.getProject(id);

        // Pre-charge by the project's .java file count on the request thread, so an
        // exhausted balance surfaces as 402 here instead of a failed background job.
        // Re-analysis uses the flat per-file rule (credit_pricing_rules), not the
        // import tier table.
        int fileCount = JavaFileCounter.count(
                project.getRootPath() != null ? Path.of(project.getRootPath()) : null);
        long requiredCredits = creditPricingService.calculateCredits("PROJECT_ANALYZE", fileCount, 0);
        creditBalanceService.assertCreditsAvailable(userId, requiredCredits);
        creditBalanceService.deductCredits(userId, requiredCredits, source, "PROJECT_ANALYZE", id);

        projectAnalysisScheduler.schedule(id);

        return ResponseEntity.accepted().build();
    }

    private ProjectResponse getOwnedProject(String id) {
        ownershipGuard.assertOwner(id);
        return projectService.getProject(id);
    }

    private String currentApiKeyProjectId() {
        return apiKeyRequestContextAccessor.current()
                .map(context -> context.projectId())
                .filter(id -> id != null && !id.isBlank())
                .orElseThrow(() -> new ForbiddenException("A project-bound API key is required."));
    }

    /**
     * Moves a project to trash. The graph and the extracted sources are kept for the retention
     * window so the owner can restore, and the project disappears from every listing meanwhile.
     * The permanent delete happens on the retention sweep, or immediately via {@code /purge}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        ownershipGuard.assertOwner(id);
        deletionOrchestrator.moveToTrash(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/trash")
    public ResponseEntity<ApiResponse<List<TrashedProjectResponse>>> trash() {
        return ResponseEntity.ok(ApiResponse.success(trashService.listTrash()));
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<Void> restore(@PathVariable String id) {
        // Ownership is checked inside the service: the usual guard treats a trashed project as
        // missing, which is exactly what we need to undo here.
        trashService.restore(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Permanently deletes a trashed project without waiting for the retention sweep. Irreversible:
     * removes the graph, the ownership row and the extracted sources.
     */
    @DeleteMapping("/{id}/purge")
    public ResponseEntity<Void> purge(@PathVariable String id) {
        trashService.purge(id);
        return ResponseEntity.noContent().build();
    }
}
