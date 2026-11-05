package com.aijobagent.dto.matching;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Filter parameters for querying and ranking job matches.
 */
@Schema(description = "Filter and pagination criteria for ranked job matches")
public record MatchScoreFilter(
        @Schema(description = "Minimum overall score threshold (0-100)", example = "60")
        Integer minScore,

        @Schema(description = "Recommendation tier (STRONG_MATCH, MODERATE_MATCH, WEAK_MATCH, or ALL)", example = "STRONG_MATCH")
        String tier,

        @Schema(description = "Filter only jobs that satisfied all hard criteria", example = "true")
        Boolean hardCriteriaOnly,

        @Schema(description = "Search keyword across job title or company", example = "Java")
        String keyword,

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
