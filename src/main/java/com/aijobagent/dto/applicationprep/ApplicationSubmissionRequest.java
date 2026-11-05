package com.aijobagent.dto.applicationprep;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApplicationSubmissionRequest(
        @AssertTrue(message = "Explicit confirmation is required for every application")
        boolean confirmed,

        @NotBlank(message = "Cover letter must not be blank")
        @Size(max = 15000, message = "Cover letter must not exceed 15,000 characters")
        String coverLetter
) {}
