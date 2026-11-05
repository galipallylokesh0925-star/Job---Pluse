package com.aijobagent.model;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * MongoDB Document representing the result of an AI/heuristic qualification evaluation
 * matching a candidate against a specific discovered job posting.
 */
@Document(collection = "job_matches")
@CompoundIndex(name = "job_candidate_unique_idx", def = "{'jobId': 1, 'candidateProfileId': 1}", unique = true)
@Schema(description = "AI qualification matching and skill gap analysis outcome")
public class JobMatchResult {

    @Id
    @Schema(description = "Unique MongoDB ObjectId", example = "671e4500a9f4c3b2e1045b10")
    private String id;

    @Indexed
    @Schema(description = "Referenced JobPosting ID", example = "671e4100a9f4c3b2e1045a99")
    private String jobId;

    @Indexed
    @Schema(description = "Referenced CandidateProfile ID", example = "671e3f89a9f4c3b2e1045a12")
    private String candidateProfileId;

    @Schema(description = "Cached job title", example = "Junior Java Developer")
    private String jobTitle;

    @Schema(description = "Cached employer name", example = "Tata Consultancy Services (TCS)")
    private String company;

    @Schema(description = "Cached work location", example = "Bengaluru, India")
    private String location;

    @Schema(description = "Cached work mode", example = "HYBRID")
    private String workMode;

    @Schema(description = "Direct application URL", example = "https://ibegin.tcs.com/careers/job/10101")
    private String applyUrl;

    @Indexed
    @Schema(description = "Overall qualification score between 0 and 100", example = "88")
    private int overallScore;

    @Schema(description = "Technical skills compatibility score (0-100)", example = "92")
    private int skillScore;

    @Schema(description = "Experience tier alignment score (0-100)", example = "85")
    private int experienceScore;

    @Schema(description = "Educational qualification score (0-100)", example = "90")
    private int educationScore;

    @Schema(description = "Search preferences alignment score (0-100)", example = "80")
    private int preferenceScore;

    @Indexed
    @Schema(description = "Indicates whether all non-negotiable deterministic criteria were satisfied", example = "true")
    private boolean hardCriteriaMet = true;

    @Schema(description = "Specific violations if any hard criteria failed")
    private List<String> hardCriteriaViolations = new ArrayList<>();

    @Indexed
    @Schema(description = "Recommendation tier (STRONG_MATCH, MODERATE_MATCH, WEAK_MATCH)", example = "STRONG_MATCH")
    private String recommendationTier = "STRONG_MATCH";

    @Schema(description = "Required skills present in candidate profile", example = "[\"Java\", \"Spring Boot\", \"MongoDB\"]")
    private List<String> matchedSkills = new ArrayList<>();

    @Schema(description = "Required skills missing from candidate profile", example = "[\"Kafka\"]")
    private List<String> missingSkills = new ArrayList<>();

    @Schema(description = "Candidate profile skills that add bonus value", example = "[\"Docker\", \"React\"]")
    private List<String> bonusSkills = new ArrayList<>();

    @Schema(description = "Qualitative assessment summary")
    private String qualitativeSummary;

    @Schema(description = "Actionable preparation tips for candidate interviews and applications")
    private List<String> preparationTips = new ArrayList<>();

    @Schema(description = "UTC timestamp when match was calculated")
    private Instant matchedAt = Instant.now();

    @Schema(description = "Matching algorithm / AI engine utilized", example = "Deterministic Semantic Matcher v1.0")
    private String matchEngine = "Deterministic Semantic Matcher v1.0";

    public JobMatchResult() {}

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getCandidateProfileId() {
        return candidateProfileId;
    }

    public void setCandidateProfileId(String candidateProfileId) {
        this.candidateProfileId = candidateProfileId;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
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
        this.workMode = workMode;
    }

    public String getApplyUrl() {
        return applyUrl;
    }

    public void setApplyUrl(String applyUrl) {
        this.applyUrl = applyUrl;
    }

    public int getOverallScore() {
        return overallScore;
    }

    public void setOverallScore(int overallScore) {
        this.overallScore = overallScore;
    }

    public int getSkillScore() {
        return skillScore;
    }

    public void setSkillScore(int skillScore) {
        this.skillScore = skillScore;
    }

    public int getExperienceScore() {
        return experienceScore;
    }

    public void setExperienceScore(int experienceScore) {
        this.experienceScore = experienceScore;
    }

    public int getEducationScore() {
        return educationScore;
    }

    public void setEducationScore(int educationScore) {
        this.educationScore = educationScore;
    }

    public int getPreferenceScore() {
        return preferenceScore;
    }

    public void setPreferenceScore(int preferenceScore) {
        this.preferenceScore = preferenceScore;
    }

    public boolean isHardCriteriaMet() {
        return hardCriteriaMet;
    }

    public void setHardCriteriaMet(boolean hardCriteriaMet) {
        this.hardCriteriaMet = hardCriteriaMet;
    }

    public List<String> getHardCriteriaViolations() {
        return hardCriteriaViolations;
    }

    public void setHardCriteriaViolations(List<String> hardCriteriaViolations) {
        this.hardCriteriaViolations = hardCriteriaViolations != null ? hardCriteriaViolations : new ArrayList<>();
    }

    public String getRecommendationTier() {
        return recommendationTier;
    }

    public void setRecommendationTier(String recommendationTier) {
        this.recommendationTier = recommendationTier;
    }

    public List<String> getMatchedSkills() {
        return matchedSkills;
    }

    public void setMatchedSkills(List<String> matchedSkills) {
        this.matchedSkills = matchedSkills != null ? matchedSkills : new ArrayList<>();
    }

    public List<String> getMissingSkills() {
        return missingSkills;
    }

    public void setMissingSkills(List<String> missingSkills) {
        this.missingSkills = missingSkills != null ? missingSkills : new ArrayList<>();
    }

    public List<String> getBonusSkills() {
        return bonusSkills;
    }

    public void setBonusSkills(List<String> bonusSkills) {
        this.bonusSkills = bonusSkills != null ? bonusSkills : new ArrayList<>();
    }

    public String getQualitativeSummary() {
        return qualitativeSummary;
    }

    public void setQualitativeSummary(String qualitativeSummary) {
        this.qualitativeSummary = qualitativeSummary;
    }

    public List<String> getPreparationTips() {
        return preparationTips;
    }

    public void setPreparationTips(List<String> preparationTips) {
        this.preparationTips = preparationTips != null ? preparationTips : new ArrayList<>();
    }

    public Instant getMatchedAt() {
        return matchedAt;
    }

    public void setMatchedAt(Instant matchedAt) {
        this.matchedAt = matchedAt;
    }

    public String getMatchEngine() {
        return matchEngine;
    }

    public void setMatchEngine(String matchEngine) {
        this.matchEngine = matchEngine;
    }
}
