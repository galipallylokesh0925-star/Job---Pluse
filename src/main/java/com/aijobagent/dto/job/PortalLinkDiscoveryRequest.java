package com.aijobagent.dto.job;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PortalLinkDiscoveryRequest(
        @NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 2048) String> portalUrls
) {}
