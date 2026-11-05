package com.aijobagent.service;

import com.aijobagent.dto.automation.AutomationStatusDto;
import com.aijobagent.dto.automation.PortalLinksConfigResponse;
import com.aijobagent.dto.job.DiscoveryRunResult;
import com.aijobagent.dto.job.PortalLinkDiscoveryItem;
import com.aijobagent.dto.job.PortalLinkDiscoveryResult;
import com.aijobagent.dto.matching.BatchMatchResult;
import com.aijobagent.model.AutomationRun;
import com.aijobagent.model.AutomationState;
import com.aijobagent.model.AutomationStatus;
import com.aijobagent.model.CandidateProfile;
import com.aijobagent.repository.AutomationRunRepository;
import com.aijobagent.repository.AutomationStateRepository;
import com.aijobagent.repository.CandidateProfileRepository;
import com.aijobagent.service.impl.AutomationServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class AutomationServiceTest {

    @Mock
    private AutomationStateRepository stateRepository;

    @Mock
    private AutomationRunRepository runRepository;

    @Mock
    private CandidateProfileRepository profileRepository;

    @Mock
    private JobDiscoveryService discoveryService;

    @Mock
    private JobMatchingService matchingService;

    @Mock
    private ObjectProvider<JavaMailSender> mailSenderProvider;

    @Test
    void defaultsToStoppedUntilUserStartsAutomation() {
        when(stateRepository.findById(AutomationState.ACTIVE_STATE_ID)).thenReturn(Optional.empty());
        AutomationService service = createService();

        AutomationStatusDto result = service.getStatus();

        assertEquals(AutomationStatus.STOPPED, result.status());
        assertEquals(360, result.intervalMinutes());
        verifyNoInteractions(discoveryService, matchingService);
    }

    @Test
    void startPersistsRunningStateAndSchedulesAnImmediateFirstCycle() {
        when(stateRepository.findById(AutomationState.ACTIVE_STATE_ID)).thenReturn(Optional.empty());
        when(stateRepository.save(any(AutomationState.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AutomationService service = createService();

        AutomationStatusDto result = service.start();

        assertEquals(AutomationStatus.RUNNING, result.status());
        assertEquals("RUNNING", result.status().name());
        assertNull(result.lastRunAt());
        assertTrue(result.nextRunAt() != null && !result.nextRunAt().isAfter(Instant.now()));
    }

    @Test
    void savesOnlySupportedPortalBoardsAndNormalizesJobLinksToTheirBoard() {
        when(stateRepository.findById(AutomationState.ACTIVE_STATE_ID)).thenReturn(Optional.empty());
        when(stateRepository.save(any(AutomationState.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PortalLinksConfigResponse result = createService().savePortalLinks(List.of(
                "https://boards.greenhouse.io/example/jobs/123",
                "https://boards.greenhouse.io/example",
                "https://www.linkedin.com/jobs/search/"));

        assertEquals(List.of("https://boards.greenhouse.io/example"), result.savedPortalUrls());
        assertEquals(List.of("https://www.linkedin.com/jobs/search/"), result.unsupportedPortalUrls());
    }

    @Test
    void scheduledRunDiscoversAndMatchesJobsAndPersistsAlertHistory() {
        AutomationState running = new AutomationState();
        running.setStatus(AutomationStatus.RUNNING);
        running.setNextRunAt(Instant.now().minusSeconds(1));
        when(stateRepository.findById(AutomationState.ACTIVE_STATE_ID)).thenReturn(Optional.of(running));
        when(stateRepository.save(any(AutomationState.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CandidateProfile profile = new CandidateProfile();
        profile.setId("profile-1");
        profile.setEmail("candidate@example.com");
        when(profileRepository.findTopByOrderByUpdatedAtDesc()).thenReturn(Optional.of(profile));
        when(discoveryService.runDiscovery(null)).thenReturn(new DiscoveryRunResult(
                true, "Discovery completed.", Instant.now(), Instant.now(), 100, 8, 3, 5, Map.of("REMOTE_OK", 8)));
        when(matchingService.evaluateAllDiscoveredJobs("profile-1", 50)).thenReturn(new BatchMatchResult(
                true, "Evaluated 8 jobs.", 8, 2, 3, 3, 5, 3, 65.0, 100));
        when(runRepository.save(any(AutomationRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AutomationService service = createService();

        service.runIfDue();

        verify(discoveryService).runDiscovery(null);
        verify(matchingService).evaluateAllDiscoveredJobs("profile-1", 50);
        ArgumentCaptor<AutomationRun> runCaptor = ArgumentCaptor.forClass(AutomationRun.class);
        verify(runRepository).save(runCaptor.capture());
        AutomationRun saved = runCaptor.getValue();
        assertTrue(saved.isSuccess());
        assertEquals(3, saved.getNewJobs());
        assertEquals(8, saved.getJobsEvaluated());
        assertEquals(2, saved.getStrongMatches());
        assertEquals("IN_APP_ONLY_EMAIL_NOT_CONFIGURED", saved.getEmailStatus());
        assertEquals(AutomationStatus.RUNNING, running.getStatus());
        assertEquals(3, running.getLastNewJobs());
    }

    @Test
    void scheduledRunScansSavedPortalBoardsBeforeMatching() {
        AutomationState running = new AutomationState();
        running.setStatus(AutomationStatus.RUNNING);
        running.setNextRunAt(Instant.now().minusSeconds(1));
        running.setPortalUrls(List.of("https://boards.greenhouse.io/example"));
        when(stateRepository.findById(AutomationState.ACTIVE_STATE_ID)).thenReturn(Optional.of(running));
        when(stateRepository.save(any(AutomationState.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(discoveryService.runDiscovery(null)).thenReturn(new DiscoveryRunResult(
                true, "Discovery completed.", Instant.now(), Instant.now(), 10, 0, 0, 0, Map.of()));
        when(discoveryService.discoverPortalLinks(running.getPortalUrls())).thenReturn(
                new PortalLinkDiscoveryResult(Instant.now(), Instant.now(), 20, 1, 1, 0,
                        List.of(new PortalLinkDiscoveryItem(
                                "https://boards.greenhouse.io/example", "GREENHOUSE", "SCANNED",
                                "Scanned.", 1, 1, 0))));
        CandidateProfile profile = new CandidateProfile();
        profile.setId("profile-1");
        when(profileRepository.findTopByOrderByUpdatedAtDesc()).thenReturn(Optional.of(profile));
        when(matchingService.evaluateAllDiscoveredJobs("profile-1", 50)).thenReturn(new BatchMatchResult(
                true, "Evaluated 1 job.", 1, 1, 0, 0, 1, 0, 90.0, 10));
        when(runRepository.save(any(AutomationRun.class))).thenAnswer(invocation -> invocation.getArgument(0));

        createService().runIfDue();

        verify(discoveryService).discoverPortalLinks(List.of("https://boards.greenhouse.io/example"));
        verify(matchingService).evaluateAllDiscoveredJobs("profile-1", 50);
        ArgumentCaptor<AutomationRun> runCaptor = ArgumentCaptor.forClass(AutomationRun.class);
        verify(runRepository).save(runCaptor.capture());
        assertEquals(1, runCaptor.getValue().getNewJobs());
        assertTrue(runCaptor.getValue().isSuccess());
    }

    @Test
    void sendsEmailAlertWhenEmailAndSmtpAreConfigured() {
        AutomationState running = new AutomationState();
        running.setStatus(AutomationStatus.RUNNING);
        running.setNextRunAt(Instant.now().minusSeconds(1));
        when(stateRepository.findById(AutomationState.ACTIVE_STATE_ID)).thenReturn(Optional.of(running));
        when(stateRepository.save(any(AutomationState.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CandidateProfile profile = new CandidateProfile();
        profile.setId("profile-1");
        profile.setEmail("candidate@example.com");
        when(profileRepository.findTopByOrderByUpdatedAtDesc()).thenReturn(Optional.of(profile));
        when(discoveryService.runDiscovery(null)).thenReturn(new DiscoveryRunResult(
                true, "Discovery completed.", Instant.now(), Instant.now(), 100, 8, 3, 5, Map.of()));
        when(matchingService.evaluateAllDiscoveredJobs("profile-1", 50)).thenReturn(new BatchMatchResult(
                true, "Evaluated 8 jobs.", 8, 2, 3, 3, 5, 3, 65.0, 100));
        when(runRepository.save(any(AutomationRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        AutomationService service = createService(true, "smtp.example.com");

        service.runIfDue();

        ArgumentCaptor<SimpleMailMessage> emailCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(emailCaptor.capture());
        assertEquals("candidate@example.com", emailCaptor.getValue().getTo()[0]);
        assertTrue(emailCaptor.getValue().getSubject().contains("3 new job"));
        ArgumentCaptor<AutomationRun> runCaptor = ArgumentCaptor.forClass(AutomationRun.class);
        verify(runRepository).save(runCaptor.capture());
        assertEquals("SENT", runCaptor.getValue().getEmailStatus());
    }

    @Test
    void pausedAutomationDoesNotCallJobSources() {
        AutomationState paused = new AutomationState();
        paused.setStatus(AutomationStatus.PAUSED);
        when(stateRepository.findById(AutomationState.ACTIVE_STATE_ID)).thenReturn(Optional.of(paused));

        createService().runIfDue();

        verifyNoInteractions(discoveryService, matchingService, runRepository);
    }

    @Test
    void doesNotRunBeforeNextScheduledTime() {
        AutomationState running = new AutomationState();
        running.setStatus(AutomationStatus.RUNNING);
        running.setNextRunAt(Instant.now().plusSeconds(120));
        when(stateRepository.findById(AutomationState.ACTIVE_STATE_ID)).thenReturn(Optional.of(running));

        createService().runIfDue();

        verifyNoInteractions(discoveryService, matchingService, runRepository);
    }

    private AutomationService createService() {
        return createService(false, "");
    }

    private AutomationService createService(boolean emailEnabled, String smtpHost) {
        return new AutomationServiceImpl(
                stateRepository,
                runRepository,
                profileRepository,
                discoveryService,
                matchingService,
                mailSenderProvider,
                21_600_000,
                50,
                emailEnabled,
                smtpHost,
                "");
    }
}
