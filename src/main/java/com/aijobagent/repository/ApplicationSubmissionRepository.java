package com.aijobagent.repository;

import com.aijobagent.model.ApplicationSubmission;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ApplicationSubmissionRepository extends MongoRepository<ApplicationSubmission, String> {

    Optional<ApplicationSubmission> findByIdempotencyKey(String idempotencyKey);

    List<ApplicationSubmission> findTop50ByCandidateProfileIdOrderByCreatedAtDesc(String candidateProfileId);
}
