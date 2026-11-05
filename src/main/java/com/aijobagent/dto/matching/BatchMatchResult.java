package com.aijobagent.dto.matching;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Outcome payload returned after executing batch qualification matching across all discovered jobs.
 */
@Schema(description = "Summary metrics from a batch AI matching run across discovered opportunities")
public record BatchMatchResult(
        @Schema(description = "Whether the batch matching run completed successfully", example = "true")
        boolean success,

        @Schema(description = "Execution message summary", example = "Evaluated 18 jobs. Found 12 strong matches.")
        String message,

        @Schema(description = "Total jobs evaluated", example = "18")
        int totalJobsEvaluated,

        @Schema(description = "Count of Strong Matches (score >= 75)", example = "12")
        int strongMatchesCount,

        @Schema(description = "Count of Moderate Matches (50 <= score < 75)", example = "4")
        int moderateMatchesCount,

        @Schema(description = "Count of Weak Matches (score < 50)", example = "2")
        int weakMatchesCount,

        @Schema(description = "Count of jobs satisfying all hard criteria", example = "16")
        int hardCriteriaPassCount,

        @Schema(description = "Count of jobs failing hard constraints", example = "2")
        int hardCriteriaFailCount,

        @Schema(description = "Average score across evaluated jobs", example = "78.4")
        double averageScore,

        @Schema(description = "Execution latency in milliseconds", example = "350")
        long durationMs
) {}
