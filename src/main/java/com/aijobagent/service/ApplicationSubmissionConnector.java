package com.aijobagent.service;

import com.aijobagent.model.CandidateProfile;
import com.aijobagent.model.JobPosting;

public interface ApplicationSubmissionConnector {

    String getProviderId();

    boolean supports(JobPosting job);

    SubmissionReceipt submit(JobPosting job, CandidateProfile profile, String coverLetter, String idempotencyKey);

    record SubmissionReceipt(String confirmationReference, String message) {}
}
