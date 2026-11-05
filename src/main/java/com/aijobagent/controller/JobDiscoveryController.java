package com.aijobagent.controller;

import com.aijobagent.dto.job.DiscoveryRunResult;
import com.aijobagent.dto.job.JobPostingDto;
import com.aijobagent.dto.job.JobSearchCriteria;
import com.aijobagent.dto.job.JobStatsDto;
import com.aijobagent.dto.job.JobImportRequest;
import com.aijobagent.dto.job.JobImportResult;
import com.aijobagent.dto.job.PortalLinkDiscoveryRequest;
import com.aijobagent.dto.job.PortalLinkDiscoveryResult;
import com.aijobagent.service.JobDiscoveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST Controller for multi-source job discovery, deduplication, search filtering,
 * and opportunity management.
 */
@RestController
@RequestMapping("/api/v1/jobs")
@Tag(name = "Job Discovery & Listings", description = "Endpoints to scan external job feeds, deduplicate postings, and query opportunities")
public class JobDiscoveryController {

    private final JobDiscoveryService discoveryService;

    public JobDiscoveryController(JobDiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    @PostMapping("/discover")
    @Operation(summary = "Trigger Job Discovery Run",
            description = "Initiates an automated scan across enabled job connectors (RemoteOK, Arbeitnow, Enterprise Career Portals) with SHA-256 deduplication")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Discovery run completed successfully",
                    content = @Content(schema = @Schema(implementation = DiscoveryRunResult.class)))
    })
    public ResponseEntity<DiscoveryRunResult> triggerDiscovery(
            @Parameter(description = "Optional list of connector IDs to run; if empty, all enabled connectors are executed")
            @RequestBody(required = false) List<String> connectorIds) {
        DiscoveryRunResult result = discoveryService.runDiscovery(connectorIds);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/alerts/import")
    @Operation(summary = "Import a job alert",
            description = "Adds an individual job shared from WhatsApp, email, LinkedIn, Indeed, or a careers page. Duplicate application links are not stored twice.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Job alert imported or identified as a duplicate"),
            @ApiResponse(responseCode = "400", description = "The alert fields or application URL are invalid")
    })
    public ResponseEntity<JobImportResult> importJobAlert(@Valid @RequestBody JobImportRequest request) {
        return ResponseEntity.ok(discoveryService.importJobAlert(request));
    }

    @PostMapping("/portal-links/discover")
    @Operation(summary = "Discover jobs from supplied public ATS links",
            description = "Scans public Greenhouse and Lever board or job links, deduplicates postings, and leaves all applications for candidate review and confirmation.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Per-link discovery results returned"),
            @ApiResponse(responseCode = "400", description = "Invalid request or more than 20 portal links supplied")
    })
    public ResponseEntity<PortalLinkDiscoveryResult> discoverPortalLinks(
            @Valid @RequestBody PortalLinkDiscoveryRequest request) {
        return ResponseEntity.ok(discoveryService.discoverPortalLinks(request.portalUrls()));
    }

    @GetMapping
    @Operation(summary = "Search & Filter Discovered Jobs",
            description = "Retrieves paginated job postings with keyword search, work mode, and connector source filters")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated list of jobs returned")
    })
    public ResponseEntity<Page<JobPostingDto>> searchJobs(
            @Parameter(description = "Search keyword across title, company, skills, or location")
            @RequestParam(value = "keyword", required = false) String keyword,
            @Parameter(description = "Work mode (REMOTE, HYBRID, ON_SITE, or ALL)")
            @RequestParam(value = "workMode", required = false) String workMode,
            @Parameter(description = "Connector source (REMOTE_OK, ARBEITNOW, ENTERPRISE_PORTAL, or ALL)")
            @RequestParam(value = "source", required = false) String source,
            @Parameter(description = "Lifecycle status (DISCOVERED, MATCHED, APPLIED, or ALL)")
            @RequestParam(value = "status", required = false) String status,
            @Parameter(description = "Location filter")
            @RequestParam(value = "location", required = false) String location,
            @Parameter(description = "Page number (0-indexed)")
            @RequestParam(value = "page", defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(value = "size", defaultValue = "20") int size) {
        JobSearchCriteria criteria = new JobSearchCriteria(keyword, workMode, source, status, location, page, size);
        return ResponseEntity.ok(discoveryService.searchJobs(criteria));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Job Posting by ID", description = "Retrieves full details of a specific job posting")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Job details retrieved",
                    content = @Content(schema = @Schema(implementation = JobPostingDto.class))),
            @ApiResponse(responseCode = "404", description = "Job posting not found")
    })
    public ResponseEntity<JobPostingDto> getJobById(
            @Parameter(description = "MongoDB Job ID", required = true)
            @PathVariable("id") String id) {
        return ResponseEntity.ok(discoveryService.getJobById(id));
    }

    @GetMapping("/stats")
    @Operation(summary = "Get Job Discovery Statistics", description = "Retrieves aggregate metrics including counts by work mode, source, and status")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Statistics summary retrieved",
                    content = @Content(schema = @Schema(implementation = JobStatsDto.class)))
    })
    public ResponseEntity<JobStatsDto> getJobStats() {
        return ResponseEntity.ok(discoveryService.getJobStats());
    }

    @DeleteMapping
    @Operation(summary = "Clear All Discovered Jobs", description = "Deletes all indexed job postings from MongoDB")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Jobs cleared successfully")
    })
    public ResponseEntity<Map<String, String>> clearJobs() {
        discoveryService.clearAllJobs();
        return ResponseEntity.ok(Map.of("message", "All discovered job postings cleared successfully."));
    }
}
