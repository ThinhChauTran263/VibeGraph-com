package com.vibegraph.graph.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateProjectRequest {

    @Size(max = 200, message = "name must be at most 200 characters")
    private String name;

    @NotBlank(message = "rootPath is required")
    @Size(max = 4096, message = "rootPath is too long")
    private String rootPath;

    /** Optional flag; absent in the request body means "not requested". */
    private Boolean autoWatch;
}
