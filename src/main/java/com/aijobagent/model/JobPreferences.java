package com.aijobagent.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;

/**
 * Job search preferences configured by the candidate.
 * Governs automated matching criteria and search constraints.
 */
@Schema(description = "Candidate job search preferences and constraints")
public class JobPreferences {

    @Schema(description = "Target job titles", example = "[\"Java Developer\", \"Software Engineer\"]")
    private List<String> targetRoles = new ArrayList<>();

    @Schema(description = "Preferred work modes (e.g., REMOTE, HYBRID, ON_SITE)", example = "[\"REMOTE\", \"HYBRID\"]")
    private List<String> preferredWorkModes = new ArrayList<>();

    @Schema(description = "Preferred locations/cities", example = "[\"Bengaluru\", \"Pune\", \"Remote\"]")
    private List<String> preferredLocations = new ArrayList<>();

    @Schema(description = "Experience level tier", example = "ENTRY_LEVEL_FRESHER")
    private String experienceLevel = "ENTRY_LEVEL_FRESHER";

    @Schema(description = "Target employment types", example = "[\"INTERNSHIP\", \"FULL_TIME\"]")
    private List<String> employmentTypes = new ArrayList<>();

    @Schema(description = "Max applications to submit per day for rate-safety", example = "20")
    private int maxDailyApplications = 20;

    @Schema(description = "Expected minimum compensation / stipend in INR", example = "500000")
    private Double expectedSalaryMin;

    @Schema(description = "Whether the candidate can join immediately without serving long notice", example = "true")
    private boolean immediateJoiner = true;

    public JobPreferences() {}

    public JobPreferences(List<String> targetRoles, List<String> preferredWorkModes, List<String> preferredLocations,
                          String experienceLevel, List<String> employmentTypes, int maxDailyApplications,
                          Double expectedSalaryMin, boolean immediateJoiner) {
        this.targetRoles = targetRoles != null ? targetRoles : new ArrayList<>();
        this.preferredWorkModes = preferredWorkModes != null ? preferredWorkModes : new ArrayList<>();
        this.preferredLocations = preferredLocations != null ? preferredLocations : new ArrayList<>();
        this.experienceLevel = experienceLevel != null ? experienceLevel : "ENTRY_LEVEL_FRESHER";
        this.employmentTypes = employmentTypes != null ? employmentTypes : new ArrayList<>();
        this.maxDailyApplications = maxDailyApplications > 0 ? maxDailyApplications : 20;
        this.expectedSalaryMin = expectedSalaryMin;
        this.immediateJoiner = immediateJoiner;
    }

    public List<String> getTargetRoles() {
        return targetRoles;
    }

    public void setTargetRoles(List<String> targetRoles) {
        this.targetRoles = targetRoles;
    }

    public List<String> getPreferredWorkModes() {
        return preferredWorkModes;
    }

    public void setPreferredWorkModes(List<String> preferredWorkModes) {
        this.preferredWorkModes = preferredWorkModes;
    }

    public List<String> getPreferredLocations() {
        return preferredLocations;
    }

    public void setPreferredLocations(List<String> preferredLocations) {
        this.preferredLocations = preferredLocations;
    }

    public String getExperienceLevel() {
        return experienceLevel;
    }

    public void setExperienceLevel(String experienceLevel) {
        this.experienceLevel = experienceLevel;
    }

    public List<String> getEmploymentTypes() {
        return employmentTypes;
    }

    public void setEmploymentTypes(List<String> employmentTypes) {
        this.employmentTypes = employmentTypes;
    }

    public int getMaxDailyApplications() {
        return maxDailyApplications;
    }

    public void setMaxDailyApplications(int maxDailyApplications) {
        this.maxDailyApplications = maxDailyApplications;
    }

    public Double getExpectedSalaryMin() {
        return expectedSalaryMin;
    }

    public void setExpectedSalaryMin(Double expectedSalaryMin) {
        this.expectedSalaryMin = expectedSalaryMin;
    }

    public boolean isImmediateJoiner() {
        return immediateJoiner;
    }

    public void setImmediateJoiner(boolean immediateJoiner) {
        this.immediateJoiner = immediateJoiner;
    }
}
