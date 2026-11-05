package com.aijobagent.dto.applicationprep;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Candidate profile completeness and resume-evidence review")
public record ProfileReviewDto(
        int completenessScore,
        int presentFields,
        int reviewedFields,
        boolean resumeAvailable,
        String overallStatus,
        List<ProfileReviewItemDto> items
) {}
