package com.aijobagent.dto.automation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PortalLinksConfigRequest(
        @Size(max = 20) List<@NotBlank @Size(max = 2048) String> portalUrls
) {}
