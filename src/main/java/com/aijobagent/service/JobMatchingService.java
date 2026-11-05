package com.aijobagent.service;

import com.aijobagent.dto.matching.BatchMatchResult;
import com.aijobagent.dto.matching.JobMatchDto;
import com.aijobagent.dto.matching.MatchScoreFilter;
import com.aijobagent.dto.matching.MatchStatsDto;
import org.springframework.data.domain.Page;

/**
 * Service contract for evaluating resume-to-job qualification compatibility,
 * enforcing deterministic criteria, calculating multi-dimensional scores,
 * and performing skill gap analysis.
 */
public interface JobMatchingService {

    /**
     * Evaluates qualification compatibility between a specific job posting and candidate profile.
     *
     * @param jobId MongoDB ID of the job posting
     * @param candidateProfileId optional candidate profile ID; defaults to active profile if null
     * @return JobMatchDto with overall score, sub-scores, criteria checks, and skill breakdown
     */
    JobMatchDto evaluateMatch(String jobId, String candidateProfileId);

    /**
     * Evaluates all discovered job postings in batch against the candidate profile,
     * persists match evaluations, and updates job posting statuses.
     */
    BatchMatchResult evaluateAllDiscoveredJobs(String candidateProfileId, Integer minScoreThreshold);

    /**
     * Retrieves ranked and filtered job match evaluations with pagination.
     */
    Page<JobMatchDto> getRankedMatches(MatchScoreFilter filter);

    /**
     * Retrieves the match evaluation for a specific job posting.
     */
    JobMatchDto getMatchByJobId(String jobId);

    /**
     * Calculates aggregate match metrics for the active candidate profile.
     */
    MatchStatsDto getMatchStats();

    /**
     * Clears all match evaluations for testing or recalculation.
     */
    void clearAllMatches();
}
