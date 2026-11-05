package com.aijobagent.connector;

import com.aijobagent.model.JobPosting;
import com.aijobagent.model.JobPreferences;

import java.util.List;

public interface PortalJobSourceConnector extends JobSourceConnector {

    boolean supportsPortalUrl(String portalUrl);

    List<JobPosting> fetchJobsFromPortalUrl(String portalUrl, JobPreferences preferences);
}
