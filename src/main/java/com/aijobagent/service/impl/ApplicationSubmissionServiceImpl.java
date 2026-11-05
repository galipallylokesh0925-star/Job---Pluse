package com.aijobagent.service.impl;

import com.aijobagent.dto.applicationprep.ApplicationSubmissionDto;
import com.aijobagent.dto.applicationprep.ApplicationSubmissionRequest;
import com.aijobagent.dto.applicationprep.ProfileReviewDto;
import com.aijobagent.dto.applicationprep.TruthfulnessValidationDto;
import com.aijobagent.exception.ApplicationRateLimitExceededException;
import com.aijobagent.exception.ResourceNotFoundException;
import com.aijobagent.model.ApplicationSubmission;
import com.aijobagent.model.ApplicationSubmissionStatus;
import com.aijobagent.model.CandidateProfile;
import com.aijobagent.model.JobMatchResult;
import com.aijobagent.model.JobPosting;
import com.aijobagent.repository.ApplicationSubmissionRepository;
import com.aijobagent.repository.CandidateProfileRepository;
import com.aijobagent.repository.JobMatchRepository;
import com.aijobagent.repository.JobPostingRepository;
import com.aijobagent.service.ApplicationPreparationService;
import com.aijobagent.service.ApplicationSubmissionConnector;
import com.aijobagent.service.ApplicationSubmissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class ApplicationSubmissionServiceImpl implements ApplicationSubmissionService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationSubmissionServiceImpl.class);
    private static final String LINK_OUT_MESSAGE =
            "No documented official application API is configured for this employer. Continue through the employer application page.";

    private final CandidateProfileRepository profileRepository;
    private final JobPostingRepository jobRepository;
    private final JobMatchRepository matchRepository;
    private final ApplicationSubmissionRepository submissionRepository;
    private final ApplicationPreparationService preparationService;
    private final MongoTemplate mongoTemplate;
    private final List<ApplicationSubmissionConnector> connectors;

    public ApplicationSubmissionServiceImpl(
            CandidateProfileRepository profileRepository,
            JobPostingRepository jobRepository,
            JobMatchRepository matchRepository,
            ApplicationSubmissionRepository submissionRepository,
            ApplicationPreparationService preparationService,
            MongoTemplate mongoTemplate,
            List<ApplicationSubmissionConnector> connectors) {
        this.profileRepository = profileRepository;
        this.jobRepository = jobRepository;
        this.matchRepository = matchRepository;
        this.submissionRepository = submissionRepository;
        this.preparationService = preparationService;
        this.mongoTemplate = mongoTemplate;
        this.connectors = connectors;
    }

    @Override
    public synchronized ApplicationSubmissionDto submit(String jobId, ApplicationSubmissionRequest request) {
        if (request == null || !request.confirmed()) {
            throw new IllegalArgumentException("Explicit confirmation is required for every application.");
        }

        CandidateProfile profile = profileRepository.findTopByOrderByUpdatedAtDesc()
                .orElseThrow(() -> new ResourceNotFoundException("No candidate profile is available."));
        if (profile.getId() == null || profile.getId().isBlank()) {
            throw new IllegalStateException("The active candidate profile must be saved before applying.");
        }
        JobPosting job = jobRepository.findById(Objects.requireNonNull(jobId, "jobId"))
                .orElseThrow(() -> new ResourceNotFoundException("Job posting not found: " + jobId));

        String idempotencyKey = createIdempotencyKey(profile.getId(), job.getId());
        Optional<ApplicationSubmission> previous = submissionRepository.findByIdempotencyKey(idempotencyKey);
        if (previous.isPresent()) {
            return toDto(previous.get(), job, true);
        }

        TruthfulnessValidationDto truthfulness =
                preparationService.validateCoverLetter(job.getId(), request.coverLetter());
        if (!truthfulness.allClaimsSupported()) {
            throw new IllegalArgumentException(
                    "The cover letter contains likely unsupported claims. Review the Phase 5 claim check before applying.");
        }

        Optional<ApplicationSubmissionConnector> connector = connectors.stream()
                .filter(candidate -> candidate.supports(job))
                .findFirst();
        if (connector.isPresent()) {
            requireAutomationEligibility(profile, job);
        }
        enforceDailyLimit(profile);

        ApplicationSubmission submission = new ApplicationSubmission();
        submission.setCandidateProfileId(profile.getId());
        submission.setJobId(job.getId());
        submission.setIdempotencyKey(idempotencyKey);
        submission.setStatus(ApplicationSubmissionStatus.PROCESSING);
        submission.setApplicationUrl(safeApplicationUrl(job.getApplyUrl()));
        try {
            submission = submissionRepository.save(submission);
        } catch (DuplicateKeyException exception) {
            return submissionRepository.findByIdempotencyKey(idempotencyKey)
                    .map(existing -> toDto(existing, job, true))
                    .orElseThrow(() -> exception);
        }

        if (connector.isEmpty()) {
            completeAsLinkOut(submission, LINK_OUT_MESSAGE);
        } else {
            submitThroughOfficialConnector(submission, connector.get(), job, profile, request.coverLetter());
        }

        ApplicationSubmission completed = submissionRepository.save(submission);
        if (completed.getStatus() == ApplicationSubmissionStatus.SUBMITTED) {
            job.setStatus("APPLIED");
            jobRepository.save(job);
        }
        return toDto(completed, job, false);
    }

    private void requireAutomationEligibility(CandidateProfile profile, JobPosting job) {
        ProfileReviewDto review = preparationService.reviewActiveProfile();
        if (!"READY".equals(review.overallStatus())) {
            throw new IllegalArgumentException(
                    "Automated submission requires a READY profile review. Resolve the profile QA issues first.");
        }
        JobMatchResult match = matchRepository.findByJobIdAndCandidateProfileId(job.getId(), profile.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Automated submission requires an evaluated job match."));
        if (!match.isHardCriteriaMet()) {
            throw new IllegalArgumentException(
                    "Automated submission is blocked because this job did not pass the hard-criteria check.");
        }
    }

    private void enforceDailyLimit(CandidateProfile profile) {
        int dailyLimit = profile.getPreferences() == null
                ? 20
                : profile.getPreferences().getMaxDailyApplications();
        if (dailyLimit < 1) {
            throw new IllegalArgumentException("The daily application limit must be at least one.");
        }
        Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant startOfNextDay = LocalDate.now(ZoneOffset.UTC).plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Query dailySubmissions = Query.query(new Criteria().andOperator(
                Criteria.where("candidateProfileId").is(profile.getId()),
                Criteria.where("createdAt")
                        .gte(Objects.requireNonNull(startOfDay))
                        .lt(Objects.requireNonNull(startOfNextDay))));
        long count = mongoTemplate.count(dailySubmissions, ApplicationSubmission.class);
        if (count >= dailyLimit) {
            throw new ApplicationRateLimitExceededException(
                    "Daily application limit reached (" + dailyLimit + "). Try again after 00:00 UTC.");
        }
    }

    private void submitThroughOfficialConnector(
            ApplicationSubmission submission,
            ApplicationSubmissionConnector connector,
            JobPosting job,
            CandidateProfile profile,
            String coverLetter) {
        try {
            ApplicationSubmissionConnector.SubmissionReceipt receipt =
                    connector.submit(job, profile, coverLetter, submission.getIdempotencyKey());
            if (receipt == null) {
                throw new IllegalStateException("The application provider returned no submission receipt.");
            }
            submission.setStatus(ApplicationSubmissionStatus.SUBMITTED);
            submission.setProvider(connector.getProviderId());
            submission.setProviderReference(receipt.confirmationReference());
            submission.setMessage(receipt.message() == null || receipt.message().isBlank()
                    ? "The official application provider confirmed submission."
                    : receipt.message());
            submission.setCompletedAt(Instant.now());
        } catch (Exception exception) {
            log.error("Official application provider {} failed for job {}",
                    connector.getProviderId(), job.getId(), exception);
            submission.setStatus(ApplicationSubmissionStatus.FAILED);
            submission.setMessage(
                    "The official provider could not confirm the submission result. Check with the provider before retrying to avoid duplicate applications.");
            submission.setCompletedAt(Instant.now());
        }
    }

    private void completeAsLinkOut(ApplicationSubmission submission, String message) {
        submission.setStatus(submission.getApplicationUrl() == null
                ? ApplicationSubmissionStatus.FAILED
                : ApplicationSubmissionStatus.LINK_OUT);
        submission.setMessage(submission.getApplicationUrl() == null
                ? "No safe HTTP(S) employer application URL is available. The application was not submitted."
                : message);
        submission.setCompletedAt(Instant.now());
    }

    private ApplicationSubmissionDto toDto(
            ApplicationSubmission submission, JobPosting job, boolean alreadyProcessed) {
        String message = submission.getStatus() == ApplicationSubmissionStatus.PROCESSING
                ? "This application request is still processing. Do not retry until its status is checked."
                : submission.getMessage();
        return new ApplicationSubmissionDto(
                submission.getId(),
                submission.getJobId(),
                job.getTitle(),
                job.getCompany(),
                submission.getStatus(),
                submission.getProvider(),
                submission.getProviderReference(),
                submission.getApplicationUrl(),
                message,
                submission.getIdempotencyKey(),
                alreadyProcessed,
                submission.getCreatedAt(),
                submission.getCompletedAt());
    }

    private String safeApplicationUrl(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme();
            if (uri.getHost() == null
                    || uri.getUserInfo() != null
                    || scheme == null
                    || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                return null;
            }
            return uri.toASCIIString();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String createIdempotencyKey(String profileId, String jobId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((profileId + ":" + jobId).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required for application idempotency.", exception);
        }
    }
}
