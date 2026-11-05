package com.aijobagent.controller;

import com.aijobagent.dto.applicationprep.ApplicationPreparationDto;
import com.aijobagent.dto.applicationprep.CoverLetterValidationRequest;
import com.aijobagent.dto.applicationprep.ProfileReviewDto;
import com.aijobagent.dto.applicationprep.TruthfulnessValidationDto;
import com.aijobagent.service.ApplicationPreparationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/application-preparation")
@Tag(name = "Application Preparation", description = "Evidence-grounded cover letters, profile QA, and claim checks")
public class ApplicationPreparationController {

    private final ApplicationPreparationService preparationService;

    public ApplicationPreparationController(ApplicationPreparationService preparationService) {
        this.preparationService = preparationService;
    }

    @GetMapping("/profile-review")
    @Operation(summary = "Review Candidate Profile", description = "Checks profile completeness and compares populated fields with uploaded resume text")
    public ResponseEntity<ProfileReviewDto> reviewProfile() {
        return ResponseEntity.ok(preparationService.reviewActiveProfile());
    }

    @PostMapping("/{jobId}")
    @Operation(summary = "Prepare Job Application", description = "Generates a job-tailored cover letter using only profile facts and reviews its source evidence")
    public ResponseEntity<ApplicationPreparationDto> prepareForJob(@PathVariable String jobId) {
        return ResponseEntity.ok(preparationService.prepareForJob(jobId));
    }

    @PostMapping("/{jobId}/validate")
    @Operation(summary = "Validate Cover Letter Claims", description = "Flags likely unsupported personal and measurable claims against profile and resume evidence")
    public ResponseEntity<TruthfulnessValidationDto> validateCoverLetter(
            @PathVariable String jobId,
            @Valid @RequestBody CoverLetterValidationRequest request) {
        return ResponseEntity.ok(preparationService.validateCoverLetter(jobId, request.coverLetter()));
    }
}
