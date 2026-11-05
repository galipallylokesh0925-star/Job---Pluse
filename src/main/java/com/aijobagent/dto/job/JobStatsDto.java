package com.aijobagent.dto.job;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * Summary metrics of indexed job postings for dashboard counters.
 */
@Schema(description = "Aggregated metrics of discovered job postings")
public record JobStatsDto(
        @Schema(description = "Total indexed jobs in database", example = "42")
        long totalJobs,

        @Schema(description = "Count of Remote roles", example = "24")
        long remoteJobs,

        @Schema(description = "Count of Hybrid roles", example = "12")
        long hybridJobs,

        @Schema(description = "Count of On-site roles", example = "6")
        long onSiteJobs,

        @Schema(description = "Breakdown by source connector")
        Map<String, Long> countBySource,

        @Schema(description = "Breakdown by lifecycle status")
        Map<String, Long> countByStatus
) {}
