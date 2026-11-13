package com.aijobagent.controller;

import com.aijobagent.dto.automation.AutomationStatusDto;
import com.aijobagent.dto.automation.PortalLinksConfigResponse;
import com.aijobagent.model.AutomationStatus;
import com.aijobagent.service.AutomationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AutomationController.class)
@SuppressWarnings("null")
class AutomationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AutomationService automationService;

    @Test
    void returnsAutomationStatus() throws Exception {
        when(automationService.getStatus()).thenReturn(new AutomationStatusDto(
                AutomationStatus.STOPPED, null, null, null, "Stopped.", 0, 360, false));

        mockMvc.perform(get("/api/v1/automation").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STOPPED"))
                .andExpect(jsonPath("$.intervalMinutes").value(360))
                .andExpect(jsonPath("$.emailConfigured").value(false));
    }

    @Test
    void startsAutomationThroughControlEndpoint() throws Exception {
        when(automationService.start()).thenReturn(new AutomationStatusDto(
                AutomationStatus.RUNNING, Instant.now(), null, Instant.now(), "Started.", 0, 360, false));

        mockMvc.perform(post("/api/v1/automation/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));

        verify(automationService).start();
    }

    @Test
    void pausesAndStopsAutomationThroughControlEndpoints() throws Exception {
        when(automationService.pause()).thenReturn(new AutomationStatusDto(
                AutomationStatus.PAUSED, null, null, null, "Paused.", 0, 360, false));
        when(automationService.stop()).thenReturn(new AutomationStatusDto(
                AutomationStatus.STOPPED, null, null, null, "Stopped.", 0, 360, false));

        mockMvc.perform(post("/api/v1/automation/pause"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"));
        mockMvc.perform(post("/api/v1/automation/stop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STOPPED"));

        verify(automationService).pause();
        verify(automationService).stop();
    }

    @Test
    void savesSupportedPortalLinksForScheduledScans() throws Exception {
        when(automationService.savePortalLinks(java.util.List.of("https://boards.greenhouse.io/example")))
                .thenReturn(new PortalLinksConfigResponse(
                        java.util.List.of("https://boards.greenhouse.io/example"), java.util.List.of()));

        mockMvc.perform(put("/api/v1/automation/portal-links")
                        .contentType(MediaType.parseMediaType(MediaType.APPLICATION_JSON_VALUE))
                        .content("""
                                {"portalUrls":["https://boards.greenhouse.io/example"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savedPortalUrls[0]").value("https://boards.greenhouse.io/example"))
                .andExpect(jsonPath("$.unsupportedPortalUrls").isEmpty());
    }
}
