package com.aijobagent.dto.job;

public record PortalLinkDiscoveryItem(
        String portalUrl,
        String source,
        String status,
        String message,
        int totalFetched,
        int newJobsSaved,
        int duplicatesSkipped
) {}
