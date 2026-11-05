package com.aijobagent.dto.profile;

import com.aijobagent.model.Education;
import com.aijobagent.model.ProjectDetail;
import com.aijobagent.model.WorkExperience;

import java.util.ArrayList;
import java.util.List;

/**
 * Intermediate extraction container holding parsed resume data.
 */
public record ExtractedResumeData(
        String rawText,
        String fullName,
        String email,
        String phone,
        String location,
        String linkedInUrl,
        String githubUrl,
        String portfolioUrl,
        String summary,
        List<String> skills,
        List<Education> education,
        List<WorkExperience> experience,
        List<ProjectDetail> projects,
        List<String> certifications,
        int characterCount,
        String parserEngine
) {
    public ExtractedResumeData {
        if (skills == null) skills = new ArrayList<>();
        if (education == null) education = new ArrayList<>();
        if (experience == null) experience = new ArrayList<>();
        if (projects == null) projects = new ArrayList<>();
        if (certifications == null) certifications = new ArrayList<>();
    }
}
