package com.aijobagent.dto.job;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

/**
 * Result payload returned following an automated job discovery scan.
 */
@Schema(description = "Outcome and metrics from an automated multi-source discovery run")
public record DiscoveryRunResult(
        @Schema(description = "Whether the discovery run executed successfully", example = "true")
        boolean success,

        @Schema(description = "Summary message", example = "Discovery scan completed across 3 connectors. Found 18 new jobs.")
        String message,

        @Schema(description = "Scan start timestamp")
        Instant runStartedAt,

        @Schema(description = "Scan completion timestamp")
        Instant runCompletedAt,

        @Schema(description = "Total execution latency in milliseconds", example = "1240")
        long durationMs,

        @Schema(description = "Total postings retrieved across all connectors before deduplication", example = "35")
        int totalFetched,

        @Schema(description = "New unique job postings saved to MongoDB", example = "18")
        int newJobsSaved,

        @Schema(description = "Existing duplicate postings skipped", example = "17")
        int duplicateJobsSkipped,

        @Schema(description = "Count of jobs fetched per connector source")
        Map<String, Integer> sourceBreakdown
) {}
