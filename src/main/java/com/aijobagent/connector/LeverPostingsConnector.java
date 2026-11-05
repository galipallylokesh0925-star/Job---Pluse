package com.aijobagent.connector;

import com.aijobagent.model.JobPosting;
import com.aijobagent.model.JobPreferences;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class LeverPostingsConnector implements PortalJobSourceConnector {

    private static final Logger log = LoggerFactory.getLogger(LeverPostingsConnector.class);
    private static final Pattern SITE_TOKEN = Pattern.compile("[A-Za-z0-9_-]{1,100}");
    private static final String SOURCE_ID = "LEVER";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final List<String> sites;
    private final boolean enabled;

    @Autowired
    public LeverPostingsConnector(
            @Value("${app.discovery.connectors.lever.enabled:true}") boolean enabled,
            @Value("${app.discovery.connectors.lever.sites:}") String configuredSites,
            ObjectMapper objectMapper) {
        this(enabled, configuredSites, objectMapper, RestClient.builder()
                .defaultHeader(HttpHeaders.USER_AGENT, "AIJobAgent/1.0 (Authorized public job-board feed)")
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build());
    }

    LeverPostingsConnector(
            boolean enabled,
            String configuredSites,
            ObjectMapper objectMapper,
            RestClient restClient) {
        this.enabled = enabled;
        this.objectMapper = objectMapper;
        this.sites = configuredSites == null || configuredSites.isBlank()
                ? List.of()
                : List.of(configuredSites.split(",")).stream()
                        .map(String::trim)
                        .filter(site -> !site.isBlank())
                        .toList();
        if (sites.stream().anyMatch(site -> !SITE_TOKEN.matcher(site).matches())) {
            throw new IllegalArgumentException("Lever site names may contain only letters, numbers, '_' or '-'.");
        }
        this.restClient = restClient;
    }

    @Override
    public String getSourceId() {
        return SOURCE_ID;
    }

    @Override
    public String getDisplayName() {
        return "Lever Public Job Boards";
    }

    @Override
    public boolean isEnabled() {
        return enabled && !sites.isEmpty();
    }

    @Override
    public boolean supportsPortalUrl(String portalUrl) {
        return enabled && PortalBoardUrl.parse(portalUrl)
                .filter(parsed -> parsed.sourceId().equals(SOURCE_ID))
                .isPresent();
    }

    @Override
    public List<JobPosting> fetchJobs(JobPreferences preferences) {
        List<JobPosting> jobs = new ArrayList<>();
        if (!isEnabled()) {
            return jobs;
        }
        for (String site : sites) {
            try {
                jobs.addAll(fetchSite(site, null));
                if (jobs.size() >= 100) return jobs.subList(0, 100);
            } catch (Exception ex) {
                log.warn("Lever site '{}' could not be fetched: {}", site, ex.getMessage());
            }
        }
        return jobs;
    }

    @Override
    public List<JobPosting> fetchJobsFromPortalUrl(String portalUrl, JobPreferences preferences) {
        if (!supportsPortalUrl(portalUrl)) {
            throw new IllegalArgumentException("Only HTTPS Lever public board and job links are supported.");
        }
        PortalBoardUrl parsed = PortalBoardUrl.parse(portalUrl).orElseThrow();
        try {
            return fetchSite(parsed.board(), parsed.postingId());
        } catch (Exception ex) {
            log.warn("Lever site '{}' could not be fetched from a supplied portal link.", parsed.board(), ex);
            throw new IllegalStateException("The Lever public board could not be fetched.", ex);
        }
    }

    private List<JobPosting> fetchSite(String site, String postingId) throws Exception {
        String response = restClient.get()
                .uri("https://api.lever.co/v0/postings/{site}?mode=json", site)
                .retrieve()
                .body(String.class);
        if (response == null || response.isBlank()) return List.of();
        JsonNode postings = objectMapper.readTree(response);
        if (!postings.isArray()) {
            throw new IllegalStateException("Lever returned an unexpected response.");
        }

        List<JobPosting> jobs = new ArrayList<>();
        for (JsonNode item : postings) {
            String title = item.path("text").asText("").trim();
            String url = item.path("hostedUrl").asText("").trim();
            if (title.isBlank() || url.isBlank()
                    || (postingId != null && !postingId.equals(lastPathSegment(url)))) {
                continue;
            }
            String location = item.path("categories").path("location").asText("Not specified");
            JobPosting job = new JobPosting();
            job.setExternalId("lever-" + item.path("id").asText());
            job.setTitle(title);
            job.setCompany(site);
            job.setLocation(location);
            job.setWorkMode((title + " " + location).toLowerCase().contains("remote") ? "REMOTE" : "UNKNOWN");
            job.setEmploymentType(item.path("categories").path("commitment").asText("UNKNOWN"));
            job.setDescription(item.path("descriptionPlain").asText(""));
            job.setApplyUrl(url);
            job.setSource(SOURCE_ID);
            long createdAt = item.path("createdAt").asLong(0);
            job.setPostedAt(createdAt > 0 ? Instant.ofEpochMilli(createdAt) : null);
            job.setDiscoveredAt(Instant.now());
            job.setStatus("DISCOVERED");
            jobs.add(job);
            if (jobs.size() >= 100) return jobs;
        }
        return jobs;
    }

    private String lastPathSegment(String value) {
        String path = java.net.URI.create(value).getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }
}
