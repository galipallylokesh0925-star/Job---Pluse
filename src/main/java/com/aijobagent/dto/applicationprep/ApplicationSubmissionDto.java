package com.aijobagent.dto.applicationprep;

import com.aijobagent.model.ApplicationSubmissionStatus;

import java.time.Instant;

public record ApplicationSubmissionDto(
        String id,
        String jobId,
        String jobTitle,
        String company,
        ApplicationSubmissionStatus status,
        String provider,
        String providerReference,
        String applicationUrl,
        String message,
        String idempotencyKey,
        boolean alreadyProcessed,
        Instant createdAt,
        Instant completedAt
) {}
