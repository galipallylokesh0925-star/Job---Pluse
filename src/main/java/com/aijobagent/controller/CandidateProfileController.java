package com.aijobagent.controller;

import com.aijobagent.dto.profile.CandidateProfileDto;
import com.aijobagent.dto.profile.CandidateProfileUpdateRequest;
import com.aijobagent.dto.profile.ResumeUploadResponse;
import com.aijobagent.service.CandidateProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * REST Controller providing candidate profile management, resume upload,
 * document parsing, and search preference configuration.
 */
@RestController
@RequestMapping("/api/v1/profile")
@Tag(name = "Candidate Profile & Resume Engine", description = "Endpoints for resume parsing, candidate profile management, and search preferences")
public class CandidateProfileController {

    private final CandidateProfileService profileService;

    public CandidateProfileController(CandidateProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    @Operation(summary = "Get Active Candidate Profile", description = "Retrieves the active candidate profile including contact details, skills, and preferences")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Candidate profile successfully retrieved",
                    content = @Content(schema = @Schema(implementation = CandidateProfileDto.class)))
    })
    public ResponseEntity<CandidateProfileDto> getActiveProfile() {
        return ResponseEntity.ok(profileService.getActiveProfile());
    }

    @PutMapping
    @Operation(summary = "Update Candidate Profile & Preferences", description = "Updates candidate profile details, target job roles, skills, and work modes")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile successfully updated",
                    content = @Content(schema = @Schema(implementation = CandidateProfileDto.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed on submitted fields")
    })
    public ResponseEntity<CandidateProfileDto> updateProfile(
            @Valid @RequestBody CandidateProfileUpdateRequest request) {
        CandidateProfileDto updated = profileService.updateProfile(request);
        return ResponseEntity.ok(updated);
    }

    @PostMapping(value = "/resume/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload & Parse Resume Document",
            description = "Accepts PDF, DOCX, or TXT resumes; scanned PDFs use Tesseract OCR when no embedded text is found.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resume uploaded and parsed successfully",
                    content = @Content(schema = @Schema(implementation = ResumeUploadResponse.class))),
            @ApiResponse(responseCode = "400", description = "No file supplied or unsupported file format"),
            @ApiResponse(responseCode = "422", description = "Failed to parse document or document is unreadable/corrupted"),
            @ApiResponse(responseCode = "413", description = "File exceeds configured maximum size (10MB)")
    })
    public ResponseEntity<ResumeUploadResponse> uploadResume(
            @Parameter(description = "Resume document file (PDF, DOCX, or TXT; max 10MB)", required = true)
            @RequestParam("file") MultipartFile file) {
        ResumeUploadResponse response = profileService.uploadAndParseResume(file);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/raw-text")
    @Operation(summary = "Get Raw Extracted Resume Text",
            description = "Returns the unformatted text extracted from the candidate's resume for inspection or AI context injection")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Raw text retrieved")
    })
    public ResponseEntity<Map<String, String>> getRawResumeText(
            @Parameter(description = "Optional profile ID; defaults to active profile if omitted")
            @RequestParam(value = "profileId", required = false) String profileId) {
        String rawText = profileService.getRawResumeText(profileId);
        return ResponseEntity.ok(Map.of("rawResumeText", rawText));
    }

    @DeleteMapping
    @Operation(summary = "Reset or Delete Candidate Profile", description = "Deletes the candidate profile and parsed resume data")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile deleted successfully")
    })
    public ResponseEntity<Map<String, String>> deleteProfile(
            @Parameter(description = "Optional profile ID; deletes active profile if omitted")
            @RequestParam(value = "profileId", required = false) String profileId) {
        profileService.deleteProfile(profileId);
        return ResponseEntity.ok(Map.of("message", "Candidate profile deleted successfully."));
    }
}
