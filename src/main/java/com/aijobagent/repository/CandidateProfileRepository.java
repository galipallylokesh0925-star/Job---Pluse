package com.aijobagent.repository;

import com.aijobagent.model.CandidateProfile;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data MongoDB repository for CandidateProfile documents.
 */
@Repository
public interface CandidateProfileRepository extends MongoRepository<CandidateProfile, String> {

    /**
     * Retrieves the most recently updated candidate profile.
     * In single-user / desktop agent mode, this represents the active candidate profile.
     */
    Optional<CandidateProfile> findTopByOrderByUpdatedAtDesc();

    /**
     * Finds a candidate profile by primary email address.
     */
    Optional<CandidateProfile> findByEmail(String email);
}
