package com.aijobagent.service.impl;

import com.aijobagent.dto.automation.AutomationRunDto;
import com.aijobagent.dto.automation.AutomationStatusDto;
import com.aijobagent.dto.automation.PortalLinksConfigResponse;
import com.aijobagent.connector.PortalBoardUrl;
import com.aijobagent.dto.job.DiscoveryRunResult;
import com.aijobagent.dto.job.PortalLinkDiscoveryResult;
import com.aijobagent.dto.matching.BatchMatchResult;
import com.aijobagent.model.AutomationRun;
import com.aijobagent.model.AutomationState;
import com.aijobagent.model.AutomationStatus;
import com.aijobagent.model.CandidateProfile;
import com.aijobagent.repository.AutomationRunRepository;
import com.aijobagent.repository.AutomationStateRepository;
import com.aijobagent.repository.CandidateProfileRepository;
import com.aijobagent.service.AutomationService;
import com.aijobagent.service.JobDiscoveryService;
import com.aijobagent.service.JobMatchingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class AutomationServiceImpl implements AutomationService {

    private static final Logger log = LoggerFactory.getLogger(AutomationServiceImpl.class);
    private static final long DEFAULT_INTERVAL_MILLIS = Duration.ofHours(6).toMillis();

    private final AutomationStateRepository stateRepository;
    private final AutomationRunRepository runRepository;
    private final CandidateProfileRepository profileRepository;
    private final JobDiscoveryService discoveryService;
    private final JobMatchingService matchingService;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final long intervalMillis;
    private final int defaultMinScore;
    private final boolean emailEnabled;
    private final String smtpHost;
    private final String fromAddress;

    public AutomationServiceImpl(
            AutomationStateRepository stateRepository,
            AutomationRunRepository runRepository,
            CandidateProfileRepository profileRepository,
            JobDiscoveryService discoveryService,
            JobMatchingService matchingService,
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${app.automation.interval-ms:21600000}") long intervalMillis,
            @Value("${app.matching.default-min-score:50}") int defaultMinScore,
            @Value("${app.automation.email.enabled:false}") boolean emailEnabled,
            @Value("${spring.mail.host:}") String smtpHost,
            @Value("${app.automation.email.from:}") String fromAddress) {
        this.stateRepository = stateRepository;
        this.runRepository = runRepository;
        this.profileRepository = profileRepository;
        this.discoveryService = discoveryService;
        this.matchingService = matchingService;
        this.mailSenderProvider = mailSenderProvider;
        this.intervalMillis = intervalMillis > 0 ? intervalMillis : DEFAULT_INTERVAL_MILLIS;
        this.defaultMinScore = defaultMinScore;
        this.emailEnabled = emailEnabled;
        this.smtpHost = smtpHost;
        this.fromAddress = fromAddress;
    }

    @Override
    public AutomationStatusDto getStatus() {
        return toStatus(getOrDefaultState());
    }

    @Override
    public AutomationStatusDto start() {
        AutomationState state = getOrDefaultState();
        if (state.getStatus() == AutomationStatus.RUNNING) {
            return toStatus(state);
        }
        Instant now = Instant.now();
        state.setStatus(AutomationStatus.RUNNING);
        state.setStartedAt(now);
        Instant nextRun = state.getLastRunAt() == null
                ? now
                : state.getLastRunAt().plusMillis(intervalMillis);
        state.setNextRunAt(nextRun.isAfter(now) ? nextRun : now);
        state.setLastMessage("Automation started. Discovery and matching are scheduled; applications still require confirmation.");
        return toStatus(stateRepository.save(state));
    }

    @Override
    public AutomationStatusDto pause() {
        AutomationState state = getOrDefaultState();
        state.setStatus(AutomationStatus.PAUSED);
        state.setNextRunAt(null);
        state.setLastMessage("Automation paused. Any currently running cycle will finish.");
        return toStatus(stateRepository.save(state));
    }

    @Override
    public AutomationStatusDto stop() {
        AutomationState state = getOrDefaultState();
        state.setStatus(AutomationStatus.STOPPED);
        state.setNextRunAt(null);
        state.setLastMessage("Automation stopped. No scheduled discovery or matching will run.");
        return toStatus(stateRepository.save(state));
    }

    @Override
    public List<AutomationRunDto> getRecentRuns() {
        return runRepository.findTop20ByOrderByStartedAtDesc().stream()
                .map(this::toRunDto)
                .toList();
    }

    @Override
    public PortalLinksConfigResponse getPortalLinks() {
        return new PortalLinksConfigResponse(getOrDefaultState().getPortalUrls(), List.of());
    }

    @Override
    public PortalLinksConfigResponse savePortalLinks(List<String> portalUrls) {
        if (portalUrls == null || portalUrls.size() > 20) {
            throw new IllegalArgumentException("Supply at most 20 portal links.");
        }

        LinkedHashSet<String> supportedBoardUrls = new LinkedHashSet<>();
        List<String> unsupported = new ArrayList<>();
        for (String value : portalUrls) {
            PortalBoardUrl.parse(value)
                    .ifPresentOrElse(
                            parsed -> supportedBoardUrls.add(parsed.boardUrl()),
                            () -> unsupported.add(value == null ? "" : value.trim()));
        }

        AutomationState state = getOrDefaultState();
        state.setPortalUrls(List.copyOf(supportedBoardUrls));
        AutomationState saved = stateRepository.save(state);
        log.info("Saved {} public ATS boards for scheduled discovery; {} unsupported links were not saved.",
                saved.getPortalUrls().size(), unsupported.size());
        return new PortalLinksConfigResponse(saved.getPortalUrls(), List.copyOf(unsupported));
    }

    @Override
    public synchronized void runIfDue() {
        AutomationState state = getOrDefaultState();
        Instant now = Instant.now();
        if (state.getStatus() != AutomationStatus.RUNNING
                || (state.getNextRunAt() != null && state.getNextRunAt().isAfter(now))) {
            return;
        }

        state.setNextRunAt(now.plusMillis(intervalMillis));
        stateRepository.save(state);
        executeCycle(now, state.getPortalUrls());
    }

    private void executeCycle(Instant startedAt, List<String> portalUrls) {
        AutomationRun run = new AutomationRun();
        run.setStartedAt(startedAt);
        run.setSuccess(false);
        run.setEmailStatus("NO_NEW_JOBS");
        StringBuilder message = new StringBuilder();
        boolean portalScansSuccessful = true;

        try {
            DiscoveryRunResult discovery = discoveryService.runDiscovery(null);
            run.setNewJobs(discovery.newJobsSaved());
            run.setTotalFetched(discovery.totalFetched());
            run.setDuplicateJobs(discovery.duplicateJobsSkipped());
            message.append(discovery.message());
            if (!portalUrls.isEmpty()) {
                PortalLinkDiscoveryResult portalResults = discoveryService.discoverPortalLinks(portalUrls);
                portalScansSuccessful = portalResults.results().stream()
                        .allMatch(result -> result.status().equals("SCANNED"));
                run.setNewJobs(run.getNewJobs() + portalResults.newJobsSaved());
                run.setTotalFetched(run.getTotalFetched() + portalResults.totalFetched());
                run.setDuplicateJobs(run.getDuplicateJobs() + portalResults.duplicatesSkipped());
                message.append(" Scanned ").append(portalUrls.size())
                        .append(" saved public ATS board(s): ")
                        .append(portalResults.newJobsSaved()).append(" new job(s), ")
                        .append(portalResults.duplicatesSkipped()).append(" duplicate(s).");
                portalResults.results().stream()
                        .filter(result -> !result.status().equals("SCANNED"))
                        .forEach(result -> message.append(" ").append(result.source())
                                .append(" portal scan ").append(result.status().toLowerCase())
                                .append(": ").append(result.message()));
            }

            CandidateProfile profile = profileRepository.findTopByOrderByUpdatedAtDesc().orElse(null);
            if (profile != null && profile.getId() != null && !profile.getId().isBlank()) {
                BatchMatchResult matches =
                        matchingService.evaluateAllDiscoveredJobs(profile.getId(), defaultMinScore);
                run.setJobsEvaluated(matches.totalJobsEvaluated());
                run.setStrongMatches(matches.strongMatchesCount());
                message.append(" ").append(matches.message());
                if (!matches.success()) {
                    run.setSuccess(false);
                    message.append(" Matching reported an incomplete result.");
                } else {
                    run.setSuccess(discovery.success() && portalScansSuccessful);
                }
            } else {
                message.append(" Matching was skipped because no saved candidate profile is available.");
                run.setSuccess(discovery.success() && portalScansSuccessful);
            }

            if (run.getNewJobs() > 0) {
                run.setEmailStatus(sendNewJobsEmail(profile, run.getNewJobs(), run.getStrongMatches()));
            }
        } catch (Exception exception) {
            log.error("Scheduled discovery/matching cycle failed", exception);
            message.append(" Automation cycle failed: ").append(exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage());
        }

        Instant completedAt = Instant.now();
        run.setCompletedAt(completedAt);
        run.setMessage(message.toString());
        AutomationRun savedRun = runRepository.save(run);

        AutomationState currentState = getOrDefaultState();
        currentState.setLastRunAt(completedAt);
        currentState.setLastNewJobs(savedRun.getNewJobs());
        currentState.setLastMessage(savedRun.getMessage());
        currentState.setNextRunAt(currentState.getStatus() == AutomationStatus.RUNNING
                ? completedAt.plusMillis(intervalMillis)
                : null);
        stateRepository.save(currentState);
    }

    private String sendNewJobsEmail(CandidateProfile profile, int newJobs, int strongMatches) {
        if (!emailEnabled || smtpHost.isBlank()) {
            return "IN_APP_ONLY_EMAIL_NOT_CONFIGURED";
        }
        if (profile == null || profile.getEmail() == null || profile.getEmail().isBlank()) {
            return "IN_APP_ONLY_NO_PROFILE_EMAIL";
        }
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            return "IN_APP_ONLY_EMAIL_SENDER_UNAVAILABLE";
        }

        SimpleMailMessage email = new SimpleMailMessage();
        if (!fromAddress.isBlank()) {
            email.setFrom(fromAddress);
        }
        email.setTo(profile.getEmail());
        email.setSubject("AI Job Agent found " + newJobs + " new job posting(s)");
        email.setText("The latest scheduled scan found " + newJobs + " new job posting(s). "
                + strongMatches + " strong match(es) were identified. Open the dashboard to review them. "
                + "No applications were submitted automatically.");
        try {
            mailSender.send(email);
            return "SENT";
        } catch (RuntimeException exception) {
            log.error("Failed to send scheduled new-job notification to the configured profile address", exception);
            return "FAILED";
        }
    }

    private AutomationState getOrDefaultState() {
        return stateRepository.findById(AutomationState.ACTIVE_STATE_ID)
                .orElseGet(AutomationState::new);
    }

    private AutomationStatusDto toStatus(AutomationState state) {
        return new AutomationStatusDto(
                state.getStatus(),
                state.getStartedAt(),
                state.getLastRunAt(),
                state.getNextRunAt(),
                state.getLastMessage(),
                state.getLastNewJobs(),
                Duration.ofMillis(intervalMillis).toMinutes(),
                emailEnabled && !smtpHost.isBlank() && mailSenderProvider.getIfAvailable() != null);
    }

    private AutomationRunDto toRunDto(AutomationRun run) {
        return new AutomationRunDto(
                run.getId(),
                run.getStartedAt(),
                run.getCompletedAt(),
                run.isSuccess(),
                run.getNewJobs(),
                run.getTotalFetched(),
                run.getDuplicateJobs(),
                run.getJobsEvaluated(),
                run.getStrongMatches(),
                run.getMessage(),
                run.getEmailStatus());
    }
}
