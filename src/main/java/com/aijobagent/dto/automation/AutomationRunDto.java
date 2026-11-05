package com.aijobagent.dto.automation;

import java.time.Instant;

public record AutomationRunDto(
        String id,
        Instant startedAt,
        Instant completedAt,
        boolean success,
        int newJobs,
        int totalFetched,
        int duplicateJobs,
        int jobsEvaluated,
        int strongMatches,
        String message,
        String emailStatus
) {}
