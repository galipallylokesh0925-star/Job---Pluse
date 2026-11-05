package com.aijobagent.service;

import com.aijobagent.dto.matching.BatchMatchResult;
import com.aijobagent.dto.matching.JobMatchDto;
import com.aijobagent.model.CandidateProfile;
import com.aijobagent.model.Education;
import com.aijobagent.model.JobMatchResult;
import com.aijobagent.model.JobPosting;
import com.aijobagent.model.JobPreferences;
import com.aijobagent.repository.CandidateProfileRepository;
import com.aijobagent.repository.JobMatchRepository;
import com.aijobagent.repository.JobPostingRepository;
import com.aijobagent.service.impl.JobMatchingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobMatchingServiceTest {

    @Mock
    private JobPostingRepository jobRepository;

    @Mock
    private JobMatchRepository matchRepository;

    @Mock
    private CandidateProfileRepository profileRepository;

    @Mock
    private CandidateProfileService profileService;

    @Mock
    private MongoTemplate mongoTemplate;

    private JobMatchingService matchingService;

    private CandidateProfile sampleProfile;

    @BeforeEach
    @SuppressWarnings("null")
    void setUp() {
        matchingService = new JobMatchingServiceImpl(
                jobRepository,
                matchRepository,
                profileRepository,
                profileService,
                mongoTemplate
        );

        // Inject default configuration weights & thresholds via ReflectionTestUtils
        ReflectionTestUtils.setField(matchingService, "skillsWeight", 0.50);
        ReflectionTestUtils.setField(matchingService, "experienceWeight", 0.25);
        ReflectionTestUtils.setField(matchingService, "educationWeight", 0.15);
        ReflectionTestUtils.setField(matchingService, "preferencesWeight", 0.10);
        ReflectionTestUtils.setField(matchingService, "strongMatchThreshold", 75);
        ReflectionTestUtils.setField(matchingService, "moderateMatchThreshold", 50);
        ReflectionTestUtils.setField(matchingService, "defaultMinScore", 50);

        sampleProfile = new CandidateProfile();
        sampleProfile.setId("prof-1");
        sampleProfile.setFullName("Arjun Sharma");
        sampleProfile.setSkills(List.of("Java", "Spring Boot", "MongoDB", "Docker", "REST APIs"));

        Education edu = new Education();
        edu.setDegree("B.Tech");
        edu.setFieldOfStudy("Computer Science");
        sampleProfile.setEducation(List.of(edu));

        JobPreferences prefs = new JobPreferences();
        prefs.setTargetRoles(List.of("Java Developer", "Software Engineer"));
        prefs.setPreferredWorkModes(List.of("REMOTE", "HYBRID"));
        prefs.setPreferredLocations(List.of("Bengaluru", "Remote"));
        prefs.setExperienceLevel("ENTRY_LEVEL_FRESHER");
        sampleProfile.setPreferences(prefs);
    }

    @Test
    @DisplayName("Calculates high match score for well-aligned candidate skills and preferences")
    @SuppressWarnings("null")
    void shouldEvaluateStrongMatch() {
        JobPosting job = new JobPosting();
        job.setId("job-101");
        job.setTitle("Junior Java Developer");
        job.setCompany("TCS");
        job.setWorkMode("HYBRID");
        job.setLocation("Bengaluru, India");
        job.setCountry("India");
        job.setExperienceLevel("ENTRY_LEVEL_FRESHER");
        job.setRequiredSkills(List.of("Java", "Spring Boot", "SQL"));

        when(jobRepository.findById("job-101")).thenReturn(Optional.of(job));
        when(profileRepository.findById("prof-1")).thenReturn(Optional.of(sampleProfile));
        when(matchRepository.save(any(JobMatchResult.class))).thenAnswer(inv -> inv.getArgument(0));

        JobMatchDto match = matchingService.evaluateMatch("job-101", "prof-1");

        assertNotNull(match);
        assertTrue(match.overallScore() >= 75);
        assertEquals("STRONG_MATCH", match.recommendationTier());
        assertTrue(match.hardCriteriaMet());
        assertTrue(match.matchedSkills().contains("Java"));
        assertTrue(match.matchedSkills().contains("Spring Boot"));
        assertTrue(match.missingSkills().contains("SQL"));
        assertTrue(match.bonusSkills().contains("Docker"));
    }

    @Test
    @DisplayName("Flags hard criteria violation when work mode violates candidate strict remote constraint")
    @SuppressWarnings("null")
    void shouldFlagHardCriteriaViolation() {
        // Candidate strictly wants REMOTE
        sampleProfile.getPreferences().setPreferredWorkModes(List.of("REMOTE"));

        JobPosting job = new JobPosting();
        job.setId("job-102");
        job.setTitle("Software Engineer");
        job.setCompany("Wipro");
        job.setWorkMode("ON_SITE");
        job.setLocation("Noida, India");
        job.setCountry("India");
        job.setExperienceLevel("ENTRY_LEVEL_FRESHER");
        job.setRequiredSkills(List.of("Java", "Spring Boot"));

        when(jobRepository.findById("job-102")).thenReturn(Optional.of(job));
        when(profileRepository.findById("prof-1")).thenReturn(Optional.of(sampleProfile));
        when(matchRepository.save(any(JobMatchResult.class))).thenAnswer(inv -> inv.getArgument(0));

        JobMatchDto match = matchingService.evaluateMatch("job-102", "prof-1");

        assertNotNull(match);
        assertFalse(match.hardCriteriaMet());
        assertFalse(match.hardCriteriaViolations().isEmpty());
        assertTrue(match.hardCriteriaViolations().get(0).contains("ON_SITE"));
    }

    @Test
    @DisplayName("Batch evaluates all discovered jobs and updates job statuses")
    @SuppressWarnings("null")
    void shouldBatchEvaluateDiscoveredJobs() {
        JobPosting job1 = new JobPosting();
        job1.setId("j-1");
        job1.setTitle("Java Developer");
        job1.setWorkMode("HYBRID");
        job1.setLocation("Bengaluru");
        job1.setRequiredSkills(List.of("Java", "Spring Boot"));

        JobPosting job2 = new JobPosting();
        job2.setId("j-2");
        job2.setTitle("Senior Lead Architect"); // Seniority mismatch
        job2.setWorkMode("ON_SITE");
        job2.setLocation("Germany");
        job2.setRequiredSkills(List.of("Rust", "Go", "Solidity"));

        when(profileRepository.findById("prof-1")).thenReturn(Optional.of(sampleProfile));
        when(jobRepository.findAll()).thenReturn(List.of(job1, job2));
        when(matchRepository.findByJobIdAndCandidateProfileId(any(), any())).thenReturn(Optional.empty());

        BatchMatchResult result = matchingService.evaluateAllDiscoveredJobs("prof-1", 50);

        assertNotNull(result);
        assertTrue(result.success());
        assertEquals(2, result.totalJobsEvaluated());
        assertEquals(1, result.hardCriteriaPassCount());
        assertEquals(1, result.hardCriteriaFailCount());
        verify(jobRepository, times(2)).save(any(JobPosting.class));
    }

    @Test
    @DisplayName("Batch minimum score threshold controls whether jobs are marked as matched")
    @SuppressWarnings("null")
    void shouldApplyBatchMinimumScoreThresholdToJobStatus() {
        JobPosting job = new JobPosting();
        job.setId("j-3");
        job.setTitle("Java Developer");
        job.setWorkMode("HYBRID");
        job.setLocation("Bengaluru");
        job.setRequiredSkills(List.of("Java", "Spring Boot"));

        when(profileRepository.findById("prof-1")).thenReturn(Optional.of(sampleProfile));
        when(jobRepository.findAll()).thenReturn(List.of(job));
        when(matchRepository.findByJobIdAndCandidateProfileId(any(), any())).thenReturn(Optional.empty());

        matchingService.evaluateAllDiscoveredJobs("prof-1", 100);

        assertEquals("REJECTED", job.getStatus());
        verify(jobRepository).save(job);
    }
}
