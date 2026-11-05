package com.aijobagent.connector;

import com.aijobagent.model.JobPosting;
import com.aijobagent.model.JobPreferences;

import java.util.List;

/**
 * Pluggable connector contract for fetching job postings from external portals,
 * career pages, or public REST APIs.
 */
public interface JobSourceConnector {

    /**
     * Unique short alphanumeric identifier for this source (e.g. "REMOTE_OK", "ARBEITNOW").
     */
    String getSourceId();

    /**
     * User-friendly display name (e.g. "RemoteOK Tech Jobs").
     */
    String getDisplayName();

    /**
     * Indicates whether this connector is currently enabled in application configuration.
     */
    boolean isEnabled();

    /**
     * Fetches and maps job postings from the external source, optionally filtering
     * against candidate search preferences.
     *
     * @param preferences candidate job preferences
     * @return List of normalized JobPosting objects (before persistence / deduplication)
     */
    List<JobPosting> fetchJobs(JobPreferences preferences);
}
