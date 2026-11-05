package com.aijobagent.dto.automation;

import com.aijobagent.model.AutomationStatus;

import java.time.Instant;

public record AutomationStatusDto(
        AutomationStatus status,
        Instant startedAt,
        Instant lastRunAt,
        Instant nextRunAt,
        String lastMessage,
        int lastNewJobs,
        long intervalMinutes,
        boolean emailConfigured
) {}
