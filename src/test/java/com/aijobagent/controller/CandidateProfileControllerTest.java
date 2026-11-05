package com.aijobagent.controller;

import com.aijobagent.dto.profile.CandidateProfileDto;
import com.aijobagent.dto.profile.CandidateProfileUpdateRequest;
import com.aijobagent.dto.profile.ResumeUploadResponse;
import com.aijobagent.model.JobPreferences;
import com.aijobagent.model.ResumeMetadata;
import com.aijobagent.service.CandidateProfileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.Objects;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CandidateProfileController.class)
class CandidateProfileControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @MockitoBean
        private CandidateProfileService profileService;

        @Test
        @DisplayName("GET /api/v1/profile returns 200 OK with active profile")
        void shouldReturnActiveProfile() throws Exception {
                CandidateProfileDto mockDto = new CandidateProfileDto(
                                "profile-123",
                                "Arjun Sharma",
                                "arjun@example.com",
                                "+91 9876543210",
                                "Bengaluru, India",
                                "https://linkedin.com/in/arjunsharma",
                                "https://github.com/arjunsharma",
                                null,
                                "Software Engineer",
                                List.of("Java", "Spring Boot", "MongoDB"),
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of(),
                                new JobPreferences(),
                                new ResumeMetadata("resume.pdf", "application/pdf", 1024L, 500, "PDFBox",
                                                Instant.now()),
                                true,
                                Instant.now(),
                                Instant.now());

                when(profileService.getActiveProfile()).thenReturn(mockDto);

                mockMvc.perform(get("/api/v1/profile")
                                .accept(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value("profile-123"))
                                .andExpect(jsonPath("$.fullName").value("Arjun Sharma"))
                                .andExpect(jsonPath("$.email").value("arjun@example.com"))
                                .andExpect(jsonPath("$.skills[0]").value("Java"))
                                .andExpect(jsonPath("$.hasResume").value(true));
        }

        @Test
        @DisplayName("POST /api/v1/profile/resume/upload returns 200 OK with upload response")
        void shouldUploadAndParseResume() throws Exception {
                MockMultipartFile file = new MockMultipartFile(
                                "file",
                                "sample.pdf",
                                "application/pdf",
                                "dummy pdf content".getBytes());

                CandidateProfileDto mockDto = new CandidateProfileDto(
                                "profile-123",
                                "Arjun Sharma",
                                "arjun@example.com",
                                "+91 9876543210",
                                "Bengaluru, India",
                                null, null, null, null,
                                List.of("Java", "Spring Boot"),
                                List.of(), List.of(), List.of(), List.of(),
                                new JobPreferences(),
                                null,
                                true,
                                Instant.now(),
                                Instant.now());

                ResumeUploadResponse mockResponse = new ResumeUploadResponse(
                                true,
                                "Resume parsed successfully",
                                "sample.pdf",
                                "Apache PDFBox 3.0.3",
                                1200,
                                2,
                                mockDto);

                when(profileService.uploadAndParseResume(any())).thenReturn(mockResponse);

                mockMvc.perform(multipart("/api/v1/profile/resume/upload")
                                .file(file)
                                .accept(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.fileName").value("sample.pdf"))
                                .andExpect(jsonPath("$.extractedSkillsCount").value(2))
                                .andExpect(jsonPath("$.profile.fullName").value("Arjun Sharma"));
        }

        @Test
        @DisplayName("PUT /api/v1/profile updates candidate profile details")
        void shouldUpdateProfile() throws Exception {
                CandidateProfileUpdateRequest request = new CandidateProfileUpdateRequest(
                                "Arjun S",
                                "arjun.updated@example.com",
                                "+91 9876543210",
                                "Pune, India",
                                null, null, null, null,
                                List.of("Java", "Microservices", "Docker"),
                                List.of(), List.of(), List.of(), List.of(),
                                new JobPreferences());

                CandidateProfileDto updatedDto = new CandidateProfileDto(
                                "profile-123",
                                "Arjun S",
                                "arjun.updated@example.com",
                                "+91 9876543210",
                                "Pune, India",
                                null, null, null, null,
                                List.of("Java", "Microservices", "Docker"),
                                List.of(), List.of(), List.of(), List.of(),
                                new JobPreferences(),
                                null,
                                false,
                                Instant.now(),
                                Instant.now());

                when(profileService.updateProfile(any())).thenReturn(updatedDto);

                mockMvc.perform(put("/api/v1/profile")
                                .contentType(MediaType.parseMediaType(MediaType.APPLICATION_JSON_VALUE))
                                .content(Objects.requireNonNull(objectMapper.writeValueAsString(request)))
                                .accept(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.fullName").value("Arjun S"))
                                .andExpect(jsonPath("$.email").value("arjun.updated@example.com"))
                                .andExpect(jsonPath("$.location").value("Pune, India"));
        }

        @Test
        @DisplayName("GET /api/v1/profile/raw-text returns raw text payload")
        void shouldReturnRawText() throws Exception {
                when(profileService.getRawResumeText(null)).thenReturn("Extracted raw resume text contents...");

                mockMvc.perform(get("/api/v1/profile/raw-text")
                                .accept(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.rawResumeText").value("Extracted raw resume text contents..."));
        }
}
