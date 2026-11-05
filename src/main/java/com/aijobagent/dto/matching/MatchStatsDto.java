package com.aijobagent.dto.matching;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Aggregated statistics of candidate job match evaluations.
 */
@Schema(description = "Aggregated metrics of qualification match distributions")
public record MatchStatsDto(
        @Schema(description = "Total jobs evaluated for the candidate", example = "24")
        long totalEvaluated,

        @Schema(description = "Count of Strong Matches (score >= 75)", example = "14")
        long strongMatches,

        @Schema(description = "Count of Moderate Matches (50-74)", example = "7")
        long moderateMatches,

        @Schema(description = "Count of Weak Matches (<50)", example = "3")
        long weakMatches,

        @Schema(description = "Count of jobs passing hard deterministic criteria", example = "21")
        long hardCriteriaPassed,

        @Schema(description = "Average qualification score", example = "76.5")
        double averageScore
) {}
