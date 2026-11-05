package com.aijobagent.dto.job;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Filter and pagination parameters for searching discovered job postings.
 */
@Schema(description = "Criteria parameters for searching and filtering job postings")
public record JobSearchCriteria(
        @Schema(description = "Free-text search keyword matching title, company, or skills", example = "Java")
        String keyword,

        @Schema(description = "Filter by work mode (REMOTE, HYBRID, ON_SITE)", example = "REMOTE")
        String workMode,

        @Schema(description = "Filter by connector source", example = "REMOTE_OK")
        String source,

        @Schema(description = "Filter by lifecycle status", example = "DISCOVERED")
        String status,

        @Schema(description = "Filter by location / city", example = "Bengaluru")
        String location,

        @Schema(description = "Page number (0-indexed)", example = "0")
        Integer page,

        @Schema(description = "Page size limit", example = "20")
        Integer size
) {
    public int getPage() {
        return (page != null && page >= 0) ? page : 0;
    }

    public int getSize() {
        return (size != null && size > 0 && size <= 100) ? size : 20;
    }
}
