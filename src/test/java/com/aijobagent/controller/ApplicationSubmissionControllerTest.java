package com.aijobagent.controller;

import com.aijobagent.dto.applicationprep.ApplicationSubmissionDto;
import com.aijobagent.dto.applicationprep.ApplicationSubmissionRequest;
import com.aijobagent.exception.ApplicationRateLimitExceededException;
import com.aijobagent.model.ApplicationSubmissionStatus;
import com.aijobagent.service.ApplicationSubmissionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApplicationSubmissionController.class)
class ApplicationSubmissionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApplicationSubmissionService submissionService;

    @Test
    void rejectsSubmissionWithoutPerApplicationConfirmation() throws Exception {
        mockMvc.perform(post("/api/v1/applications/job-1/submit")
                        .contentType(MediaType.parseMediaType(MediaType.APPLICATION_JSON_VALUE))
                        .content("{\"confirmed\":false,\"coverLetter\":\"Reviewed letter\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitsExplicitlyConfirmedApplicationRequest() throws Exception {
        ApplicationSubmissionDto response = new ApplicationSubmissionDto(
                "submission-1",
                "job-1",
                "Software Engineer",
                "Example Co",
                ApplicationSubmissionStatus.LINK_OUT,
                null,
                null,
                "https://careers.example.com/apply",
                "Continue on the employer site.",
                "idempotency-key",
                false,
                null,
                null);
        when(submissionService.submit(eq("job-1"), eq(new ApplicationSubmissionRequest(true, "Reviewed letter"))))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/applications/job-1/submit")
                        .contentType(MediaType.parseMediaType(MediaType.APPLICATION_JSON_VALUE))
                        .content("{\"confirmed\":true,\"coverLetter\":\"Reviewed letter\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LINK_OUT"))
                .andExpect(jsonPath("$.applicationUrl").value("https://careers.example.com/apply"));

        verify(submissionService).submit("job-1", new ApplicationSubmissionRequest(true, "Reviewed letter"));
    }

    @Test
    void returnsTooManyRequestsWhenDailyLimitIsReached() throws Exception {
        when(submissionService.submit(eq("job-1"), eq(new ApplicationSubmissionRequest(true, "Reviewed letter"))))
                .thenThrow(new ApplicationRateLimitExceededException("Daily limit reached."));

        mockMvc.perform(post("/api/v1/applications/job-1/submit")
                        .contentType(MediaType.parseMediaType(MediaType.APPLICATION_JSON_VALUE))
                        .content("{\"confirmed\":true,\"coverLetter\":\"Reviewed letter\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("Application Rate Limit Reached"));
    }
}
