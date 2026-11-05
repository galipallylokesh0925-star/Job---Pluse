package com.aijobagent.controller;

import com.aijobagent.dto.automation.AutomationRunDto;
import com.aijobagent.dto.automation.AutomationStatusDto;
import com.aijobagent.dto.automation.PortalLinksConfigRequest;
import com.aijobagent.dto.automation.PortalLinksConfigResponse;
import com.aijobagent.service.AutomationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@RestController
@RequestMapping("/api/v1/automation")
@Tag(name = "Background Automation", description = "Opt-in scheduled job discovery, matching, and run notifications")
public class AutomationController {

    private final AutomationService automationService;

    public AutomationController(AutomationService automationService) {
        this.automationService = automationService;
    }

    @GetMapping
    @Operation(summary = "Get background automation status")
    public ResponseEntity<AutomationStatusDto> getStatus() {
        return ResponseEntity.ok(automationService.getStatus());
    }

    @GetMapping("/runs")
    @Operation(summary = "Get recent scheduled run notifications")
    public ResponseEntity<List<AutomationRunDto>> getRecentRuns() {
        return ResponseEntity.ok(automationService.getRecentRuns());
    }

    @GetMapping("/portal-links")
    @Operation(summary = "Get saved public ATS board links for scheduled discovery")
    public ResponseEntity<PortalLinksConfigResponse> getPortalLinks() {
        return ResponseEntity.ok(automationService.getPortalLinks());
    }

    @PutMapping("/portal-links")
    @Operation(summary = "Save public ATS board links for scheduled discovery")
    public ResponseEntity<PortalLinksConfigResponse> savePortalLinks(
            @Valid @RequestBody PortalLinksConfigRequest request) {
        return ResponseEntity.ok(automationService.savePortalLinks(request.portalUrls()));
    }

    @PostMapping("/start")
    @Operation(summary = "Start scheduled job discovery and matching")
    public ResponseEntity<AutomationStatusDto> start() {
        return ResponseEntity.ok(automationService.start());
    }

    @PostMapping("/pause")
    @Operation(summary = "Pause scheduled discovery and matching")
    public ResponseEntity<AutomationStatusDto> pause() {
        return ResponseEntity.ok(automationService.pause());
    }

    @PostMapping("/stop")
    @Operation(summary = "Stop scheduled discovery and matching")
    public ResponseEntity<AutomationStatusDto> stop() {
        return ResponseEntity.ok(automationService.stop());
    }
}
