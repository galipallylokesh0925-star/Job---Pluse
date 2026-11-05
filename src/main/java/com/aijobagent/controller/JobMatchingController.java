package com.aijobagent.controller;

import com.aijobagent.dto.matching.BatchMatchResult;
import com.aijobagent.dto.matching.JobMatchDto;
import com.aijobagent.dto.matching.MatchScoreFilter;
import com.aijobagent.dto.matching.MatchStatsDto;
import com.aijobagent.service.JobMatchingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for AI resume matching, qualification scoring, and skill gap evaluations.
 */
@RestController
@RequestMapping("/api/v1/matches")
@Tag(name = "AI Resume Matching & Qualification", description = "Endpoints for qualification scoring, deterministic criteria checks, and skill gap analysis")
public class JobMatchingController {

    private final JobMatchingService matchingService;

    public JobMatchingController(JobMatchingService matchingService) {
        this.matchingService = matchingService;
    }

    @PostMapping("/evaluate-all")
    @Operation(summary = "Batch Match All Discovered Jobs",
            description = "Evaluates all discovered job postings against candidate resume and profile, computing 0-100 scores and skill gaps")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Batch evaluation completed",
                    content = @Content(schema = @Schema(implementation = BatchMatchResult.class)))
    })
    public ResponseEntity<BatchMatchResult> evaluateAll(
            @Parameter(description = "Optional profile ID; defaults to active candidate")
            @RequestParam(value = "candidateProfileId", required = false) String candidateProfileId,
            @Parameter(description = "Optional minimum qualification score threshold")
            @RequestParam(value = "minScoreThreshold", required = false) Integer minScoreThreshold) {
        BatchMatchResult result = matchingService.evaluateAllDiscoveredJobs(candidateProfileId, minScoreThreshold);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/evaluate/{jobId}")
    @Operation(summary = "Evaluate Single Job Posting",
            description = "Calculates compatibility score and skill gap breakdown for a specific job")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Match calculated successfully",
                    content = @Content(schema = @Schema(implementation = JobMatchDto.class))),
            @ApiResponse(responseCode = "404", description = "Job posting not found")
    })
    public ResponseEntity<JobMatchDto> evaluateSingle(
            @Parameter(description = "Job posting ID", required = true)
            @PathVariable("jobId") String jobId,
            @Parameter(description = "Optional profile ID")
            @RequestParam(value = "candidateProfileId", required = false) String candidateProfileId) {
        JobMatchDto match = matchingService.evaluateMatch(jobId, candidateProfileId);
        return ResponseEntity.ok(match);
    }

    @GetMapping
    @Operation(summary = "Get Ranked Job Matches",
            description = "Retrieves paginated job matches sorted by overall score descending, with tier and score filtering")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ranked matches returned")
    })
    public ResponseEntity<Page<JobMatchDto>> getRankedMatches(
            @Parameter(description = "Minimum overall score threshold (0-100)")
            @RequestParam(value = "minScore", required = false) Integer minScore,
            @Parameter(description = "Recommendation tier (STRONG_MATCH, MODERATE_MATCH, WEAK_MATCH, or ALL)")
            @RequestParam(value = "tier", required = false) String tier,
            @Parameter(description = "Only return jobs satisfying deterministic hard criteria")
            @RequestParam(value = "hardCriteriaOnly", required = false) Boolean hardCriteriaOnly,
            @Parameter(description = "Keyword search matching title or company")
            @RequestParam(value = "keyword", required = false) String keyword,
            @Parameter(description = "Page number (0-indexed)")
            @RequestParam(value = "page", defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(value = "size", defaultValue = "20") int size) {
        MatchScoreFilter filter = new MatchScoreFilter(minScore, tier, hardCriteriaOnly, keyword, page, size);
        return ResponseEntity.ok(matchingService.getRankedMatches(filter));
    }

    @GetMapping("/{jobId}")
    @Operation(summary = "Get Match Details for Job",
            description = "Retrieves qualification analysis for a specific job posting")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Match details found",
                    content = @Content(schema = @Schema(implementation = JobMatchDto.class))),
            @ApiResponse(responseCode = "404", description = "Job not found")
    })
    public ResponseEntity<JobMatchDto> getMatchByJobId(
            @Parameter(description = "Job posting ID", required = true)
            @PathVariable("jobId") String jobId) {
        return ResponseEntity.ok(matchingService.getMatchByJobId(jobId));
    }

    @GetMapping("/stats")
    @Operation(summary = "Get Match Distributions & Stats",
            description = "Returns count of strong/moderate/weak matches and hard criteria pass rate")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Statistics retrieved",
                    content = @Content(schema = @Schema(implementation = MatchStatsDto.class)))
    })
    public ResponseEntity<MatchStatsDto> getStats() {
        return ResponseEntity.ok(matchingService.getMatchStats());
    }

    @DeleteMapping
    @Operation(summary = "Clear All Match Evaluations", description = "Deletes all stored match results for active candidate")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Matches cleared")
    })
    public ResponseEntity<Map<String, String>> clearMatches() {
        matchingService.clearAllMatches();
        return ResponseEntity.ok(Map.of("message", "All match evaluations cleared successfully."));
    }
}
