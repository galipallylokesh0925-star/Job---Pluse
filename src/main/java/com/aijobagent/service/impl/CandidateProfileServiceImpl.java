package com.aijobagent.service.impl;

import com.aijobagent.dto.profile.CandidateProfileDto;
import com.aijobagent.dto.profile.CandidateProfileUpdateRequest;
import com.aijobagent.dto.profile.ExtractedResumeData;
import com.aijobagent.dto.profile.ResumeUploadResponse;
import com.aijobagent.exception.ResourceNotFoundException;
import com.aijobagent.model.CandidateProfile;
import com.aijobagent.model.JobPreferences;
import com.aijobagent.model.ResumeMetadata;
import com.aijobagent.repository.CandidateProfileRepository;
import com.aijobagent.service.CandidateProfileService;
import com.aijobagent.service.ResumeParserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.*;

/**
 * Implementation of CandidateProfileService managing MongoDB persistence,
 * resume upload handling, and preference updates.
 */
@Service
public class CandidateProfileServiceImpl implements CandidateProfileService {

    private static final Logger log = LoggerFactory.getLogger(CandidateProfileServiceImpl.class);

    private final CandidateProfileRepository profileRepository;
    private final ResumeParserService resumeParserService;

    @Value("${app.defaults.preferred-roles:Java Developer,Full Stack Developer,Software Engineer}")
    private List<String> defaultPreferredRoles;

    @Value("${app.defaults.work-modes:ON_SITE,HYBRID,REMOTE}")
    private List<String> defaultWorkModes;

    @Value("${app.defaults.experience-level:ENTRY_LEVEL_FRESHER}")
    private String defaultExperienceLevel;

    @Value("${app.defaults.max-daily-applications:20}")
    private int defaultMaxDailyApplications;

    public CandidateProfileServiceImpl(CandidateProfileRepository profileRepository,
                                      ResumeParserService resumeParserService) {
        this.profileRepository = profileRepository;
        this.resumeParserService = resumeParserService;
    }

    @Override
    public CandidateProfileDto getActiveProfile() {
        CandidateProfile profile = profileRepository.findTopByOrderByUpdatedAtDesc()
                .orElseGet(this::createDefaultInitialProfile);
        return toDto(profile);
    }

    @Override
    @Transactional
    public CandidateProfileDto updateProfile(CandidateProfileUpdateRequest request) {
        CandidateProfile profile = profileRepository.findTopByOrderByUpdatedAtDesc()
                .orElseGet(CandidateProfile::new);

        if (request.fullName() != null && !request.fullName().isBlank()) {
            profile.setFullName(request.fullName());
        }
        if (request.email() != null && !request.email().isBlank()) {
            profile.setEmail(request.email());
        }
        if (request.phone() != null) {
            profile.setPhone(request.phone());
        }
        if (request.location() != null) {
            profile.setLocation(request.location());
        }
        if (request.linkedInUrl() != null) {
            profile.setLinkedInUrl(request.linkedInUrl());
        }
        if (request.githubUrl() != null) {
            profile.setGithubUrl(request.githubUrl());
        }
        if (request.portfolioUrl() != null) {
            profile.setPortfolioUrl(request.portfolioUrl());
        }
        if (request.summary() != null) {
            profile.setSummary(request.summary());
        }
        if (request.skills() != null) {
            profile.setSkills(request.skills());
        }
        if (request.education() != null) {
            profile.setEducation(request.education());
        }
        if (request.experience() != null) {
            profile.setExperience(request.experience());
        }
        if (request.projects() != null) {
            profile.setProjects(request.projects());
        }
        if (request.certifications() != null) {
            profile.setCertifications(request.certifications());
        }
        if (request.preferences() != null) {
            profile.setPreferences(request.preferences());
        }

        CandidateProfile saved = profileRepository.save(Objects.requireNonNull(profile));
        log.info("Candidate profile updated successfully with ID: {}", saved.getId());
        return toDto(saved);
    }

    @Override
    @Transactional
    public ResumeUploadResponse uploadAndParseResume(MultipartFile file) {
        log.info("Processing resume upload: name={}, size={} bytes, contentType={}",
                file.getOriginalFilename(), file.getSize(), file.getContentType());

        // Parse file via PDFBox / POI engine
        ExtractedResumeData extracted = resumeParserService.parse(file);

        // Fetch active profile or initialize new one
        CandidateProfile profile = profileRepository.findTopByOrderByUpdatedAtDesc()
                .orElseGet(this::createDefaultInitialProfile);

        // Populate contact info if not already set or if empty
        if ((profile.getFullName() == null || profile.getFullName().isBlank()) && extracted.fullName() != null) {
            profile.setFullName(extracted.fullName());
        }
        if ((profile.getEmail() == null || profile.getEmail().isBlank()) && extracted.email() != null) {
            profile.setEmail(extracted.email());
        }
        if ((profile.getPhone() == null || profile.getPhone().isBlank()) && extracted.phone() != null) {
            profile.setPhone(extracted.phone());
        }
        if ((profile.getLocation() == null || profile.getLocation().isBlank()) && extracted.location() != null) {
            profile.setLocation(extracted.location());
        }
        if (extracted.linkedInUrl() != null) {
            profile.setLinkedInUrl(extracted.linkedInUrl());
        }
        if (extracted.githubUrl() != null) {
            profile.setGithubUrl(extracted.githubUrl());
        }
        if (extracted.portfolioUrl() != null) {
            profile.setPortfolioUrl(extracted.portfolioUrl());
        }
        if (extracted.summary() != null && (profile.getSummary() == null || profile.getSummary().isBlank())) {
            profile.setSummary(extracted.summary());
        }

        // Merge skills (union set to avoid duplicates)
        Set<String> mergedSkills = new LinkedHashSet<>(profile.getSkills() != null ? profile.getSkills() : List.of());
        if (extracted.skills() != null) {
            mergedSkills.addAll(extracted.skills());
        }
        profile.setSkills(new ArrayList<>(mergedSkills));

        // Add education if existing is empty
        if ((profile.getEducation() == null || profile.getEducation().isEmpty()) && !extracted.education().isEmpty()) {
            profile.setEducation(extracted.education());
        }

        // Add experience if existing is empty
        if ((profile.getExperience() == null || profile.getExperience().isEmpty()) && !extracted.experience().isEmpty()) {
            profile.setExperience(extracted.experience());
        }

        // Add projects if existing is empty
        if ((profile.getProjects() == null || profile.getProjects().isEmpty()) && !extracted.projects().isEmpty()) {
            profile.setProjects(extracted.projects());
        }

        // Save raw text for AI prompt injection (Phase 4)
        profile.setRawResumeText(extracted.rawText());

        // Update resume metadata
        ResumeMetadata metadata = new ResumeMetadata(
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize(),
                extracted.characterCount(),
                extracted.parserEngine(),
                Instant.now()
        );
        profile.setResumeMetadata(metadata);

        CandidateProfile saved = profileRepository.save(profile);
        log.info("Resume parsed and saved to profile ID: {}. Extracted {} skills, {} characters.",
                saved.getId(), extracted.skills().size(), extracted.characterCount());

        return new ResumeUploadResponse(
                true,
                "Resume successfully parsed via " + extracted.parserEngine() + " and profile updated.",
                file.getOriginalFilename(),
                extracted.parserEngine(),
                extracted.characterCount(),
                extracted.skills().size(),
                toDto(saved)
        );
    }

    @Override
    public String getRawResumeText(String profileId) {
        CandidateProfile profile = findProfileByIdOrActive(profileId);
        if (profile.getRawResumeText() == null || profile.getRawResumeText().isBlank()) {
            return "No resume document has been parsed yet for this candidate.";
        }
        return profile.getRawResumeText();
    }

    @Override
    @Transactional
    public void deleteProfile(String profileId) {
        if (profileId != null && !profileId.isBlank()) {
            profileRepository.deleteById(profileId);
        } else {
            profileRepository.deleteAll();
        }
        log.info("Candidate profile deleted.");
    }

    private CandidateProfile findProfileByIdOrActive(String profileId) {
        if (profileId != null && !profileId.isBlank()) {
            return profileRepository.findById(profileId)
                    .orElseThrow(() -> new ResourceNotFoundException("Profile not found with ID: " + profileId));
        }
        return profileRepository.findTopByOrderByUpdatedAtDesc()
                .orElseThrow(() -> new ResourceNotFoundException("No candidate profile found."));
    }

    private CandidateProfile createDefaultInitialProfile() {
        CandidateProfile profile = new CandidateProfile();
        JobPreferences prefs = new JobPreferences();
        prefs.setTargetRoles(new ArrayList<>(defaultPreferredRoles));
        prefs.setPreferredWorkModes(new ArrayList<>(defaultWorkModes));
        prefs.setExperienceLevel(defaultExperienceLevel);
        prefs.setMaxDailyApplications(defaultMaxDailyApplications);
        prefs.setPreferredLocations(List.of("Bengaluru", "Pune", "Hyderabad", "Remote"));
        profile.setPreferences(prefs);
        return profileRepository.save(profile);
    }

    private CandidateProfileDto toDto(CandidateProfile p) {
        boolean hasResume = p.getResumeMetadata() != null && p.getRawResumeText() != null && !p.getRawResumeText().isBlank();
        return new CandidateProfileDto(
                p.getId(),
                p.getFullName(),
                p.getEmail(),
                p.getPhone(),
                p.getLocation(),
                p.getLinkedInUrl(),
                p.getGithubUrl(),
                p.getPortfolioUrl(),
                p.getSummary(),
                p.getSkills(),
                p.getEducation(),
                p.getExperience(),
                p.getProjects(),
                p.getCertifications(),
                p.getPreferences(),
                p.getResumeMetadata(),
                hasResume,
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
