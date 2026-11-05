package com.aijobagent.dto.applicationprep;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Deterministic review of potentially unsupported cover-letter claims")
public record TruthfulnessValidationDto(
        boolean allClaimsSupported,
        int checkedClaims,
        List<String> unsupportedClaims,
        String disclaimer
) {}
