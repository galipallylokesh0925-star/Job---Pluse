package com.aijobagent.connector;

import com.aijobagent.model.JobPosting;
import com.aijobagent.model.JobPreferences;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Job source connector for Arbeitnow API (arbeitnow.com/api/job-board-api).
 * Retrieves European and international remote/hybrid technology jobs.
 */
@Component
public class ArbeitnowConnector implements JobSourceConnector {

    private static final Logger log = LoggerFactory.getLogger(ArbeitnowConnector.class);
    private static final String SOURCE_ID = "ARBEITNOW";
    private static final String DISPLAY_NAME = "Arbeitnow Career Feed";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final @NonNull String apiUrl;

    public ArbeitnowConnector(
            @Value("${app.discovery.connectors.arbeitnow.enabled:true}") boolean enabled,
            @Value("${app.discovery.connectors.arbeitnow.api-url:https://www.arbeitnow.com/api/job-board-api}") @NonNull String apiUrl,
            ObjectMapper objectMapper) {
        this.enabled = enabled;
        this.apiUrl = apiUrl;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .defaultHeader(HttpHeaders.USER_AGENT, "AIJobAgent/1.0 (Automated Career Assistant)")
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public String getSourceId() {
        return SOURCE_ID;
    }

    @Override
    public String getDisplayName() {
        return DISPLAY_NAME;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public List<JobPosting> fetchJobs(JobPreferences preferences) {
        if (!enabled) {
            log.info("Arbeitnow connector is disabled; skipping.");
            return List.of();
        }

        List<JobPosting> jobs = new ArrayList<>();
        try {
            log.info("Fetching job postings from Arbeitnow API: {}", apiUrl);
            String rawJson = restClient.get()
                    .uri(apiUrl)
                    .retrieve()
                    .body(String.class);

            if (rawJson == null || rawJson.isBlank()) {
                log.warn("Arbeitnow API returned empty response body.");
                return List.of();
            }

            JsonNode rootNode = objectMapper.readTree(rawJson);
            JsonNode dataArray = rootNode.path("data");
            if (!dataArray.isArray()) {
                log.warn("Arbeitnow unexpected format: 'data' is not a JSON array.");
                return List.of();
            }

            for (JsonNode item : dataArray) {
                String title = item.path("title").asText("");
                String company = item.path("company_name").asText("Tech Employer");
                String url = item.path("url").asText("");
                String location = item.path("location").asText("Remote");
                boolean isRemote = item.path("remote").asBoolean(true);
                String description = item.path("description").asText("");
                long createdAt = item.path("created_at").asLong(0);

                List<String> tags = new ArrayList<>();
                JsonNode tagsNode = item.path("tags");
                if (tagsNode.isArray()) {
                    for (JsonNode t : tagsNode) {
                        tags.add(t.asText());
                    }
                }

                JobPosting posting = new JobPosting();
                posting.setExternalId("arbeitnow-" + item.path("slug").asText(String.valueOf(jobs.size())));
                posting.setTitle(title);
                posting.setCompany(company);
                posting.setLocation(location);
                posting.setWorkMode(isRemote ? "REMOTE" : "HYBRID");
                posting.setEmploymentType("FULL_TIME");
                posting.setExperienceLevel("ENTRY_LEVEL_FRESHER");
                posting.setCountry("Global");
                posting.setDescription(cleanDescription(description));
                posting.setRequiredSkills(tags);
                posting.setSalaryCurrency("EUR");
                posting.setApplyUrl(url);
                posting.setSource(SOURCE_ID);
                posting.setPostedAt(createdAt > 0 ? Instant.ofEpochSecond(createdAt) : Instant.now());
                posting.setDiscoveredAt(Instant.now());
                posting.setStatus("DISCOVERED");

                jobs.add(posting);
                if (jobs.size() >= 30) break;
            }

            log.info("Arbeitnow connector successfully parsed {} jobs.", jobs.size());
        } catch (Exception ex) {
            log.warn("Arbeitnow connector request failed (offline / rate-limited): {}", ex.getMessage());
        }

        return jobs;
    }

    private String cleanDescription(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("<[^>]*>", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
