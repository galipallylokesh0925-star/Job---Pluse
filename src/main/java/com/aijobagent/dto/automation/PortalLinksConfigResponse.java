package com.aijobagent.dto.automation;

import java.util.List;

public record PortalLinksConfigResponse(
        List<String> savedPortalUrls,
        List<String> unsupportedPortalUrls
) {}
