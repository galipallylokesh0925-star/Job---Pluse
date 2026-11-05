package com.aijobagent.dto.matching;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Public response DTO representing a job qualification match analysis.
 */
@Schema(description = "Detailed match score and skill gap analysis for a job")
public record JobMatchDto(
        @Schema(description = "Match result ID", example = "671e4500a9f4c3b2e1045b10")
        String id,

        @Schema(description = "Referenced Job Posting ID", example = "671e4100a9f4c3b2e1045a99")
        String jobId,

        @Schema(description = "Job title", example = "Junior Java Developer")
        String jobTitle,

        @Schema(description = "Company name", example = "Tata Consultancy Services (TCS)")
        String company,

        @Schema(description = "Location", example = "Bengaluru, India")
        String location,

        @Schema(description = "Work mode", example = "HYBRID")
        String workMode,

        @Schema(description = "Application URL", example = "https://ibegin.tcs.com/careers/job/10101")
        String applyUrl,

        @Schema(description = "Overall compatibility score (0-100)", example = "88")
        int overallScore,

        @Schema(description = "Skills compatibility score (0-100)", example = "92")
        int skillScore,

        @Schema(description = "Experience compatibility score (0-100)", example = "85")
        int experienceScore,

        @Schema(description = "Education compatibility score (0-100)", example = "90")
        int educationScore,

        @Schema(description = "Preferences alignment score (0-100)", example = "80")
        int preferenceScore,

        @Schema(description = "Whether deterministic hard constraints were satisfied", example = "true")
        boolean hardCriteriaMet,

        @Schema(description = "Specific violations if any hard criteria failed")
        List<String> hardCriteriaViolations,

        @Schema(description = "Recommendation tier (STRONG_MATCH, MODERATE_MATCH, WEAK_MATCH)", example = "STRONG_MATCH")
        String recommendationTier,

        @Schema(description = "Required skills present in candidate profile")
        List<String> matchedSkills,

        @Schema(description = "Required skills missing from candidate profile")
        List<String> missingSkills,

        @Schema(description = "Bonus candidate skills")
        List<String> bonusSkills,

        @Schema(description = "Qualitative assessment summary")
        String qualitativeSummary,

        @Schema(description = "Interview preparation and highlight tips")
        List<String> preparationTips,

        @Schema(description = "Evaluation timestamp")
        Instant matchedAt,

        @Schema(description = "Evaluation engine utilized", example = "Deterministic Semantic Matcher v1.0")
        String matchEngine
) {}
