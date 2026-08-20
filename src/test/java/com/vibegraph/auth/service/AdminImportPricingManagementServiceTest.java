package com.vibegraph.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vibegraph.auth.domain.ImportPricingTier;
import com.vibegraph.auth.dto.AdminImportPricingResponse;
import com.vibegraph.auth.dto.AdminImportPricingUpdateRequest;
import com.vibegraph.auth.repository.ImportPricingTierRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminImportPricingManagementService")
class AdminImportPricingManagementServiceTest {

    @Mock ImportPricingTierRepository tierRepository;
    @Mock AuditService auditService;

    private AdminImportPricingManagementService newService() {
        return new AdminImportPricingManagementService(tierRepository, auditService);
    }

    private static AdminImportPricingUpdateRequest validRequest() {
        return new AdminImportPricingUpdateRequest(List.of(
                new AdminImportPricingUpdateRequest.Tier("SMALL", 100, 2),
                new AdminImportPricingUpdateRequest.Tier("MEDIUM", 500, 5),
                new AdminImportPricingUpdateRequest.Tier("LARGE", 2000, 15),
                new AdminImportPricingUpdateRequest.Tier("XLARGE", null, 40)));
    }

    @Test
    @DisplayName("listAll returns one entry per supported operation")
    void listAll_coversAllThreeMethods() {
        when(tierRepository.findByOperationCodeOrderBySortOrderAsc(any())).thenReturn(List.of());

        List<AdminImportPricingResponse> all = newService().listAll();

        assertThat(all).extracting(AdminImportPricingResponse::operationCode).containsExactly(
                "IMPORT_ARCHIVE", "IMPORT_GITHUB", "CLI_PUSH");
    }

    @Test
    @DisplayName("replace swaps the whole tier set and audits the change")
    void replace_persistsTiersInOrder() {
        when(tierRepository.save(any(ImportPricingTier.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(tierRepository.findByOperationCodeOrderBySortOrderAsc("IMPORT_ARCHIVE")).thenReturn(List.of(
                ImportPricingTier.builder().operationCode("IMPORT_ARCHIVE").tierCode("SMALL")
                        .maxFiles(100).credits(2).sortOrder(10).build()));

        AdminImportPricingResponse response = newService().replace("IMPORT_ARCHIVE", validRequest());

        verify(tierRepository).deleteByOperationCode("IMPORT_ARCHIVE");
        assertThat(response.operationCode()).isEqualTo("IMPORT_ARCHIVE");
        verify(auditService).recordCurrentUser(
                org.mockito.ArgumentMatchers.eq("IMPORT_PRICING_UPDATE"),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq("IMPORT_PRICING_TIER"),
                org.mockito.ArgumentMatchers.eq("IMPORT_ARCHIVE"),
                org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    @DisplayName("replace rejects non-import operation codes")
    void rejectUnsupportedOperation() {
        assertThatThrownBy(() -> newService().replace("MCP_TOOL_CALL", validRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MCP_TOOL_CALL");
    }

    @Test
    @DisplayName("replace rejects tiers whose bounds are not strictly ascending")
    void rejectNonAscendingBounds() {
        AdminImportPricingUpdateRequest bad = new AdminImportPricingUpdateRequest(List.of(
                new AdminImportPricingUpdateRequest.Tier("SMALL", 500, 2),
                new AdminImportPricingUpdateRequest.Tier("MEDIUM", 500, 5),
                new AdminImportPricingUpdateRequest.Tier("XLARGE", null, 40)));

        assertThatThrownBy(() -> newService().replace("IMPORT_ARCHIVE", bad))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ascending");
    }

    @Test
    @DisplayName("replace rejects an unlimited tier that is not the last one")
    void rejectUnlimitedNotLast() {
        AdminImportPricingUpdateRequest bad = new AdminImportPricingUpdateRequest(List.of(
                new AdminImportPricingUpdateRequest.Tier("SMALL", null, 2),
                new AdminImportPricingUpdateRequest.Tier("MEDIUM", 500, 5)));

        assertThatThrownBy(() -> newService().replace("IMPORT_GITHUB", bad))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unlimited");
    }

    @Test
    @DisplayName("replace rejects duplicate tier codes")
    void rejectDuplicateTierCodes() {
        AdminImportPricingUpdateRequest bad = new AdminImportPricingUpdateRequest(List.of(
                new AdminImportPricingUpdateRequest.Tier("small", 100, 2),
                new AdminImportPricingUpdateRequest.Tier("SMALL", 500, 5)));

        assertThatThrownBy(() -> newService().replace("CLI_PUSH", bad))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate");
    }
}
