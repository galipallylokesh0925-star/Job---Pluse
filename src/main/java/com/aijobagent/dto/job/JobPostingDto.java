package com.aijobagent.dto.job;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Public response DTO for a normalized Job Posting.
 */
@Schema(description = "Job posting representation payload")
public record JobPostingDto(
        @Schema(description = "Job document ID", example = "671e4100a9f4c3b2e1045a99")
        String id,

        @Schema(description = "Standardized job title", example = "Junior Java Developer")
        String title,

        @Schema(description = "Company or employer name", example = "Infosys")
        String company,

        @Schema(description = "Location / city", example = "Bengaluru, India")
        String location,

        @Schema(description = "Work mode (REMOTE, HYBRID, ON_SITE)", example = "HYBRID")
        String workMode,

        @Schema(description = "Employment type (FULL_TIME, INTERNSHIP)", example = "FULL_TIME")
        String employmentType,

        @Schema(description = "Experience tier", example = "ENTRY_LEVEL_FRESHER")
        String experienceLevel,

        @Schema(description = "Country", example = "India")
        String country,

        @Schema(description = "Job description summary")
        String description,

        @Schema(description = "Catalog of required skills")
        List<String> requiredSkills,

        @Schema(description = "Minimum compensation", example = "600000")
        Double salaryMin,

        @Schema(description = "Maximum compensation", example = "900000")
        Double salaryMax,

        @Schema(description = "Salary currency", example = "INR")
        String salaryCurrency,

        @Schema(description = "Direct application URL", example = "https://careers.example.com/apply/123")
        String applyUrl,

        @Schema(description = "Discovery source connector", example = "ENTERPRISE_PORTAL")
        String source,

        @Schema(description = "Original publication timestamp")
        Instant postedAt,

        @Schema(description = "Agent discovery timestamp")
        Instant discoveredAt,

        @Schema(description = "Lifecycle status", example = "DISCOVERED")
        String status
) {}
