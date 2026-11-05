package com.aijobagent.service;

import com.aijobagent.dto.profile.CandidateProfileDto;
import com.aijobagent.dto.profile.CandidateProfileUpdateRequest;
import com.aijobagent.dto.profile.ResumeUploadResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service contract for managing the candidate's profile, parsed resume metadata,
 * and search preferences.
 */
public interface CandidateProfileService {

    /**
     * Retrieves the current candidate profile, or generates a sensible initialized default
     * with system configuration values if none exists yet.
     */
    CandidateProfileDto getActiveProfile();

    /**
     * Updates an existing profile or creates a customized profile with user-submitted preferences.
     */
    CandidateProfileDto updateProfile(CandidateProfileUpdateRequest request);

    /**
     * Uploads and parses a PDF/DOCX resume, merges extracted data with the profile,
     * persists it in MongoDB, and returns an enriched response.
     */
    ResumeUploadResponse uploadAndParseResume(MultipartFile file);

    /**
     * Returns the full plain text extracted from the candidate's resume for AI matching prompts.
     */
    String getRawResumeText(String profileId);

    /**
     * Clears or removes the candidate profile.
     */
    void deleteProfile(String profileId);
}
