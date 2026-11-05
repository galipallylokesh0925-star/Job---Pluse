package com.aijobagent.connector;

import com.aijobagent.model.JobPosting;
import com.aijobagent.model.JobPreferences;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JobConnectorsTest {

    @Test
    @DisplayName("EnterpriseCareerPortalConnector returns valid Indian & global fresher developer opportunities")
    void shouldReturnEnterpriseJobs() {
        EnterpriseCareerPortalConnector connector = new EnterpriseCareerPortalConnector(true);

        assertEquals("ENTERPRISE_PORTAL", connector.getSourceId());
        assertEquals("Enterprise Career Portals", connector.getDisplayName());
        assertTrue(connector.isEnabled());

        JobPreferences prefs = new JobPreferences();
        List<JobPosting> jobs = connector.fetchJobs(prefs);

        assertNotNull(jobs);
        assertFalse(jobs.isEmpty());
        assertTrue(jobs.size() >= 8);

        // Verify structure of seed jobs
        JobPosting firstJob = jobs.get(0);
        assertNotNull(firstJob.getTitle());
        assertNotNull(firstJob.getCompany());
        assertNotNull(firstJob.getApplyUrl());
        assertEquals("ENTERPRISE_PORTAL", firstJob.getSource());
        assertNotNull(firstJob.getRequiredSkills());
        assertFalse(firstJob.getRequiredSkills().isEmpty());
        assertTrue(firstJob.getRequiredSkills().contains("Java"));
    }

    @Test
    @DisplayName("EnterpriseCareerPortalConnector returns empty list when disabled")
    void shouldReturnEmptyWhenDisabled() {
        EnterpriseCareerPortalConnector connector = new EnterpriseCareerPortalConnector(false);
        assertFalse(connector.isEnabled());

        List<JobPosting> jobs = connector.fetchJobs(new JobPreferences());
        assertNotNull(jobs);
        assertTrue(jobs.isEmpty());
    }
}
