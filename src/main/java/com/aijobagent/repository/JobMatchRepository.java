package com.aijobagent.repository;

import com.aijobagent.model.JobMatchResult;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data MongoDB repository for JobMatchResult documents.
 */
@Repository
public interface JobMatchRepository extends MongoRepository<JobMatchResult, String> {

    /**
     * Finds an existing match evaluation for a specific job and candidate pair.
     */
    Optional<JobMatchResult> findByJobIdAndCandidateProfileId(String jobId, String candidateProfileId);

    /**
     * Finds the latest match result for a given job posting ID.
     */
    Optional<JobMatchResult> findTopByJobIdOrderByMatchedAtDesc(String jobId);

    /**
     * Finds all matches for a candidate sorted by overall score descending.
     */
    List<JobMatchResult> findByCandidateProfileIdOrderByOverallScoreDesc(String candidateProfileId);

    /**
     * Counts evaluated matches for a candidate profile.
     */
    long countByCandidateProfileId(String candidateProfileId);

    /**
     * Counts matches by recommendation tier for a candidate.
     */
    long countByCandidateProfileIdAndRecommendationTier(String candidateProfileId, String recommendationTier);

    /**
     * Counts matches meeting or failing deterministic hard criteria.
     */
    long countByCandidateProfileIdAndHardCriteriaMet(String candidateProfileId, boolean hardCriteriaMet);

    /**
     * Deletes all match results for a candidate profile.
     */
    void deleteByCandidateProfileId(String candidateProfileId);
}
