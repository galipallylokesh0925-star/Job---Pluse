package com.aijobagent.controller;

import com.aijobagent.dto.applicationprep.ApplicationSubmissionDto;
import com.aijobagent.dto.applicationprep.ApplicationSubmissionRequest;
import com.aijobagent.service.ApplicationSubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/applications")
@Tag(name = "Applications", description = "Explicitly confirmed application submission and employer link-out")
public class ApplicationSubmissionController {

    private final ApplicationSubmissionService submissionService;

    public ApplicationSubmissionController(ApplicationSubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @PostMapping("/{jobId}/submit")
    @Operation(summary = "Submit or hand off an application",
            description = "Requires explicit per-job confirmation; uses a documented official provider API when configured and otherwise returns a safe employer link.")
    public ResponseEntity<ApplicationSubmissionDto> submit(
            @PathVariable String jobId,
            @Valid @RequestBody ApplicationSubmissionRequest request) {
        return ResponseEntity.ok(submissionService.submit(jobId, request));
    }
}
