package com.aijobagent.dto.applicationprep;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Profile field review result")
public record ProfileReviewItemDto(
        String field,
        String status,
        String message
) {}
