package com.aijobagent.model;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * MongoDB Document representing a Candidate Profile.
 *
 * Stores parsed resume information, contact points, verified skills, and search preferences
 * used for AI matching and automated submissions.
 */
@Document(collection = "candidate_profiles")
@Schema(description = "Candidate Profile document stored in MongoDB")
public class CandidateProfile {

    @Id
    @Schema(description = "Unique MongoDB ObjectId hex string", example = "671e3f89a9f4c3b2e1045a12")
    private String id;

    @Schema(description = "Full legal name of candidate", example = "Jane Doe")
    private String fullName;

    @Indexed
    @Schema(description = "Primary contact email address", example = "jane.doe@example.com")
    private String email;

    @Schema(description = "Primary contact phone number", example = "+91 9876543210")
    private String phone;

    @Schema(description = "Candidate current residential location / city", example = "Bengaluru, India")
    private String location;

    @Schema(description = "LinkedIn public profile URL", example = "https://linkedin.com/in/janedoe")
    private String linkedInUrl;

    @Schema(description = "GitHub profile URL", example = "https://github.com/janedoe")
    private String githubUrl;

    @Schema(description = "Portfolio / personal website URL", example = "https://janedoe.dev")
    private String portfolioUrl;

    @Schema(description = "Executive professional summary or resume headline")
    private String summary;

    @Schema(description = "Catalog of verified technical and soft skills", example = "[\"Java\", \"Spring Boot\", \"MongoDB\", \"REST APIs\", \"Docker\"]")
    private List<String> skills = new ArrayList<>();

    @Schema(description = "Educational qualifications")
    private List<Education> education = new ArrayList<>();

    @Schema(description = "Professional experience and internships")
    private List<WorkExperience> experience = new ArrayList<>();

    @Schema(description = "Highlighted software engineering projects")
    private List<ProjectDetail> projects = new ArrayList<>();

    @Schema(description = "Certifications and credentials", example = "[\"AWS Certified Cloud Practitioner\", \"Oracle Certified Java SE 17\"]")
    private List<String> certifications = new ArrayList<>();

    @Schema(description = "Job search and application preferences")
    private JobPreferences preferences = new JobPreferences();

    @Schema(description = "Metadata regarding the latest uploaded resume")
    private ResumeMetadata resumeMetadata;

    @Schema(description = "Full raw plain text extracted from the candidate resume for AI context ingestion")
    private String rawResumeText;

    @CreatedDate
    @Schema(description = "UTC timestamp when the profile document was created")
    private Instant createdAt;

    @LastModifiedDate
    @Schema(description = "UTC timestamp when the profile document was last modified")
    private Instant updatedAt;

    public CandidateProfile() {}

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getLinkedInUrl() {
        return linkedInUrl;
    }

    public void setLinkedInUrl(String linkedInUrl) {
        this.linkedInUrl = linkedInUrl;
    }

    public String getGithubUrl() {
        return githubUrl;
    }

    public void setGithubUrl(String githubUrl) {
        this.githubUrl = githubUrl;
    }

    public String getPortfolioUrl() {
        return portfolioUrl;
    }

    public void setPortfolioUrl(String portfolioUrl) {
        this.portfolioUrl = portfolioUrl;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<String> getSkills() {
        return skills;
    }

    public void setSkills(List<String> skills) {
        this.skills = skills != null ? skills : new ArrayList<>();
    }

    public List<Education> getEducation() {
        return education;
    }

    public void setEducation(List<Education> education) {
        this.education = education != null ? education : new ArrayList<>();
    }

    public List<WorkExperience> getExperience() {
        return experience;
    }

    public void setExperience(List<WorkExperience> experience) {
        this.experience = experience != null ? experience : new ArrayList<>();
    }

    public List<ProjectDetail> getProjects() {
        return projects;
    }

    public void setProjects(List<ProjectDetail> projects) {
        this.projects = projects != null ? projects : new ArrayList<>();
    }

    public List<String> getCertifications() {
        return certifications;
    }

    public void setCertifications(List<String> certifications) {
        this.certifications = certifications != null ? certifications : new ArrayList<>();
    }

    public JobPreferences getPreferences() {
        return preferences;
    }

    public void setPreferences(JobPreferences preferences) {
        this.preferences = preferences != null ? preferences : new JobPreferences();
    }

    public ResumeMetadata getResumeMetadata() {
        return resumeMetadata;
    }

    public void setResumeMetadata(ResumeMetadata resumeMetadata) {
        this.resumeMetadata = resumeMetadata;
    }

    public String getRawResumeText() {
        return rawResumeText;
    }

    public void setRawResumeText(String rawResumeText) {
        this.rawResumeText = rawResumeText;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
