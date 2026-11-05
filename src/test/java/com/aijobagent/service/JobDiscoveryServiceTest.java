package com.aijobagent.service;

import com.aijobagent.connector.JobSourceConnector;
import com.aijobagent.connector.PortalJobSourceConnector;
import com.aijobagent.dto.job.DiscoveryRunResult;
import com.aijobagent.dto.job.JobAlertSource;
import com.aijobagent.dto.job.JobImportRequest;
import com.aijobagent.dto.job.JobImportResult;
import com.aijobagent.dto.job.JobStatsDto;
import com.aijobagent.dto.job.PortalLinkDiscoveryResult;
import com.aijobagent.dto.profile.CandidateProfileDto;
import com.aijobagent.model.JobPosting;
import com.aijobagent.model.JobPreferences;
import com.aijobagent.repository.JobPostingRepository;
import com.aijobagent.service.impl.JobDiscoveryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobDiscoveryServiceTest {

    @Mock
    private JobPostingRepository jobRepository;

    @Mock
    private CandidateProfileService profileService;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private JobSourceConnector mockConnector;

    private JobDiscoveryService discoveryService;

    @BeforeEach
    void setUp() {
        lenient().when(mockConnector.getSourceId()).thenReturn("TEST_SOURCE");
        lenient().when(mockConnector.getDisplayName()).thenReturn("Test Connector");
        lenient().when(mockConnector.isEnabled()).thenReturn(true);

        discoveryService = new JobDiscoveryServiceImpl(
                List.of(mockConnector),
                jobRepository,
                profileService,
                mongoTemplate
        );
    }

    @Test
    @DisplayName("Discovery run executes deduplication and prevents saving existing postings")
    @SuppressWarnings("null")
    void shouldDeduplicateIdenticalJobPostings() {
        // Arrange
        CandidateProfileDto profile = new CandidateProfileDto(
                "p-1", "Jane", "jane@example.com", null, null, null, null, null, null,
                List.of(), List.of(), List.of(), List.of(), List.of(),
                new JobPreferences(), null, false, Instant.now(), Instant.now()
        );
        when(profileService.getActiveProfile()).thenReturn(profile);

        JobPosting job1 = new JobPosting();
        job1.setTitle("Junior Java Engineer");
        job1.setCompany("Acme Corp");
        job1.setApplyUrl("https://example.com/jobs/1");
        job1.setSource("TEST_SOURCE");

        JobPosting job2 = new JobPosting();
        job2.setTitle("Junior Python Engineer");
        job2.setCompany("Beta Corp");
        job2.setApplyUrl("https://example.com/jobs/2");
        job2.setSource("TEST_SOURCE");

        when(mockConnector.fetchJobs(any())).thenReturn(List.of(job1, job2));

        // First hash exists (duplicate), second hash does not exist (new job)
        when(jobRepository.existsByDedupHash(anyString()))
                .thenReturn(true)  // job1 is duplicate
                .thenReturn(false); // job2 is fresh

        // Act
        DiscoveryRunResult result = discoveryService.runDiscovery(null);

        // Assert
        assertNotNull(result);
        assertTrue(result.success());
        assertEquals(2, result.totalFetched());
        assertEquals(1, result.newJobsSaved());
        assertEquals(1, result.duplicateJobsSkipped());
        verify(jobRepository, times(1)).save(any(JobPosting.class));
    }

    @Test
    @DisplayName("Imports a WhatsApp job alert and creates a source-independent deduplication key")
    @SuppressWarnings("null")
    void shouldImportJobAlert() {
        JobImportRequest request = new JobImportRequest(
                JobAlertSource.WHATSAPP_ALERT,
                "Junior Java Engineer",
                "Example Company",
                "https://careers.example.com/jobs/123",
                "Bengaluru",
                "HYBRID",
                "Java and Spring Boot experience preferred."
        );
        when(jobRepository.findFirstByApplyUrlIgnoreCase(request.applyUrl())).thenReturn(Optional.empty());
        when(jobRepository.existsByDedupHash(anyString())).thenReturn(false);
        when(jobRepository.save(any(JobPosting.class))).thenAnswer(invocation -> {
            JobPosting saved = invocation.getArgument(0);
            saved.setId("imported-job-1");
            return saved;
        });

        JobImportResult result = discoveryService.importJobAlert(request);

        assertTrue(result.success());
        assertFalse(result.duplicate());
        assertEquals("imported-job-1", result.job().id());
        assertEquals("WHATSAPP_ALERT", result.job().source());
        assertEquals("HYBRID", result.job().workMode());
        verify(jobRepository).save(any(JobPosting.class));
    }

    @Test
    @DisplayName("Returns the existing job when an alert repeats its application URL")
    @SuppressWarnings("null")
    void shouldReturnExistingJobForDuplicateAlert() {
        JobImportRequest request = new JobImportRequest(
                JobAlertSource.EMAIL_ALERT,
                "Java Engineer",
                "Example Company",
                "https://careers.example.com/jobs/123",
                "Bengaluru",
                "UNKNOWN",
                "Java role"
        );
        JobPosting existing = new JobPosting();
        existing.setId("existing-job");
        existing.setTitle("Java Engineer");
        existing.setCompany("Example Company");
        existing.setApplyUrl(request.applyUrl());
        existing.setSource("LINKEDIN_ALERT");
        when(jobRepository.findFirstByApplyUrlIgnoreCase(request.applyUrl())).thenReturn(Optional.of(existing));

        JobImportResult result = discoveryService.importJobAlert(request);

        assertTrue(result.duplicate());
        assertEquals("existing-job", result.job().id());
        verify(jobRepository, never()).save(any(JobPosting.class));
    }

    @Test
    @DisplayName("Rejects job alert application URLs that are not HTTP or HTTPS")
    @SuppressWarnings("null")
    void shouldRejectUnsafeJobApplicationUrls() {
        JobImportRequest request = new JobImportRequest(
                JobAlertSource.OTHER_ALERT,
                "Java Engineer",
                "Example Company",
                "javascript:alert(1)",
                "Bengaluru",
                "UNKNOWN",
                "Java role"
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> discoveryService.importJobAlert(request));

        assertTrue(exception.getMessage().contains("HTTP or HTTPS"));
        verifyNoInteractions(jobRepository);
    }

    @Test
    @DisplayName("Scans supported portal links and stores normalized postings for matching")
    @SuppressWarnings("null")
    void shouldDiscoverJobsFromSuppliedPortalLinks() {
        PortalJobSourceConnector portalConnector = mock(PortalJobSourceConnector.class);
        when(portalConnector.getSourceId()).thenReturn("GREENHOUSE");
        when(portalConnector.supportsPortalUrl("https://boards.greenhouse.io/example"))
                .thenReturn(true);
        JobPosting job = new JobPosting();
        job.setTitle("Java Engineer");
        job.setCompany("Example");
        job.setApplyUrl("https://boards.greenhouse.io/example/jobs/123");
        job.setSource("GREENHOUSE");
        when(portalConnector.fetchJobsFromPortalUrl(
                eq("https://boards.greenhouse.io/example"), any(JobPreferences.class))).thenReturn(List.of(job));
        when(profileService.getActiveProfile()).thenReturn(null);
        when(jobRepository.existsByDedupHash(anyString())).thenReturn(false);
        when(jobRepository.existsByApplyUrlIgnoreCase(job.getApplyUrl())).thenReturn(false);
        when(jobRepository.save(any(JobPosting.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobDiscoveryService service = new JobDiscoveryServiceImpl(
                List.of(portalConnector), jobRepository, profileService, mongoTemplate);

        PortalLinkDiscoveryResult result =
                service.discoverPortalLinks(List.of(" https://boards.greenhouse.io/example "));

        assertEquals(1, result.totalFetched());
        assertEquals(1, result.newJobsSaved());
        assertEquals("SCANNED", result.results().getFirst().status());
        verify(jobRepository).save(job);
    }

    @Test
    @DisplayName("Reports unsupported platforms without making external requests")
    void shouldReportUnsupportedPortalLinks() {
        PortalLinkDiscoveryResult result = discoveryService.discoverPortalLinks(
                List.of("https://www.linkedin.com/jobs/search/?keywords=Java",
                        "http://127.0.0.1/private"));

        assertEquals(2, result.results().size());
        assertTrue(result.results().stream().allMatch(item -> item.status().equals("UNSUPPORTED")));
        verifyNoInteractions(jobRepository);
        verify(mockConnector, never()).fetchJobs(any());
    }

    @Test
    @DisplayName("Limits supplied portal scans to twenty links")
    void shouldLimitPortalLinkScanSize() {
        assertThrows(IllegalArgumentException.class,
                () -> discoveryService.discoverPortalLinks(java.util.Collections.nCopies(21, "https://boards.greenhouse.io/example")));
    }

    @Test
    @DisplayName("Calculates aggregate metrics by work mode and source")
    void shouldCalculateStatsCorrectly() {
        // Arrange
        when(jobRepository.count()).thenReturn(50L);
        when(jobRepository.countByWorkMode("REMOTE")).thenReturn(30L);
        when(jobRepository.countByWorkMode("HYBRID")).thenReturn(15L);
        when(jobRepository.countByWorkMode("ON_SITE")).thenReturn(5L);
        when(jobRepository.countBySource("TEST_SOURCE")).thenReturn(50L);
        when(jobRepository.countByStatus("DISCOVERED")).thenReturn(45L);
        when(jobRepository.countByStatus("MATCHED")).thenReturn(5L);
        when(jobRepository.countByStatus("APPLIED")).thenReturn(0L);

        // Act
        JobStatsDto stats = discoveryService.getJobStats();

        // Assert
        assertNotNull(stats);
        assertEquals(50L, stats.totalJobs());
        assertEquals(30L, stats.remoteJobs());
        assertEquals(15L, stats.hybridJobs());
        assertEquals(5L, stats.onSiteJobs());
        assertEquals(50L, stats.countBySource().get("TEST_SOURCE"));
    }
}
