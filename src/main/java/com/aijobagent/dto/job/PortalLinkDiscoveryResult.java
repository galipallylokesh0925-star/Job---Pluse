package com.aijobagent.dto.job;

import java.time.Instant;
import java.util.List;

public record PortalLinkDiscoveryResult(
        Instant runStartedAt,
        Instant runCompletedAt,
        long durationMs,
        int totalFetched,
        int newJobsSaved,
        int duplicatesSkipped,
        List<PortalLinkDiscoveryItem> results
) {}
