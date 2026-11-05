package com.aijobagent.dto.job;

public record JobImportResult(
        boolean success,
        boolean duplicate,
        String message,
        JobPostingDto job
) {}
