package com.aijobagent.dto.profile;

import com.aijobagent.model.Education;
import com.aijobagent.model.JobPreferences;
import com.aijobagent.model.ProjectDetail;
import com.aijobagent.model.ResumeMetadata;
import com.aijobagent.model.WorkExperience;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Public response DTO for Candidate Profile.
 */
@Schema(description = "Candidate profile representation payload")
public record CandidateProfileDto(
        @Schema(description = "Profile ID", example = "671e3f89a9f4c3b2e1045a12")
        String id,

        @Schema(description = "Full name", example = "Jane Doe")
        String fullName,

        @Schema(description = "Email address", example = "jane.doe@example.com")
        String email,

        @Schema(description = "Phone number", example = "+91 9876543210")
        String phone,

        @Schema(description = "Current location", example = "Bengaluru, India")
        String location,

        @Schema(description = "LinkedIn URL", example = "https://linkedin.com/in/janedoe")
        String linkedInUrl,

        @Schema(description = "GitHub URL", example = "https://github.com/janedoe")
        String githubUrl,

        @Schema(description = "Portfolio URL", example = "https://janedoe.dev")
        String portfolioUrl,

        @Schema(description = "Executive summary")
        String summary,

        @Schema(description = "List of verified skills")
        List<String> skills,

        @Schema(description = "Education history")
        List<Education> education,

        @Schema(description = "Work and internship experience")
        List<WorkExperience> experience,

        @Schema(description = "Key technical projects")
        List<ProjectDetail> projects,

        @Schema(description = "Professional certifications")
        List<String> certifications,

        @Schema(description = "Configured search preferences")
        JobPreferences preferences,

        @Schema(description = "Resume upload metadata")
        ResumeMetadata resumeMetadata,

        @Schema(description = "Indicates whether a resume has been uploaded and parsed", example = "true")
        boolean hasResume,

        @Schema(description = "Creation timestamp")
        Instant createdAt,

        @Schema(description = "Last update timestamp")
        Instant updatedAt
) {}
