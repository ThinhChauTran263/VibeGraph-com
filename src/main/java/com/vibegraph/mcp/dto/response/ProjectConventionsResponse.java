package com.vibegraph.mcp.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for {@code get_project_conventions}: durable, curated repo conventions and known
 * facts parsed from the AI memory markdown file. Read-only; never returns secrets.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectConventionsResponse {
    private boolean available;
    private String source;
    private List<Section> sections;
    private List<String> warnings;
    private List<String> notes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Section {
        private String title;
        private List<String> items;
    }
}
