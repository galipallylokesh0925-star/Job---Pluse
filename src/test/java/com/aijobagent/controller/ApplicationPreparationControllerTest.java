package com.aijobagent.controller;

import com.aijobagent.dto.applicationprep.ProfileReviewDto;
import com.aijobagent.dto.applicationprep.TruthfulnessValidationDto;
import com.aijobagent.service.ApplicationPreparationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApplicationPreparationController.class)
class ApplicationPreparationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApplicationPreparationService preparationService;

    @Test
    void returnsProfileReview() throws Exception {
        when(preparationService.reviewActiveProfile()).thenReturn(
                new ProfileReviewDto(80, 8, 10, true, "NEEDS_REVIEW", List.of()));

        mockMvc.perform(get("/api/v1/application-preparation/profile-review"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completenessScore").value(80))
                .andExpect(jsonPath("$.resumeAvailable").value(true));
    }

    @Test
    void validatesCoverLetterRequest() throws Exception {
        when(preparationService.validateCoverLetter(eq("job-1"), eq("I am excited to apply.")))
                .thenReturn(new TruthfulnessValidationDto(true, 0, List.of(), "Review before submitting."));

        mockMvc.perform(post("/api/v1/application-preparation/job-1/validate")
                        .contentType(MediaType.parseMediaType(MediaType.APPLICATION_JSON_VALUE))
                        .content("{\"coverLetter\":\"I am excited to apply.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allClaimsSupported").value(true));
    }
}
