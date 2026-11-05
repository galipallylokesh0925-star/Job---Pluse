package com.aijobagent.controller;

import com.aijobagent.dto.matching.BatchMatchResult;
import com.aijobagent.dto.matching.JobMatchDto;
import com.aijobagent.dto.matching.MatchStatsDto;
import com.aijobagent.service.JobMatchingService;
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
import java.util.Objects;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(JobMatchingController.class)
class JobMatchingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JobMatchingService matchingService;

    @Test
    @DisplayName("POST /api/v1/matches/evaluate-all returns 200 OK with BatchMatchResult")
    void shouldEvaluateAllJobs() throws Exception {
        BatchMatchResult mockResult = new BatchMatchResult(
                true,
                "Batch matching completed. Evaluated 10 jobs.",
                10, 6, 3, 1, 9, 1, 81.5, 320L
        );

        when(matchingService.evaluateAllDiscoveredJobs(any(), any())).thenReturn(mockResult);

        mockMvc.perform(post("/api/v1/matches/evaluate-all")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.totalJobsEvaluated").value(10))
                .andExpect(jsonPath("$.strongMatchesCount").value(6))
                .andExpect(jsonPath("$.averageScore").value(81.5));
    }

    @Test
    @DisplayName("GET /api/v1/matches returns 200 OK with paginated matches")
    void shouldReturnRankedMatches() throws Exception {
        JobMatchDto dto = new JobMatchDto(
                "match-1",
                "job-101",
                "Junior Java Developer",
                "TCS",
                "Bengaluru, India",
                "HYBRID",
                "https://tcs.com/apply",
                88, 92, 85, 90, 80,
                true,
                List.of(),
                "STRONG_MATCH",
                List.of("Java", "Spring Boot"),
                List.of("SQL"),
                List.of("Docker"),
                "High qualification match",
                List.of("Highlight Java experience"),
                Instant.now(),
                "Deterministic Semantic Matcher v1.0"
        );

        PageImpl<JobMatchDto> page = new PageImpl<>(Objects.requireNonNull(List.of(dto)), PageRequest.of(0, 20), 1);
        when(matchingService.getRankedMatches(any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/matches")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("match-1"))
                .andExpect(jsonPath("$.content[0].overallScore").value(88))
                .andExpect(jsonPath("$.content[0].recommendationTier").value("STRONG_MATCH"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/matches/stats returns 200 OK with statistics")
    void shouldReturnMatchStats() throws Exception {
        MatchStatsDto stats = new MatchStatsDto(
                20L, 12L, 6L, 2L, 18L, 78.5
        );

        when(matchingService.getMatchStats()).thenReturn(stats);

        mockMvc.perform(get("/api/v1/matches/stats")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEvaluated").value(20))
                .andExpect(jsonPath("$.strongMatches").value(12))
                .andExpect(jsonPath("$.averageScore").value(78.5));
    }
}
