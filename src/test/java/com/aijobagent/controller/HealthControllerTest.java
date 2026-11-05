package com.aijobagent.controller;

import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit Test for HealthController using Spring's MockMvc.
 *
 * Annotations explained:
 * - @WebMvcTest: Slices the Spring context to test only the web layer
 * (controllers, JSON serialization),
 * mocking downstream dependencies like MongoTemplate.
 * - @MockitoBean: Injects a Mockito mock into the ApplicationContext in place of a
 * real MongoTemplate.
 */
@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MongoTemplate mongoTemplate;

    @Test
    @DisplayName("Health endpoint returns 200 OK and UP when MongoDB ping succeeds")
    void shouldReturnHealthyWhenMongoPingSucceeds() throws Exception {
        // Arrange: Mock mongoTemplate.executeCommand to return { "ok": 1.0 }
        Document mockPingResult = new Document("ok", 1.0);
        when(mongoTemplate.executeCommand("{ ping: 1 }")).thenReturn(mockPingResult);

        // Act & Assert
        mockMvc.perform(get("/api/v1/health")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("ai-job-application-agent"))
                .andExpect(jsonPath("$.database").value("UP (MongoDB connected)"))
                .andExpect(jsonPath("$.systemInfo.javaVersion").isNotEmpty());
    }

    @Test
    @DisplayName("Health endpoint returns 503 SERVICE_UNAVAILABLE when MongoDB ping fails")
    void shouldReturnDegradedWhenMongoPingFails() throws Exception {
        // Arrange: Mock mongoTemplate.executeCommand to throw an exception
        when(mongoTemplate.executeCommand("{ ping: 1 }")).thenThrow(new RuntimeException("Connection refused"));

        // Act & Assert
        mockMvc.perform(get("/api/v1/health")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DEGRADED"))
                .andExpect(jsonPath("$.database").value("DOWN (Cannot reach MongoDB: Connection refused)"));
    }

    @Test
    @DisplayName("Unknown static resources return 404 instead of 500")
    void shouldReturnNotFoundForUnknownResource() throws Exception {
        mockMvc.perform(get("/missing-resource"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }
}
