package com.aijobagent.service;

import com.aijobagent.dto.job.DiscoveryRunResult;
import com.aijobagent.dto.job.JobPostingDto;
import com.aijobagent.dto.job.JobSearchCriteria;
import com.aijobagent.dto.job.JobStatsDto;
import com.aijobagent.dto.job.JobImportRequest;
import com.aijobagent.dto.job.JobImportResult;
import com.aijobagent.dto.job.PortalLinkDiscoveryResult;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Service contract for managing multi-source job discovery runs,
 * deduplication, and filtered searches.
 */
public interface JobDiscoveryService {

    /**
     * Executes an automated job discovery run across all active connectors
     * (or specific connector IDs if supplied).
     *
     * @param connectorIds optional list of specific connector source IDs to run; if null or empty, runs all enabled connectors
     * @return DiscoveryRunResult summarizing discovered jobs and deduplication counts
     */
    DiscoveryRunResult runDiscovery(List<String> connectorIds);

    /**
     * Imports a job alert copied from a messaging channel, email, or job platform.
     */
    JobImportResult importJobAlert(JobImportRequest request);

    /**
     * Scans user-supplied public ATS board or job links and stores supported postings.
     */
    PortalLinkDiscoveryResult discoverPortalLinks(List<String> portalUrls);

    /**
     * Searches and filters discovered job postings using pagination and criteria.
     */
    Page<JobPostingDto> searchJobs(JobSearchCriteria criteria);

    /**
     * Retrieves a single job posting by MongoDB ID.
     */
    JobPostingDto getJobById(String id);

    /**
     * Calculates aggregated metrics across all indexed postings for dashboard counters.
     */
    JobStatsDto getJobStats();

    /**
     * Deletes all job postings (useful for testing or cache refresh).
     */
    void clearAllJobs();
}
