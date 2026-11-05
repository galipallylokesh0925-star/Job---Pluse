package com.aijobagent.dto.profile;

import com.aijobagent.model.Education;
import com.aijobagent.model.JobPreferences;
import com.aijobagent.model.ProjectDetail;
import com.aijobagent.model.WorkExperience;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;

import java.util.List;

/**
 * Request DTO for updating candidate profile details and application preferences.
 */
@Schema(description = "Payload to update candidate profile information and job search preferences")
public record CandidateProfileUpdateRequest(
        @Schema(description = "Full legal name", example = "Jane Doe")
        String fullName,

        @Email(message = "Email must be a valid email address format")
        @Schema(description = "Primary email address", example = "jane.doe@example.com")
        String email,

        @Schema(description = "Phone number", example = "+91 9876543210")
        String phone,

        @Schema(description = "Location / city", example = "Bengaluru, India")
        String location,

        @Schema(description = "LinkedIn URL", example = "https://linkedin.com/in/janedoe")
        String linkedInUrl,

        @Schema(description = "GitHub URL", example = "https://github.com/janedoe")
        String githubUrl,

        @Schema(description = "Portfolio URL", example = "https://janedoe.dev")
        String portfolioUrl,

        @Schema(description = "Professional summary")
        String summary,

        @Schema(description = "List of skills")
        List<String> skills,

        @Schema(description = "Education history")
        List<Education> education,

        @Schema(description = "Work experience history")
        List<WorkExperience> experience,

        @Schema(description = "Projects list")
        List<ProjectDetail> projects,

        @Schema(description = "Certifications")
        List<String> certifications,

        @Schema(description = "Updated job preferences")
        JobPreferences preferences
) {}
