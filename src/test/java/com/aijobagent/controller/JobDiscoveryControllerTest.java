package com.aijobagent.controller;

import com.aijobagent.dto.job.DiscoveryRunResult;
import com.aijobagent.dto.job.JobImportResult;
import com.aijobagent.dto.job.JobPostingDto;
import com.aijobagent.dto.job.JobStatsDto;
import com.aijobagent.dto.job.PortalLinkDiscoveryItem;
import com.aijobagent.dto.job.PortalLinkDiscoveryResult;
import com.aijobagent.service.JobDiscoveryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(JobDiscoveryController.class)
@SuppressWarnings("null")
class JobDiscoveryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JobDiscoveryService discoveryService;

    @Test
    @DisplayName("POST /api/v1/jobs/discover returns 200 OK with discovery run metrics")
    void shouldTriggerDiscoveryRun() throws Exception {
        DiscoveryRunResult mockResult = new DiscoveryRunResult(
                true,
                "Scan complete. Found 8 jobs.",
                Instant.now(),
                Instant.now(),
                450L,
                10,
                8,
                2,
                Map.of("ENTERPRISE_PORTAL", 8, "REMOTE_OK", 2)
        );

        when(discoveryService.runDiscovery(any())).thenReturn(mockResult);

        mockMvc.perform(post("/api/v1/jobs/discover")
                        .contentType(MediaType.parseMediaType(MediaType.APPLICATION_JSON_VALUE))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.newJobsSaved").value(8))
                .andExpect(jsonPath("$.duplicateJobsSkipped").value(2))
                .andExpect(jsonPath("$.sourceBreakdown.ENTERPRISE_PORTAL").value(8));
    }

    @Test
    @DisplayName("POST /api/v1/jobs/alerts/import returns the imported job")
    @SuppressWarnings("null")
    void shouldImportJobAlert() throws Exception {
        JobPostingDto job = new JobPostingDto(
                "job-imported", "Junior Java Developer", "Example Company", "Bengaluru",
                "HYBRID", "UNKNOWN", "UNKNOWN", "Unknown", "Java role", List.of("Java"),
                null, null, null, "https://careers.example.com/jobs/123",
                "WHATSAPP_ALERT", Instant.now(), Instant.now(), "DISCOVERED"
        );
        when(discoveryService.importJobAlert(any())).thenReturn(
                new JobImportResult(true, false, "Job alert imported.", job));

        mockMvc.perform(post("/api/v1/jobs/alerts/import")
                        .contentType(MediaType.parseMediaType(MediaType.APPLICATION_JSON_VALUE))
                        .content("""
                                {
                                  "source": "WHATSAPP_ALERT",
                                  "title": "Junior Java Developer",
                                  "company": "Example Company",
                                  "applyUrl": "https://careers.example.com/jobs/123",
                                  "location": "Bengaluru",
                                  "workMode": "HYBRID",
                                  "description": "Java role"
                                }
                                """)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(false))
                .andExpect(jsonPath("$.job.id").value("job-imported"))
                .andExpect(jsonPath("$.job.source").value("WHATSAPP_ALERT"));
    }

    @Test
    @DisplayName("POST /api/v1/jobs/portal-links/discover returns supported and unsupported link outcomes")
    void shouldDiscoverJobsFromPortalLinks() throws Exception {
        when(discoveryService.discoverPortalLinks(any())).thenReturn(new PortalLinkDiscoveryResult(
                Instant.now(), Instant.now(), 12, 1, 1, 0,
                List.of(new PortalLinkDiscoveryItem(
                        "https://boards.greenhouse.io/example", "GREENHOUSE", "SCANNED",
                        "Public postings scanned.", 1, 1, 0))));

        mockMvc.perform(post("/api/v1/jobs/portal-links/discover")
                        .contentType(MediaType.parseMediaType(MediaType.APPLICATION_JSON_VALUE))
                        .content("""
                                {"portalUrls":["https://boards.greenhouse.io/example"]}
                                """)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newJobsSaved").value(1))
                .andExpect(jsonPath("$.results[0].source").value("GREENHOUSE"))
                .andExpect(jsonPath("$.results[0].status").value("SCANNED"));
    }

    @Test
    @DisplayName("Rejects portal discovery requests with more than twenty links")
    void shouldRejectTooManyPortalLinks() throws Exception {
        String urls = java.util.stream.IntStream.range(0, 21)
                .mapToObj(index -> "\"https://boards.greenhouse.io/company" + index + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        mockMvc.perform(post("/api/v1/jobs/portal-links/discover")
                        .contentType(MediaType.parseMediaType(MediaType.APPLICATION_JSON_VALUE))
                        .content("{\"portalUrls\":[" + urls + "]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/jobs returns 200 OK with paginated jobs")
    void shouldSearchAndReturnPaginatedJobs() throws Exception {
        JobPostingDto dto = new JobPostingDto(
                "job-101",
                "Junior Java Developer",
                "TCS",
                "Bengaluru, India",
                "HYBRID",
                "FULL_TIME",
                "ENTRY_LEVEL_FRESHER",
                "India",
                "Great role",
                List.of("Java", "Spring Boot"),
                500000.0,
                700000.0,
                "INR",
                "https://tcs.com/apply",
                "ENTERPRISE_PORTAL",
                Instant.now(),
                Instant.now(),
                "DISCOVERED"
        );

        PageImpl<JobPostingDto> page = new PageImpl<>(Objects.requireNonNull(List.of(dto)), PageRequest.of(0, 20), 1);
        when(discoveryService.searchJobs(any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/jobs")
                        .param("keyword", "Java")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("job-101"))
                .andExpect(jsonPath("$.content[0].title").value("Junior Java Developer"))
                .andExpect(jsonPath("$.content[0].company").value("TCS"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/jobs/stats returns 200 OK with aggregated statistics")
    void shouldReturnJobStats() throws Exception {
        JobStatsDto stats = new JobStatsDto(
                25L, 15L, 7L, 3L,
                Map.of("ENTERPRISE_PORTAL", 15L, "REMOTE_OK", 10L),
                Map.of("DISCOVERED", 25L)
        );

        when(discoveryService.getJobStats()).thenReturn(stats);

        mockMvc.perform(get("/api/v1/jobs/stats")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalJobs").value(25))
                .andExpect(jsonPath("$.remoteJobs").value(15))
                .andExpect(jsonPath("$.hybridJobs").value(7));
    }

    @Test
    @DisplayName("GET /api/v1/jobs/{id} returns job details")
    void shouldReturnJobById() throws Exception {
        JobPostingDto dto = new JobPostingDto(
                "job-101",
                "Software Engineer Intern",
                "Amazon India",
                "Bengaluru, India",
                "HYBRID",
                "INTERNSHIP",
                "ENTRY_LEVEL_FRESHER",
                "India",
                "Amazon SDE Intern role",
                List.of("Java", "Python", "DSA"),
                80000.0, null, "INR",
                "https://amazon.jobs/101",
                "ENTERPRISE_PORTAL",
                Instant.now(),
                Instant.now(),
                "DISCOVERED"
        );

        when(discoveryService.getJobById("job-101")).thenReturn(dto);

        mockMvc.perform(get("/api/v1/jobs/job-101")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Software Engineer Intern"))
                .andExpect(jsonPath("$.company").value("Amazon India"));
    }
}
