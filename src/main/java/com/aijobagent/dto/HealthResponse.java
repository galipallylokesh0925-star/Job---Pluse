package com.aijobagent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

/**
 * HealthResponse DTO represented as an immutable Java Record.
 *
 * Java Records (introduced in Java 16+, standard in Java 21 LTS) are concise data carriers
 * that automatically generate constructor, getters, equals, hashCode, and toString.
 */
@Schema(description = "Health status payload of the application and connected systems")
public record HealthResponse(
        @Schema(description = "Overall health status", example = "UP")
        String status,

        @Schema(description = "Detailed status message", example = "AI Job Application Agent is healthy and operational")
        String message,

        @Schema(description = "Current server timestamp in UTC", example = "2026-10-28T18:30:00Z")
        Instant timestamp,

        @Schema(description = "Application name", example = "ai-job-application-agent")
        String service,

        @Schema(description = "Application version", example = "1.0.0")
        String version,

        @Schema(description = "Database connectivity status", example = "UP")
        String database,

        @Schema(description = "Runtime environment metadata")
        Map<String, String> systemInfo
) {}
