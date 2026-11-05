package com.aijobagent.service;

import com.aijobagent.dto.applicationprep.ApplicationPreparationDto;
import com.aijobagent.dto.applicationprep.ProfileReviewDto;
import com.aijobagent.dto.applicationprep.TruthfulnessValidationDto;
import com.aijobagent.model.CandidateProfile;
import com.aijobagent.model.Education;
import com.aijobagent.model.JobPosting;
import com.aijobagent.model.ProjectDetail;
import com.aijobagent.model.WorkExperience;
import com.aijobagent.repository.CandidateProfileRepository;
import com.aijobagent.repository.JobPostingRepository;
import com.aijobagent.service.impl.ApplicationPreparationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationPreparationServiceTest {

    @Mock
    private CandidateProfileRepository profileRepository;

    @Mock
    private JobPostingRepository jobRepository;

    private ApplicationPreparationService preparationService;
    private CandidateProfile profile;
    private JobPosting job;

    @BeforeEach
    void setUp() {
        preparationService = new ApplicationPreparationServiceImpl(profileRepository, jobRepository);

        profile = new CandidateProfile();
        profile.setId("profile-1");
        profile.setFullName("Asha Rao");
        profile.setEmail("asha@example.com");
        profile.setSkills(List.of("Java", "Spring Boot"));
        profile.setRawResumeText("Asha Rao asha@example.com Java Spring Boot");
        profile.setExperience(List.of(new WorkExperience(
                "Example Labs", "Software Intern", "Pune", "Jan 2024", "Jun 2024", false, List.of())));
        profile.setEducation(List.of(new Education("B.Tech", "Example University", "Computer Science", null, null, null)));
        profile.setProjects(List.of(new ProjectDetail("Job Tracker", "A job tracker project", List.of("Java"), null, null)));

        job = new JobPosting();
        job.setId("job-1");
        job.setTitle("Junior Java Developer");
        job.setCompany("Acme");
        job.setApplyUrl("https://example.com/apply");
        job.setRequiredSkills(List.of("Java", "SQL"));

        when(profileRepository.findTopByOrderByUpdatedAtDesc()).thenReturn(Optional.of(profile));
    }

    @Test
    void generatesTailoredLetterFromProfileEvidenceOnly() {
        when(jobRepository.findById("job-1")).thenReturn(Optional.of(job));
        ApplicationPreparationDto result = preparationService.prepareForJob("job-1");

        assertTrue(result.coverLetter().contains("Junior Java Developer"));
        assertTrue(result.coverLetter().contains("Acme"));
        assertTrue(result.coverLetter().contains("Java"));
        assertTrue(result.coverLetter().contains("Software Intern at Example Labs"));
        assertTrue(result.coverLetter().contains("Job Tracker"));
        assertTrue(result.evidence().stream().anyMatch(item -> item.contains("Java")));
        assertEquals("Evidence-grounded deterministic template", result.generationMethod());
        assertTrue(result.truthfulness().allClaimsSupported());
    }

    @Test
    void profileReviewMarksValuesThatDoNotAppearInResumeForReview() {
        profile.setSkills(List.of("Java", "Kubernetes"));

        ProfileReviewDto result = preparationService.reviewActiveProfile();

        assertTrue(result.resumeAvailable());
        assertEquals("NEEDS_REVIEW", result.overallStatus());
        assertTrue(result.items().stream()
                .anyMatch(item -> item.field().equals("Skills") && item.status().equals("PARTIAL_MATCH")));
        assertTrue(result.items().stream()
                .anyMatch(item -> item.field().equals("Projects") && item.status().equals("PARTIAL_MATCH")));
    }

    @Test
    void flagsUnsupportedPersonalAndQuantifiedClaims() {
        when(jobRepository.findById("job-1")).thenReturn(Optional.of(job));
        TruthfulnessValidationDto result = preparationService.validateCoverLetter(
                "job-1", "I led a team of 10 people. I delivered 50 projects.");

        assertFalse(result.allClaimsSupported());
        assertEquals(2, result.unsupportedClaims().size());
        assertTrue(result.unsupportedClaims().get(0).contains("10 people"));
        assertTrue(result.unsupportedClaims().get(1).contains("50 projects"));
        assertFalse(result.disclaimer().isBlank());
    }

    @Test
    void doesNotTreatAMentionedSkillAsEvidenceForAnUnrelatedExperienceClaim() {
        when(jobRepository.findById("job-1")).thenReturn(Optional.of(job));

        TruthfulnessValidationDto result = preparationService.validateCoverLetter(
                "job-1", "I have led Java engineering teams.");

        assertFalse(result.allClaimsSupported());
        assertEquals(List.of("I have led Java engineering teams."), result.unsupportedClaims());
    }
}
