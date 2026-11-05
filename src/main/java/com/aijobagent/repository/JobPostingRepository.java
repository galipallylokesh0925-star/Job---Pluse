package com.aijobagent.repository;

import com.aijobagent.model.JobPosting;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data MongoDB repository for JobPosting documents.
 */
@Repository
public interface JobPostingRepository extends MongoRepository<JobPosting, String> {

    /**
     * Checks if a job posting with the specified deduplication hash already exists.
     */
    boolean existsByDedupHash(String dedupHash);

    /**
     * Finds a job posting by its unique deduplication hash.
     */
    Optional<JobPosting> findByDedupHash(String dedupHash);

    boolean existsByApplyUrlIgnoreCase(String applyUrl);

    Optional<JobPosting> findFirstByApplyUrlIgnoreCase(String applyUrl);

    /**
     * Counts postings by work mode (e.g. REMOTE, HYBRID, ON_SITE).
     */
    long countByWorkMode(String workMode);

    /**
     * Counts postings by connector source (e.g. REMOTE_OK, ARBEITNOW, ENTERPRISE_PORTAL).
     */
    long countBySource(String source);

    /**
     * Counts postings by application status (e.g. DISCOVERED, MATCHED, APPLIED).
     */
    long countByStatus(String status);

    /**
     * Flexible multi-field regex query supporting keyword search across title, company, or requiredSkills.
     */
    @Query("{ $or: [ " +
            "{ 'title': { $regex: ?0, $options: 'i' } }, " +
            "{ 'company': { $regex: ?0, $options: 'i' } }, " +
            "{ 'requiredSkills': { $regex: ?0, $options: 'i' } }, " +
            "{ 'location': { $regex: ?0, $options: 'i' } } " +
            "] }")
    Page<JobPosting> searchByKeyword(String regex, Pageable pageable);
}
