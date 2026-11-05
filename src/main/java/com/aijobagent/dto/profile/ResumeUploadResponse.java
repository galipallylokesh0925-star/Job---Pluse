package com.aijobagent.dto.profile;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response payload returned after resume upload and extraction.
 */
@Schema(description = "Result of resume upload and AI/rule parsing")
public record ResumeUploadResponse(
        @Schema(description = "Whether parsing succeeded", example = "true")
        boolean success,

        @Schema(description = "Status or confirmation message", example = "Resume successfully parsed and profile updated.")
        String message,

        @Schema(description = "Uploaded file name", example = "Jane_Doe_Resume.pdf")
        String fileName,

        @Schema(description = "Engine used to extract document", example = "Apache PDFBox 3.0.3")
        String parserEngine,

        @Schema(description = "Total characters extracted from resume", example = "3540")
        int extractedCharacterCount,

        @Schema(description = "Total skills identified", example = "12")
        int extractedSkillsCount,

        @Schema(description = "Updated candidate profile")
        CandidateProfileDto profile
) {}
