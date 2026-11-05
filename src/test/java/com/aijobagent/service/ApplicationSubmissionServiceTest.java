package com.aijobagent.service;

import com.aijobagent.dto.applicationprep.ApplicationSubmissionDto;
import com.aijobagent.dto.applicationprep.ApplicationSubmissionRequest;
import com.aijobagent.dto.applicationprep.ProfileReviewDto;
import com.aijobagent.dto.applicationprep.TruthfulnessValidationDto;
import com.aijobagent.exception.ApplicationRateLimitExceededException;
import com.aijobagent.model.ApplicationSubmission;
import com.aijobagent.model.ApplicationSubmissionStatus;
import com.aijobagent.model.CandidateProfile;
import com.aijobagent.model.JobMatchResult;
import com.aijobagent.model.JobPosting;
import com.aijobagent.repository.ApplicationSubmissionRepository;
import com.aijobagent.repository.CandidateProfileRepository;
import com.aijobagent.repository.JobMatchRepository;
import com.aijobagent.repository.JobPostingRepository;
import com.aijobagent.service.impl.ApplicationSubmissionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class ApplicationSubmissionServiceTest {

    @Mock
    private CandidateProfileRepository profileRepository;

    @Mock
    private JobPostingRepository jobRepository;

    @Mock
    private JobMatchRepository matchRepository;

    @Mock
    private ApplicationSubmissionRepository submissionRepository;

    @Mock
    private ApplicationPreparationService preparationService;

    @Mock
    private MongoTemplate mongoTemplate;

    private CandidateProfile profile;
    private JobPosting job;

    @BeforeEach
    void setUp() {
        profile = new CandidateProfile();
        profile.setId("profile-1");

        job = new JobPosting();
        job.setId("job-1");
        job.setTitle("Software Engineer");
        job.setCompany("Example Co");
        job.setApplyUrl("https://careers.example.com/apply");
    }

    @Test
    void requiresExplicitConfirmationBeforeLookingUpProfile() {
        ApplicationSubmissionService service = createService(List.of());

        assertThrows(IllegalArgumentException.class,
                () -> service.submit("job-1", new ApplicationSubmissionRequest(false, "Reviewed letter")));

        verifyNoInteractions(profileRepository, jobRepository, submissionRepository);
    }

    @Test
    void recordsLinkOutInsteadOfClaimingApplicationWasSubmitted() {
        stubProfileAndJob();
        when(preparationService.validateCoverLetter("job-1", "Reviewed letter"))
                .thenReturn(supportedClaims());
        when(mongoTemplate.count(any(), eq(ApplicationSubmission.class))).thenReturn(0L);
        when(submissionRepository.save(any(ApplicationSubmission.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ApplicationSubmissionService service = createService(List.of());

        ApplicationSubmissionDto result =
                service.submit("job-1", new ApplicationSubmissionRequest(true, "Reviewed letter"));

        assertEquals(ApplicationSubmissionStatus.LINK_OUT, result.status());
        assertEquals("https://careers.example.com/apply", result.applicationUrl());
        assertFalse(result.alreadyProcessed());
        verify(jobRepository, never()).save(any());
        ArgumentCaptor<ApplicationSubmission> saved = ArgumentCaptor.forClass(ApplicationSubmission.class);
        verify(submissionRepository, times(2)).save(saved.capture());
        assertEquals(ApplicationSubmissionStatus.LINK_OUT, saved.getValue().getStatus());
        assertNotNull(saved.getValue().getCompletedAt());
    }

    @Test
    void rejectsUnsafeEmployerUrlAndDoesNotReturnItAsLinkOut() {
        stubProfileAndJob();
        job.setApplyUrl("javascript:alert(1)");
        when(preparationService.validateCoverLetter("job-1", "Reviewed letter"))
                .thenReturn(supportedClaims());
        when(mongoTemplate.count(any(), eq(ApplicationSubmission.class))).thenReturn(0L);
        when(submissionRepository.save(any(ApplicationSubmission.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ApplicationSubmissionService service = createService(List.of());

        ApplicationSubmissionDto result =
                service.submit("job-1", new ApplicationSubmissionRequest(true, "Reviewed letter"));

        assertEquals(ApplicationSubmissionStatus.FAILED, result.status());
        assertNull(result.applicationUrl());
        assertTrue(result.message().contains("No safe HTTP(S)"));
    }

    @Test
    void returnsExistingAttemptWithoutSubmittingItAgain() {
        stubProfileAndJob();
        ApplicationSubmission previous = new ApplicationSubmission();
        previous.setId("submission-1");
        previous.setJobId("job-1");
        previous.setStatus(ApplicationSubmissionStatus.LINK_OUT);
        previous.setApplicationUrl("https://careers.example.com/apply");
        previous.setMessage("Continue on the employer site.");
        previous.setCreatedAt(Instant.now());
        when(submissionRepository.findByIdempotencyKey(any())).thenReturn(Optional.of(previous));
        ApplicationSubmissionService service = createService(List.of());

        ApplicationSubmissionDto result =
                service.submit("job-1", new ApplicationSubmissionRequest(true, "Reviewed letter"));

        assertTrue(result.alreadyProcessed());
        assertEquals(ApplicationSubmissionStatus.LINK_OUT, result.status());
        verifyNoInteractions(preparationService, mongoTemplate);
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void enforcesCandidateDailyApplicationLimit() {
        stubProfileAndJob();
        profile.getPreferences().setMaxDailyApplications(1);
        when(preparationService.validateCoverLetter("job-1", "Reviewed letter"))
                .thenReturn(supportedClaims());
        when(mongoTemplate.count(any(), eq(ApplicationSubmission.class))).thenReturn(1L);
        ApplicationSubmissionService service = createService(List.of());

        assertThrows(ApplicationRateLimitExceededException.class,
                () -> service.submit("job-1", new ApplicationSubmissionRequest(true, "Reviewed letter")));

        verify(submissionRepository, never()).save(any());
    }

    @Test
    void requiresReadyProfileAndPassingMatchForOfficialProviderSubmission() {
        stubProfileAndJob();
        ApplicationSubmissionConnector connector = mock(ApplicationSubmissionConnector.class);
        when(connector.supports(job)).thenReturn(true);
        when(preparationService.validateCoverLetter("job-1", "Reviewed letter"))
                .thenReturn(supportedClaims());
        when(preparationService.reviewActiveProfile())
                .thenReturn(new ProfileReviewDto(80, 4, 5, true, "NEEDS_REVIEW", List.of()));
        ApplicationSubmissionService service = createService(List.of(connector));

        assertThrows(IllegalArgumentException.class,
                () -> service.submit("job-1", new ApplicationSubmissionRequest(true, "Reviewed letter")));

        verify(matchRepository, never()).findByJobIdAndCandidateProfileId(any(), any());
        verify(connector, never()).submit(any(), any(), any(), any());
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void submitsThroughEligibleOfficialProviderAndMarksJobApplied() {
        stubProfileAndJob();
        ApplicationSubmissionConnector connector = mock(ApplicationSubmissionConnector.class);
        when(connector.supports(job)).thenReturn(true);
        when(connector.getProviderId()).thenReturn("official-ats");
        when(connector.submit(eq(job), eq(profile), eq("Reviewed letter"), any()))
                .thenReturn(new ApplicationSubmissionConnector.SubmissionReceipt("receipt-123", "Confirmed."));
        when(preparationService.validateCoverLetter("job-1", "Reviewed letter"))
                .thenReturn(supportedClaims());
        when(preparationService.reviewActiveProfile())
                .thenReturn(new ProfileReviewDto(100, 5, 5, true, "READY", List.of()));
        when(matchRepository.findByJobIdAndCandidateProfileId("job-1", "profile-1"))
                .thenReturn(Optional.of(new JobMatchResult()));
        when(mongoTemplate.count(any(), eq(ApplicationSubmission.class))).thenReturn(0L);
        when(submissionRepository.save(any(ApplicationSubmission.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ApplicationSubmissionService service = createService(List.of(connector));

        ApplicationSubmissionDto result =
                service.submit("job-1", new ApplicationSubmissionRequest(true, "Reviewed letter"));

        assertEquals(ApplicationSubmissionStatus.SUBMITTED, result.status());
        assertEquals("official-ats", result.provider());
        assertEquals("receipt-123", result.providerReference());
        assertEquals("APPLIED", job.getStatus());
        verify(jobRepository).save(job);
    }

    @Test
    void blocksOfficialProviderSubmissionWhenHardCriteriaFail() {
        stubProfileAndJob();
        ApplicationSubmissionConnector connector = mock(ApplicationSubmissionConnector.class);
        when(connector.supports(job)).thenReturn(true);
        when(preparationService.validateCoverLetter("job-1", "Reviewed letter"))
                .thenReturn(supportedClaims());
        when(preparationService.reviewActiveProfile())
                .thenReturn(new ProfileReviewDto(100, 5, 5, true, "READY", List.of()));
        JobMatchResult failedMatch = new JobMatchResult();
        failedMatch.setHardCriteriaMet(false);
        when(matchRepository.findByJobIdAndCandidateProfileId("job-1", "profile-1"))
                .thenReturn(Optional.of(failedMatch));
        ApplicationSubmissionService service = createService(List.of(connector));

        assertThrows(IllegalArgumentException.class,
                () -> service.submit("job-1", new ApplicationSubmissionRequest(true, "Reviewed letter")));

        verify(connector, never()).submit(any(), any(), any(), any());
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void recordsUncertainProviderFailureWithoutMarkingJobApplied() {
        stubProfileAndJob();
        ApplicationSubmissionConnector connector = mock(ApplicationSubmissionConnector.class);
        when(connector.supports(job)).thenReturn(true);
        when(connector.getProviderId()).thenReturn("official-ats");
        when(connector.submit(eq(job), eq(profile), eq("Reviewed letter"), any()))
                .thenThrow(new IllegalStateException("Provider unavailable"));
        when(preparationService.validateCoverLetter("job-1", "Reviewed letter"))
                .thenReturn(supportedClaims());
        when(preparationService.reviewActiveProfile())
                .thenReturn(new ProfileReviewDto(100, 5, 5, true, "READY", List.of()));
        when(matchRepository.findByJobIdAndCandidateProfileId("job-1", "profile-1"))
                .thenReturn(Optional.of(new JobMatchResult()));
        when(mongoTemplate.count(any(), eq(ApplicationSubmission.class))).thenReturn(0L);
        when(submissionRepository.save(any(ApplicationSubmission.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ApplicationSubmissionService service = createService(List.of(connector));

        ApplicationSubmissionDto result =
                service.submit("job-1", new ApplicationSubmissionRequest(true, "Reviewed letter"));

        assertEquals(ApplicationSubmissionStatus.FAILED, result.status());
        assertTrue(result.message().contains("Check with the provider"));
        verify(jobRepository, never()).save(any());
    }

    @Test
    void rejectsUnsupportedClaimsBeforeCreatingSubmission() {
        stubProfileAndJob();
        when(preparationService.validateCoverLetter("job-1", "Unsupported claim"))
                .thenReturn(new TruthfulnessValidationDto(
                        false, 1, List.of("Unsupported claim"), "Review claims."));
        ApplicationSubmissionService service = createService(List.of());

        assertThrows(IllegalArgumentException.class,
                () -> service.submit("job-1", new ApplicationSubmissionRequest(true, "Unsupported claim")));

        verify(mongoTemplate, never()).count(any(), eq(ApplicationSubmission.class));
        verify(submissionRepository, never()).save(any());
    }

    private ApplicationSubmissionService createService(List<ApplicationSubmissionConnector> connectors) {
        return new ApplicationSubmissionServiceImpl(
                profileRepository,
                jobRepository,
                matchRepository,
                submissionRepository,
                preparationService,
                mongoTemplate,
                connectors);
    }

    private void stubProfileAndJob() {
        when(profileRepository.findTopByOrderByUpdatedAtDesc()).thenReturn(Optional.of(profile));
        when(jobRepository.findById("job-1")).thenReturn(Optional.of(job));
        when(submissionRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
    }

    private TruthfulnessValidationDto supportedClaims() {
        return new TruthfulnessValidationDto(true, 1, List.of(), "Review all claims before applying.");
    }
}
