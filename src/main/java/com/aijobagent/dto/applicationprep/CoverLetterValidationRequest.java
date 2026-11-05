package com.aijobagent.dto.applicationprep;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Cover letter text to review against candidate profile evidence")
public record CoverLetterValidationRequest(
        @NotBlank(message = "Cover letter must not be blank")
        @Size(max = 15000, message = "Cover letter must not exceed 15,000 characters")
        String coverLetter
) {}
