package com.aijobagent.service.impl;

import com.aijobagent.dto.matching.BatchMatchResult;
import com.aijobagent.dto.matching.JobMatchDto;
import com.aijobagent.dto.matching.MatchScoreFilter;
import com.aijobagent.dto.matching.MatchStatsDto;
import com.aijobagent.dto.profile.CandidateProfileDto;
import com.aijobagent.exception.ResourceNotFoundException;
import com.aijobagent.model.CandidateProfile;
import com.aijobagent.model.Education;
import com.aijobagent.model.JobMatchResult;
import com.aijobagent.model.JobPosting;
import com.aijobagent.model.JobPreferences;
import com.aijobagent.repository.CandidateProfileRepository;
import com.aijobagent.repository.JobMatchRepository;
import com.aijobagent.repository.JobPostingRepository;
import com.aijobagent.service.CandidateProfileService;
import com.aijobagent.service.JobMatchingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Production implementation of JobMatchingService.
 *
 * Implements deterministic hard criteria validation, multi-dimensional qualification
 * scoring (skills, experience, education, preferences), and skill gap analysis.
 */
@Service
public class JobMatchingServiceImpl implements JobMatchingService {

    private static final Logger log = LoggerFactory.getLogger(JobMatchingServiceImpl.class);

    private final JobPostingRepository jobRepository;
    private final JobMatchRepository matchRepository;
    private final CandidateProfileRepository profileRepository;
    private final CandidateProfileService profileService;
    private final MongoTemplate mongoTemplate;

    @Value("${app.matching.weights.skills:0.50}")
    private double skillsWeight;

    @Value("${app.matching.weights.experience:0.25}")
    private double experienceWeight;

    @Value("${app.matching.weights.education:0.15}")
    private double educationWeight;

    @Value("${app.matching.weights.preferences:0.10}")
    private double preferencesWeight;

    @Value("${app.matching.thresholds.strong-match:75}")
    private int strongMatchThreshold;

    @Value("${app.matching.thresholds.moderate-match:50}")
    private int moderateMatchThreshold;

    @Value("${app.matching.default-min-score:50}")
    private int defaultMinScore;

    public JobMatchingServiceImpl(
            JobPostingRepository jobRepository,
            JobMatchRepository matchRepository,
            CandidateProfileRepository profileRepository,
            CandidateProfileService profileService,
            MongoTemplate mongoTemplate) {
        this.jobRepository = jobRepository;
        this.matchRepository = matchRepository;
        this.profileRepository = profileRepository;
        this.profileService = profileService;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    @Transactional
    public JobMatchDto evaluateMatch(String jobId, String candidateProfileId) {
        String requiredJobId = Objects.requireNonNull(jobId, "Job ID is required");
        JobPosting job = jobRepository.findById(requiredJobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job posting not found with ID: " + requiredJobId));

        CandidateProfile profile = resolveCandidateProfile(candidateProfileId);

        JobMatchResult matchResult = calculateMatch(job, profile);
        JobMatchResult saved = matchRepository.save(Objects.requireNonNull(matchResult));

        // Update JobPosting lifecycle status based on evaluation outcome
        if (saved.isHardCriteriaMet() && saved.getOverallScore() >= moderateMatchThreshold) {
            job.setStatus("MATCHED");
            jobRepository.save(job);
        }

        return toDto(saved);
    }

    @Override
    @Transactional
    public BatchMatchResult evaluateAllDiscoveredJobs(String candidateProfileId, Integer minScoreThreshold) {
        Instant startTime = Instant.now();
        CandidateProfile profile = resolveCandidateProfile(candidateProfileId);
        int threshold = minScoreThreshold != null ? minScoreThreshold : defaultMinScore;
        if (threshold < 0 || threshold > 100) {
            throw new IllegalArgumentException("Minimum score threshold must be between 0 and 100.");
        }

        List<JobPosting> allJobs = jobRepository.findAll();
        log.info("Starting batch qualification matching across {} discovered jobs for candidate: {}",
                allJobs.size(), profile.getFullName());

        int strongCount = 0;
        int moderateCount = 0;
        int weakCount = 0;
        int passCount = 0;
        int failCount = 0;
        double totalScore = 0.0;

        for (JobPosting job : allJobs) {
            JobMatchResult result = calculateMatch(job, profile);

            // Check if existing match exists to preserve ID
            Optional<JobMatchResult> existing = matchRepository.findByJobIdAndCandidateProfileId(job.getId(), profile.getId());
            existing.ifPresent(prev -> result.setId(prev.getId()));

            matchRepository.save(Objects.requireNonNull(result));

            if (result.isHardCriteriaMet()) {
                passCount++;
            } else {
                failCount++;
            }

            int score = result.getOverallScore();
            totalScore += score;

            if (score >= strongMatchThreshold && result.isHardCriteriaMet()) {
                strongCount++;
            } else if (score >= moderateMatchThreshold && result.isHardCriteriaMet()) {
                moderateCount++;
            } else {
                weakCount++;
            }
            if (result.isHardCriteriaMet() && score >= threshold) {
                job.setStatus("MATCHED");
            } else {
                job.setStatus("REJECTED");
            }

            jobRepository.save(job);
        }

        Instant endTime = Instant.now();
        long durationMs = endTime.toEpochMilli() - startTime.toEpochMilli();
        double avgScore = allJobs.isEmpty() ? 0.0 : Math.round((totalScore / allJobs.size()) * 10.0) / 10.0;

        String message = String.format("Batch matching completed in %d ms. Evaluated %d jobs: %d strong matches, %d moderate matches, %d hard criteria passed (avg score: %.1f).",
                durationMs, allJobs.size(), strongCount, moderateCount, passCount, avgScore);
        log.info(message);

        return new BatchMatchResult(
                true,
                message,
                allJobs.size(),
                strongCount,
                moderateCount,
                weakCount,
                passCount,
                failCount,
                avgScore,
                durationMs
        );
    }

    @Override
    public Page<JobMatchDto> getRankedMatches(MatchScoreFilter filter) {
        CandidateProfile profile = resolveCandidateProfile(null);

        Query query = new Query();
        query.addCriteria(Criteria.where("candidateProfileId").is(profile.getId()));

        if (filter.minScore() != null && filter.minScore() > 0) {
            query.addCriteria(Criteria.where("overallScore").gte(filter.minScore().intValue()));
        }

        if (filter.tier() != null && !filter.tier().isBlank() && !filter.tier().equalsIgnoreCase("ALL")) {
            query.addCriteria(Criteria.where("recommendationTier").is(filter.tier().trim().toUpperCase()));
        }

        if (Boolean.TRUE.equals(filter.hardCriteriaOnly())) {
            query.addCriteria(Criteria.where("hardCriteriaMet").is(true));
        }

        if (filter.keyword() != null && !filter.keyword().isBlank()) {
            Pattern regex = Pattern.compile(Pattern.quote(filter.keyword().trim()), Pattern.CASE_INSENSITIVE);
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("jobTitle").regex(Objects.requireNonNull(regex)),
                    Criteria.where("company").regex(Objects.requireNonNull(regex)),
                    Criteria.where("matchedSkills").regex(Objects.requireNonNull(regex))
            ));
        }

        long total = mongoTemplate.count(query, JobMatchResult.class);

        Pageable pageable = PageRequest.of(
                filter.getPage(),
                filter.getSize(),
                Sort.by(Sort.Direction.DESC, "overallScore")
        );
        query.with(pageable);

        List<JobMatchResult> results = mongoTemplate.find(query, JobMatchResult.class);
        List<JobMatchDto> dtos = results.stream().map(this::toDto).toList();

        return new PageImpl<>(Objects.requireNonNull(dtos), pageable, total);
    }

    @Override
    public JobMatchDto getMatchByJobId(String jobId) {
        CandidateProfile profile = resolveCandidateProfile(null);
        JobMatchResult match = matchRepository.findByJobIdAndCandidateProfileId(jobId, profile.getId())
                .orElseGet(() -> {
                    JobPosting job = jobRepository.findById(Objects.requireNonNull(jobId, "Job ID is required"))
                            .orElseThrow(() -> new ResourceNotFoundException("Job posting not found with ID: " + jobId));
                    return matchRepository.save(Objects.requireNonNull(calculateMatch(job, profile)));
                });
        return toDto(match);
    }

    @Override
    public MatchStatsDto getMatchStats() {
        CandidateProfile profile = resolveCandidateProfile(null);
        String profileId = profile.getId();

        long total = matchRepository.countByCandidateProfileId(profileId);
        long strong = matchRepository.countByCandidateProfileIdAndRecommendationTier(profileId, "STRONG_MATCH");
        long moderate = matchRepository.countByCandidateProfileIdAndRecommendationTier(profileId, "MODERATE_MATCH");
        long weak = matchRepository.countByCandidateProfileIdAndRecommendationTier(profileId, "WEAK_MATCH");
        long hardPassed = matchRepository.countByCandidateProfileIdAndHardCriteriaMet(profileId, true);

        List<JobMatchResult> all = matchRepository.findByCandidateProfileIdOrderByOverallScoreDesc(profileId);
        double avg = all.isEmpty() ? 0.0 : all.stream().mapToInt(JobMatchResult::getOverallScore).average().orElse(0.0);
        avg = Math.round(avg * 10.0) / 10.0;

        return new MatchStatsDto(total, strong, moderate, weak, hardPassed, avg);
    }

    @Override
    public void clearAllMatches() {
        CandidateProfile profile = resolveCandidateProfile(null);
        matchRepository.deleteByCandidateProfileId(profile.getId());
        log.info("Cleared all qualification match evaluations for candidate profile: {}", profile.getId());
    }

    /**
     * Core multi-dimensional scoring and criteria evaluation algorithm.
     */
    private JobMatchResult calculateMatch(JobPosting job, CandidateProfile profile) {
        JobMatchResult result = new JobMatchResult();
        result.setJobId(job.getId());
        result.setCandidateProfileId(profile.getId());
        result.setJobTitle(job.getTitle());
        result.setCompany(job.getCompany());
        result.setLocation(job.getLocation());
        result.setWorkMode(job.getWorkMode());
        result.setApplyUrl(job.getApplyUrl());
        result.setMatchedAt(Instant.now());
        result.setMatchEngine("Deterministic Semantic Matcher v1.0");

        JobPreferences prefs = profile.getPreferences() != null ? profile.getPreferences() : new JobPreferences();

        // 1. Evaluate Deterministic Hard Criteria
        List<String> violations = new ArrayList<>();
        boolean hardCriteriaPassed = true;

        // Hard Criteria A: Work Mode compatibility
        List<String> preferredModes = prefs.getPreferredWorkModes() != null ? prefs.getPreferredWorkModes() : List.of();
        if (!preferredModes.isEmpty() && preferredModes.size() == 1 && preferredModes.contains("REMOTE")) {
            if ("ON_SITE".equalsIgnoreCase(job.getWorkMode())) {
                violations.add("Work mode 'ON_SITE' violates candidate's strict REMOTE-only requirement.");
                hardCriteriaPassed = false;
            }
        }

        // Hard Criteria B: Seniority compatibility
        String lowerTitle = job.getTitle() != null ? job.getTitle().toLowerCase() : "";
        if ("ENTRY_LEVEL_FRESHER".equalsIgnoreCase(prefs.getExperienceLevel())) {
            if (lowerTitle.contains("senior") || lowerTitle.contains("lead") || lowerTitle.contains("architect") || lowerTitle.contains("principal")) {
                violations.add("Seniority tier mismatch: Candidate is entry-level/fresher but role demands senior/lead qualifications.");
                hardCriteriaPassed = false;
            }
        }

        // Hard Criteria C: Country / Jurisdiction alignment
        if ("India".equalsIgnoreCase(prefs.getExperienceLevel()) || job.getCountry() != null) {
            if ("ON_SITE".equalsIgnoreCase(job.getWorkMode()) && !"India".equalsIgnoreCase(job.getCountry()) && !job.getLocation().toLowerCase().contains("india")) {
                violations.add("Jurisdiction mismatch: On-site role in " + job.getLocation() + " without domestic work authorization.");
                hardCriteriaPassed = false;
            }
        }

        result.setHardCriteriaMet(hardCriteriaPassed);
        result.setHardCriteriaViolations(violations);

        // 2. Skill Overlap & Gap Analysis (50% weight)
        Set<String> candidateSkills = profile.getSkills() != null
                ? profile.getSkills().stream().map(String::trim).collect(Collectors.toCollection(LinkedHashSet::new))
                : Set.of();

        Set<String> candidateSkillsLower = candidateSkills.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        List<String> jobSkills = job.getRequiredSkills() != null ? job.getRequiredSkills() : List.of();

        List<String> matchedSkills = new ArrayList<>();
        List<String> missingSkills = new ArrayList<>();

        for (String req : jobSkills) {
            if (candidateSkillsLower.contains(req.toLowerCase())) {
                matchedSkills.add(req);
            } else {
                missingSkills.add(req);
            }
        }

        List<String> bonusSkills = new ArrayList<>();
        for (String cs : candidateSkills) {
            boolean isRequired = jobSkills.stream().anyMatch(js -> js.equalsIgnoreCase(cs));
            if (!isRequired) {
                // Check if bonus skill is relevant to software engineering
                if (cs.equalsIgnoreCase("Docker") || cs.equalsIgnoreCase("AWS") || cs.equalsIgnoreCase("Kubernetes") ||
                        cs.equalsIgnoreCase("Kafka") || cs.equalsIgnoreCase("React") || cs.equalsIgnoreCase("MongoDB")) {
                    bonusSkills.add(cs);
                }
            }
        }

        int skillScore;
        if (jobSkills.isEmpty()) {
            skillScore = 75; // Baseline when job has no specific skills tagged
        } else {
            double ratio = (double) matchedSkills.size() / jobSkills.size();
            int baseSkillScore = (int) Math.round(ratio * 90.0);
            int bonus = Math.min(10, bonusSkills.size() * 3);
            skillScore = Math.min(100, baseSkillScore + bonus);
        }

        result.setMatchedSkills(matchedSkills);
        result.setMissingSkills(missingSkills);
        result.setBonusSkills(bonusSkills);
        result.setSkillScore(skillScore);

        // 3. Experience & Role Alignment Score (25% weight)
        int experienceScore = 75;
        // Boost if job title matches candidate target roles
        if (prefs.getTargetRoles() != null) {
            boolean roleMatch = prefs.getTargetRoles().stream()
                    .anyMatch(tr -> lowerTitle.contains(tr.toLowerCase()) || tr.toLowerCase().contains(lowerTitle));
            if (roleMatch) experienceScore += 15;
        }
        if (profile.getExperience() != null && !profile.getExperience().isEmpty()) {
            experienceScore += 10;
        }
        experienceScore = Math.min(100, Math.max(20, experienceScore));
        result.setExperienceScore(experienceScore);

        // 4. Education Alignment Score (15% weight)
        int educationScore = 80;
        if (profile.getEducation() != null && !profile.getEducation().isEmpty()) {
            Education edu = profile.getEducation().get(0);
            String deg = edu.getDegree() != null ? edu.getDegree().toLowerCase() : "";
            String field = edu.getFieldOfStudy() != null ? edu.getFieldOfStudy().toLowerCase() : "";
            if (deg.contains("b.tech") || deg.contains("b.e") || deg.contains("m.tech") || deg.contains("mca") || field.contains("computer")) {
                educationScore = 95;
            }
        }
        result.setEducationScore(educationScore);

        // 5. Preferences Alignment Score (10% weight)
        int preferenceScore = 60;
        if (preferredModes.contains(job.getWorkMode())) {
            preferenceScore += 20;
        }
        if (prefs.getPreferredLocations() != null && !prefs.getPreferredLocations().isEmpty()) {
            boolean locMatch = prefs.getPreferredLocations().stream()
                    .anyMatch(loc -> job.getLocation() != null && job.getLocation().toLowerCase().contains(loc.toLowerCase()));
            if (locMatch || "REMOTE".equalsIgnoreCase(job.getWorkMode())) {
                preferenceScore += 20;
            }
        }
        preferenceScore = Math.min(100, preferenceScore);
        result.setPreferenceScore(preferenceScore);

        // 6. Calculate Weighted Overall Score
        double weighted = (skillScore * skillsWeight) +
                (experienceScore * experienceWeight) +
                (educationScore * educationWeight) +
                (preferenceScore * preferencesWeight);

        // Penalty if hard criteria failed
        if (!hardCriteriaPassed) {
            weighted = weighted * 0.65; // 35% penalty for hard constraint violation
        }

        int overallScore = Math.min(100, Math.max(0, (int) Math.round(weighted)));
        result.setOverallScore(overallScore);

        // 7. Determine Recommendation Tier
        if (overallScore >= strongMatchThreshold && hardCriteriaPassed) {
            result.setRecommendationTier("STRONG_MATCH");
        } else if (overallScore >= moderateMatchThreshold && hardCriteriaPassed) {
            result.setRecommendationTier("MODERATE_MATCH");
        } else {
            result.setRecommendationTier("WEAK_MATCH");
        }

        // 8. Generate Qualitative Summary & Preparation Tips
        result.setQualitativeSummary(generateQualitativeSummary(result, job));
        result.setPreparationTips(generatePreparationTips(result, job));

        return result;
    }

    private String generateQualitativeSummary(JobMatchResult res, JobPosting job) {
        if (!res.isHardCriteriaMet()) {
            return "Application not recommended due to hard constraint violations: " +
                    String.join("; ", res.getHardCriteriaViolations());
        }

        if ("STRONG_MATCH".equals(res.getRecommendationTier())) {
            return String.format("High qualification compatibility (%d%%). Verified candidate skills match %d of %d required tech stacks (%s). Excellent alignment with entry-level developer profile.",
                    res.getOverallScore(), res.getMatchedSkills().size(),
                    res.getMatchedSkills().size() + res.getMissingSkills().size(),
                    String.join(", ", res.getMatchedSkills()));
        } else if ("MODERATE_MATCH".equals(res.getRecommendationTier())) {
            return String.format("Good foundational compatibility (%d%%). Strong core competencies in %s, with minor skill gaps in %s. Worth applying with tailored cover letter.",
                    res.getOverallScore(),
                    res.getMatchedSkills().isEmpty() ? "general engineering" : String.join(", ", res.getMatchedSkills()),
                    res.getMissingSkills().isEmpty() ? "domain specific tools" : String.join(", ", res.getMissingSkills()));
        } else {
            return String.format("Limited qualification compatibility (%d%%). Multiple critical technical prerequisites are missing from resume (%s).",
                    res.getOverallScore(), String.join(", ", res.getMissingSkills()));
        }
    }

    private List<String> generatePreparationTips(JobMatchResult res, JobPosting job) {
        List<String> tips = new ArrayList<>();

        if (!res.getMatchedSkills().isEmpty()) {
            tips.add("Lead your application by emphasizing proven projects utilizing " + String.join(", ", res.getMatchedSkills()) + ".");
        }

        if (!res.getMissingSkills().isEmpty()) {
            tips.add("Review core architectural concepts for missing skills (" + String.join(", ", res.getMissingSkills()) + ") prior to interviews.");
        }

        if (!res.getBonusSkills().isEmpty()) {
            tips.add("Highlight bonus value-add strengths in " + String.join(", ", res.getBonusSkills()) + " as a competitive differentiator.");
        }

        if ("HYBRID".equalsIgnoreCase(job.getWorkMode()) || "ON_SITE".equalsIgnoreCase(job.getWorkMode())) {
            tips.add("Confirm flexibility for " + job.getLocation() + " office attendance during HR screening.");
        }

        return tips;
    }

    private CandidateProfile resolveCandidateProfile(String candidateProfileId) {
        if (candidateProfileId != null && !candidateProfileId.isBlank()) {
            return profileRepository.findById(Objects.requireNonNull(candidateProfileId, "Candidate profile ID is required"))
                    .orElseThrow(() -> new ResourceNotFoundException("Candidate profile not found with ID: " + candidateProfileId));
        }

        CandidateProfileDto activeDto = profileService.getActiveProfile();
        return profileRepository.findById(Objects.requireNonNull(activeDto.id(), "Active profile ID is required"))
                .orElseGet(() -> profileRepository.findTopByOrderByUpdatedAtDesc()
                        .orElseThrow(() -> new ResourceNotFoundException("No candidate profile available.")));
    }

    private JobMatchDto toDto(JobMatchResult m) {
        return new JobMatchDto(
                m.getId(),
                m.getJobId(),
                m.getJobTitle(),
                m.getCompany(),
                m.getLocation(),
                m.getWorkMode(),
                m.getApplyUrl(),
                m.getOverallScore(),
                m.getSkillScore(),
                m.getExperienceScore(),
                m.getEducationScore(),
                m.getPreferenceScore(),
                m.isHardCriteriaMet(),
                m.getHardCriteriaViolations(),
                m.getRecommendationTier(),
                m.getMatchedSkills(),
                m.getMissingSkills(),
                m.getBonusSkills(),
                m.getQualitativeSummary(),
                m.getPreparationTips(),
                m.getMatchedAt(),
                m.getMatchEngine()
        );
    }
}
