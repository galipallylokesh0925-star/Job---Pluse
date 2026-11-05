package com.aijobagent.model;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * MongoDB Document representing a normalized Job Posting.
 *
 * Indexed with a unique deduplication hash to prevent duplicate entries across runs and sources.
 */
@Document(collection = "job_postings")
@Schema(description = "Normalized job posting discovered from multi-source connectors")
public class JobPosting {

    @Id
    @Schema(description = "Unique MongoDB ObjectId hex string", example = "671e4100a9f4c3b2e1045a99")
    private String id;

    @Indexed(unique = true)
    @Schema(description = "SHA-256 deduplication hash computed from source, company, title, and URL", example = "a1b2c3d4...")
    private String dedupHash;

    @Schema(description = "External identifier assigned by the original source portal", example = "remoteok-123456")
    private String externalId;

    @Indexed
    @Schema(description = "Standardized job title", example = "Junior Java Developer")
    private String title;

    @Indexed
    @Schema(description = "Hiring company or organization name", example = "Acme Technologies")
    private String company;

    @Schema(description = "Primary work location or office city", example = "Bengaluru, India")
    private String location;

    @Indexed
    @Schema(description = "Work arrangement mode (REMOTE, HYBRID, ON_SITE)", example = "REMOTE")
    private String workMode = "REMOTE";

    @Schema(description = "Employment type (FULL_TIME, INTERNSHIP, CONTRACT, PART_TIME)", example = "FULL_TIME")
    private String employmentType = "FULL_TIME";

    @Schema(description = "Target experience level tier", example = "ENTRY_LEVEL_FRESHER")
    private String experienceLevel = "ENTRY_LEVEL_FRESHER";

    @Schema(description = "Country jurisdiction", example = "India")
    private String country = "India";

    @Schema(description = "Full text or HTML sanitized job description")
    private String description;

    @Schema(description = "Catalog of required technical skills", example = "[\"Java\", \"Spring Boot\", \"MongoDB\"]")
    private List<String> requiredSkills = new ArrayList<>();

    @Schema(description = "Minimum annual compensation or monthly stipend", example = "600000")
    private Double salaryMin;

    @Schema(description = "Maximum annual compensation or monthly stipend", example = "900000")
    private Double salaryMax;

    @Schema(description = "Salary currency code", example = "INR")
    private String salaryCurrency = "INR";

    @Schema(description = "Direct application URL or careers portal link", example = "https://careers.example.com/apply/123")
    private String applyUrl;

    @Indexed
    @Schema(description = "Source connector identifier (REMOTE_OK, ARBEITNOW, ENTERPRISE_PORTAL)", example = "REMOTE_OK")
    private String source;

    @Schema(description = "Timestamp when the job was originally posted by the employer")
    private Instant postedAt;

    @Schema(description = "UTC timestamp when the job was discovered and indexed by the agent")
    private Instant discoveredAt = Instant.now();

    @Indexed
    @Schema(description = "Application pipeline lifecycle status (DISCOVERED, MATCHED, APPLIED, REJECTED, EXPIRED)", example = "DISCOVERED")
    private String status = "DISCOVERED";

    public JobPosting() {}

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDedupHash() {
        return dedupHash;
    }

    public void setDedupHash(String dedupHash) {
        this.dedupHash = dedupHash;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getWorkMode() {
        return workMode;
    }

    public void setWorkMode(String workMode) {
        this.workMode = workMode != null ? workMode.toUpperCase() : "REMOTE";
    }

    public String getEmploymentType() {
        return employmentType;
    }

    public void setEmploymentType(String employmentType) {
        this.employmentType = employmentType;
    }

    public String getExperienceLevel() {
        return experienceLevel;
    }

    public void setExperienceLevel(String experienceLevel) {
        this.experienceLevel = experienceLevel;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getRequiredSkills() {
        return requiredSkills;
    }

    public void setRequiredSkills(List<String> requiredSkills) {
        this.requiredSkills = requiredSkills != null ? requiredSkills : new ArrayList<>();
    }

    public Double getSalaryMin() {
        return salaryMin;
    }

    public void setSalaryMin(Double salaryMin) {
        this.salaryMin = salaryMin;
    }

    public Double getSalaryMax() {
        return salaryMax;
    }

    public void setSalaryMax(Double salaryMax) {
        this.salaryMax = salaryMax;
    }

    public String getSalaryCurrency() {
        return salaryCurrency;
    }

    public void setSalaryCurrency(String salaryCurrency) {
        this.salaryCurrency = salaryCurrency;
    }

    public String getApplyUrl() {
        return applyUrl;
    }

    public void setApplyUrl(String applyUrl) {
        this.applyUrl = applyUrl;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public void setPostedAt(Instant postedAt) {
        this.postedAt = postedAt;
    }

    public Instant getDiscoveredAt() {
        return discoveredAt;
    }

    public void setDiscoveredAt(Instant discoveredAt) {
        this.discoveredAt = discoveredAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
