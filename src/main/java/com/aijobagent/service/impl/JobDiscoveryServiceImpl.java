package com.aijobagent.service.impl;

import com.aijobagent.connector.JobSourceConnector;
import com.aijobagent.connector.PortalBoardUrl;
import com.aijobagent.connector.PortalJobSourceConnector;
import com.aijobagent.dto.job.DiscoveryRunResult;
import com.aijobagent.dto.job.JobPostingDto;
import com.aijobagent.dto.job.JobSearchCriteria;
import com.aijobagent.dto.job.JobStatsDto;
import com.aijobagent.dto.job.JobAlertSource;
import com.aijobagent.dto.job.JobImportRequest;
import com.aijobagent.dto.job.JobImportResult;
import com.aijobagent.dto.job.PortalLinkDiscoveryItem;
import com.aijobagent.dto.job.PortalLinkDiscoveryResult;
import com.aijobagent.dto.profile.CandidateProfileDto;
import com.aijobagent.exception.ResourceNotFoundException;
import com.aijobagent.model.JobPosting;
import com.aijobagent.model.JobPreferences;
import com.aijobagent.repository.JobPostingRepository;
import com.aijobagent.service.CandidateProfileService;
import com.aijobagent.service.JobDiscoveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Production implementation of JobDiscoveryService.
 *
 * Orchestrates multi-source connectors, enforces SHA-256 deduplication,
 * filters against candidate profile preferences, and manages MongoDB job storage.
 */
@Service
public class JobDiscoveryServiceImpl implements JobDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(JobDiscoveryServiceImpl.class);

    private final List<JobSourceConnector> connectors;
    private final JobPostingRepository jobRepository;
    private final CandidateProfileService profileService;
    private final MongoTemplate mongoTemplate;

    public JobDiscoveryServiceImpl(
            List<JobSourceConnector> connectors,
            JobPostingRepository jobRepository,
            CandidateProfileService profileService,
            MongoTemplate mongoTemplate) {
        this.connectors = connectors != null ? connectors : List.of();
        this.jobRepository = jobRepository;
        this.profileService = profileService;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public DiscoveryRunResult runDiscovery(List<String> connectorIds) {
        Instant startTime = Instant.now();
        log.info("Starting multi-source job discovery scan. Active registered connectors: {}", connectors.size());

        CandidateProfileDto activeProfile = profileService.getActiveProfile();
        JobPreferences preferences = activeProfile != null && activeProfile.preferences() != null
                ? activeProfile.preferences()
                : new JobPreferences();

        int totalFetched = 0;
        int newJobsSaved = 0;
        int duplicatesSkipped = 0;
        Map<String, Integer> sourceBreakdown = new LinkedHashMap<>();

        // Filter connectors if specific IDs were requested
        List<JobSourceConnector> targetConnectors = connectors.stream()
                .filter(c -> c.isEnabled() && (connectorIds == null || connectorIds.isEmpty() || connectorIds.contains(c.getSourceId())))
                .toList();

        for (JobSourceConnector connector : targetConnectors) {
            String sourceId = connector.getSourceId();
            try {
                log.info("Running connector: {} ({})", connector.getDisplayName(), sourceId);
                List<JobPosting> rawJobs = connector.fetchJobs(preferences);
                int connectorCount = rawJobs != null ? rawJobs.size() : 0;
                totalFetched += connectorCount;
                sourceBreakdown.put(sourceId, connectorCount);

                if (rawJobs != null) {
                    for (JobPosting job : rawJobs) {
                        String hash = computeDedupHash(job);
                        job.setDedupHash(hash);

                        if (isDuplicate(job, hash)) {
                            duplicatesSkipped++;
                        } else {
                            jobRepository.save(job);
                            newJobsSaved++;
                        }
                    }
                }
            } catch (Exception ex) {
                log.error("Connector '{}' encountered an error during scan: {}", sourceId, ex.getMessage(), ex);
                sourceBreakdown.put(sourceId, 0);
            }
        }

        Instant endTime = Instant.now();
        long durationMs = endTime.toEpochMilli() - startTime.toEpochMilli();

        String message = String.format("Discovery run completed in %d ms. Scanned %d jobs across %d connectors: %d new jobs saved, %d duplicates skipped.",
                durationMs, totalFetched, targetConnectors.size(), newJobsSaved, duplicatesSkipped);
        log.info(message);

        return new DiscoveryRunResult(
                true,
                message,
                startTime,
                endTime,
                durationMs,
                totalFetched,
                newJobsSaved,
                duplicatesSkipped,
                sourceBreakdown
        );
    }

    @Override
    public JobImportResult importJobAlert(JobImportRequest request) {
        Objects.requireNonNull(request, "Job alert is required");
        String title = request.title().trim();
        String company = request.company().trim();
        String applyUrl = validateApplicationUrl(request.applyUrl());

        JobPosting job = new JobPosting();
        job.setTitle(title);
        job.setCompany(company);
        job.setLocation(request.location() == null || request.location().isBlank()
                ? "Not specified" : request.location().trim());
        job.setWorkMode(request.workMode() == null || request.workMode().isBlank()
                ? "UNKNOWN" : request.workMode().trim().toUpperCase(Locale.ROOT));
        job.setCountry("Unknown");
        job.setEmploymentType("UNKNOWN");
        job.setExperienceLevel("UNKNOWN");
        job.setDescription(request.description() == null ? "" : request.description().trim());
        job.setRequiredSkills(extractSkills(job.getDescription()));
        job.setApplyUrl(applyUrl);
        job.setSource(sourceId(request.source()));
        job.setExternalId("alert-" + UUID.randomUUID());
        job.setPostedAt(null);
        job.setDiscoveredAt(Instant.now());
        job.setStatus("DISCOVERED");

        String hash = computeDedupHash(job);
        job.setDedupHash(hash);
        Optional<JobPosting> existing = jobRepository.findFirstByApplyUrlIgnoreCase(applyUrl);
        if (existing.isPresent()) {
            return new JobImportResult(true, true,
                    "This application URL is already in your job list.", toDto(existing.get()));
        }
        if (jobRepository.existsByDedupHash(hash)) {
            JobPosting duplicate = jobRepository.findByDedupHash(hash).orElse(job);
            return new JobImportResult(true, true,
                    "This job is already in your job list.", toDto(duplicate));
        }

        JobPosting saved = jobRepository.save(job);
        return new JobImportResult(true, false,
                "Job alert imported. It is ready to be ranked against your profile.", toDto(saved));
    }

    @Override
    public PortalLinkDiscoveryResult discoverPortalLinks(List<String> portalUrls) {
        Objects.requireNonNull(portalUrls, "Portal links are required");
        if (portalUrls.isEmpty() || portalUrls.size() > 20) {
            throw new IllegalArgumentException("Supply between 1 and 20 portal links per scan.");
        }

        Instant startedAt = Instant.now();
        CandidateProfileDto profile = profileService.getActiveProfile();
        JobPreferences preferences = profile != null && profile.preferences() != null
                ? profile.preferences() : new JobPreferences();
        List<PortalLinkDiscoveryItem> outcomes = new ArrayList<>();
        int totalFetched = 0;
        int newJobsSaved = 0;
        int duplicatesSkipped = 0;

        for (String value : portalUrls) {
            String portalUrl = value == null ? "" : value.trim();
            Optional<PortalBoardUrl> parsed = PortalBoardUrl.parse(portalUrl);
            if (parsed.isEmpty()) {
                outcomes.add(new PortalLinkDiscoveryItem(portalUrl, "UNSUPPORTED", "UNSUPPORTED",
                        "Only HTTPS Greenhouse and Lever public board/job links are scanned. For other platforms, import a job alert or paste its details; this app will not sign in or scrape private pages.",
                        0, 0, 0));
                continue;
            }

            PortalJobSourceConnector connector = connectors.stream()
                    .filter(PortalJobSourceConnector.class::isInstance)
                    .map(PortalJobSourceConnector.class::cast)
                    .filter(candidate -> candidate.getSourceId().equals(parsed.get().sourceId()))
                    .filter(candidate -> candidate.supportsPortalUrl(portalUrl))
                    .findFirst()
                    .orElse(null);
            if (connector == null) {
                outcomes.add(new PortalLinkDiscoveryItem(portalUrl, parsed.get().sourceId(), "DISABLED",
                        "This public ATS connector is disabled by application configuration.", 0, 0, 0));
                continue;
            }

            try {
                List<JobPosting> jobs = connector.fetchJobsFromPortalUrl(portalUrl, preferences);
                int fetched = jobs.size();
                int savedCount = 0;
                int duplicateCount = 0;
                for (JobPosting job : jobs) {
                    String hash = computeDedupHash(job);
                    job.setDedupHash(hash);
                    if (isDuplicate(job, hash)) {
                        duplicateCount++;
                    } else {
                        jobRepository.save(job);
                        savedCount++;
                    }
                }
                totalFetched += fetched;
                newJobsSaved += savedCount;
                duplicatesSkipped += duplicateCount;
                outcomes.add(new PortalLinkDiscoveryItem(portalUrl, connector.getSourceId(),
                        "SCANNED", fetched == 0 ? "No open postings were returned for this link."
                        : "Public postings scanned and added to your job list.", fetched, savedCount, duplicateCount));
            } catch (Exception ex) {
                log.warn("Public ATS link scan failed for source {}.", connector.getSourceId(), ex);
                outcomes.add(new PortalLinkDiscoveryItem(portalUrl, connector.getSourceId(), "ERROR",
                        "The public job feed could not be fetched. Check that the link is valid and publicly available.",
                        0, 0, 0));
            }
        }

        Instant completedAt = Instant.now();
        return new PortalLinkDiscoveryResult(startedAt, completedAt,
                completedAt.toEpochMilli() - startedAt.toEpochMilli(),
                totalFetched, newJobsSaved, duplicatesSkipped, outcomes);
    }

    @Override
    public Page<JobPostingDto> searchJobs(JobSearchCriteria criteria) {
        Query query = new Query();

        // 1. Keyword search (case-insensitive across title, company, skills, location)
        if (criteria.keyword() != null && !criteria.keyword().isBlank()) {
            Pattern regex = Pattern.compile(Pattern.quote(criteria.keyword().trim()), Pattern.CASE_INSENSITIVE);
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("title").regex(Objects.requireNonNull(regex)),
                    Criteria.where("company").regex(Objects.requireNonNull(regex)),
                    Criteria.where("requiredSkills").regex(Objects.requireNonNull(regex)),
                    Criteria.where("location").regex(Objects.requireNonNull(regex))
            ));
        }

        // 2. Work Mode filter
        if (criteria.workMode() != null && !criteria.workMode().isBlank() && !criteria.workMode().equalsIgnoreCase("ALL")) {
            query.addCriteria(Criteria.where("workMode").is(criteria.workMode().trim().toUpperCase()));
        }

        // 3. Source filter
        if (criteria.source() != null && !criteria.source().isBlank() && !criteria.source().equalsIgnoreCase("ALL")) {
            query.addCriteria(Criteria.where("source").is(criteria.source().trim().toUpperCase()));
        }

        // 4. Status filter
        if (criteria.status() != null && !criteria.status().isBlank() && !criteria.status().equalsIgnoreCase("ALL")) {
            query.addCriteria(Criteria.where("status").is(criteria.status().trim().toUpperCase()));
        }

        // 5. Location filter
        if (criteria.location() != null && !criteria.location().isBlank() && !criteria.location().equalsIgnoreCase("ALL")) {
            Pattern locRegex = Pattern.compile(Pattern.quote(criteria.location().trim()), Pattern.CASE_INSENSITIVE);
            query.addCriteria(Criteria.where("location").regex(Objects.requireNonNull(locRegex)));
        }

        long total = mongoTemplate.count(query, JobPosting.class);

        Pageable pageable = PageRequest.of(
                criteria.getPage(),
                criteria.getSize(),
                Sort.by(Sort.Direction.DESC, "discoveredAt")
        );
        query.with(pageable);

        List<JobPosting> items = mongoTemplate.find(query, JobPosting.class);
        List<JobPostingDto> dtos = items.stream().map(this::toDto).toList();

        return new PageImpl<>(Objects.requireNonNull(dtos), pageable, total);
    }

    @Override
    public JobPostingDto getJobById(String id) {
        String jobId = Objects.requireNonNull(id, "Job ID is required");
        JobPosting job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job posting not found with ID: " + jobId));
        return toDto(job);
    }

    @Override
    public JobStatsDto getJobStats() {
        long totalJobs = jobRepository.count();
        long remoteJobs = jobRepository.countByWorkMode("REMOTE");
        long hybridJobs = jobRepository.countByWorkMode("HYBRID");
        long onSiteJobs = jobRepository.countByWorkMode("ON_SITE");

        Map<String, Long> countBySource = new LinkedHashMap<>();
        for (JobSourceConnector connector : connectors) {
            countBySource.put(connector.getSourceId(), jobRepository.countBySource(connector.getSourceId()));
        }

        Map<String, Long> countByStatus = new LinkedHashMap<>();
        countByStatus.put("DISCOVERED", jobRepository.countByStatus("DISCOVERED"));
        countByStatus.put("MATCHED", jobRepository.countByStatus("MATCHED"));
        countByStatus.put("APPLIED", jobRepository.countByStatus("APPLIED"));

        return new JobStatsDto(
                totalJobs,
                remoteJobs,
                hybridJobs,
                onSiteJobs,
                countBySource,
                countByStatus
        );
    }

    @Override
    public void clearAllJobs() {
        jobRepository.deleteAll();
        log.info("Cleared all discovered job postings from MongoDB.");
    }

    /**
     * Computes a cryptographic SHA-256 fingerprint for deduplication.
     */
    private String computeDedupHash(JobPosting job) {
        String company = job.getCompany() != null ? job.getCompany().trim().toLowerCase() : "";
        String title = job.getTitle() != null ? job.getTitle().trim().toLowerCase() : "";
        String applyUrl = job.getApplyUrl() != null ? job.getApplyUrl().trim().toLowerCase() : "";
        String location = job.getLocation() != null ? job.getLocation().trim().toLowerCase() : "";

        String raw = company + "|" + title + "|" + location + "|" + applyUrl;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // Fallback to base64 encoding if SHA-256 unavailable (should never occur in Java 21)
            return Base64.getUrlEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }
    }

    private boolean isDuplicate(JobPosting job, String hash) {
        return jobRepository.existsByDedupHash(hash)
                || (job.getApplyUrl() != null && !job.getApplyUrl().isBlank()
                && jobRepository.existsByApplyUrlIgnoreCase(job.getApplyUrl()));
    }

    private String validateApplicationUrl(String value) {
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme();
            if (uri.getHost() == null || uri.getUserInfo() != null
                    || scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException("Application URL must be a valid HTTP or HTTPS link.");
            }
            return uri.toASCIIString();
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Application URL must be a valid HTTP or HTTPS link.", ex);
        }
    }

    private String sourceId(JobAlertSource source) {
        return switch (Objects.requireNonNull(source, "Job alert source is required")) {
            case WHATSAPP_ALERT -> "WHATSAPP_ALERT";
            case EMAIL_ALERT -> "EMAIL_ALERT";
            case LINKEDIN_ALERT -> "LINKEDIN_ALERT";
            case INDEED_ALERT -> "INDEED_ALERT";
            case CAREERS_ALERT -> "CAREERS_ALERT";
            case OTHER_ALERT -> "OTHER_ALERT";
        };
    }

    private List<String> extractSkills(String description) {
        if (description == null || description.isBlank()) {
            return List.of();
        }
        String lowerText = description.toLowerCase(Locale.ROOT);
        return List.of("Java", "Python", "JavaScript", "TypeScript", "Spring Boot", "React", "Angular",
                "Node.js", "SQL", "MongoDB", "PostgreSQL", "AWS", "Azure", "Docker", "Kubernetes",
                "Git", "REST APIs", "Microservices", "C++", "C#").stream()
                .filter(skill -> Pattern.compile("(?<![a-zA-Z0-9#+])" + Pattern.quote(skill.toLowerCase(Locale.ROOT))
                        + "(?![a-zA-Z0-9#+])").matcher(lowerText).find())
                .toList();
    }

    private JobPostingDto toDto(JobPosting j) {
        return new JobPostingDto(
                j.getId(),
                j.getTitle(),
                j.getCompany(),
                j.getLocation(),
                j.getWorkMode(),
                j.getEmploymentType(),
                j.getExperienceLevel(),
                j.getCountry(),
                j.getDescription(),
                j.getRequiredSkills(),
                j.getSalaryMin(),
                j.getSalaryMax(),
                j.getSalaryCurrency(),
                j.getApplyUrl(),
                j.getSource(),
                j.getPostedAt(),
                j.getDiscoveredAt(),
                j.getStatus()
        );
    }
}
