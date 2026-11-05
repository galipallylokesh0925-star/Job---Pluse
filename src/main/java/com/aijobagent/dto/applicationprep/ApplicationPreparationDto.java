package com.aijobagent.dto.applicationprep;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Evidence-grounded application preparation for a job posting")
public record ApplicationPreparationDto(
        String jobId,
        String jobTitle,
        String company,
        String applyUrl,
        String coverLetter,
        String generationMethod,
        List<String> evidence,
        ProfileReviewDto profileReview,
        TruthfulnessValidationDto truthfulness
) {}
