package com.aijobagent.dto.job;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record JobImportRequest(
        @NotNull JobAlertSource source,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 200) String company,
        @NotBlank @Size(max = 2048) String applyUrl,
        @Size(max = 250) String location,
        @Pattern(regexp = "(?i)REMOTE|HYBRID|ON_SITE|UNKNOWN")
        String workMode,
        @Size(max = 15000) String description
) {}
