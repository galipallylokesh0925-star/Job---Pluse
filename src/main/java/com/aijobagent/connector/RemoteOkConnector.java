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
 * Job source connector for RemoteOK (remoteok.com/api).
 * Retrieves global remote technology and software engineering opportunities.
 */
@Component
public class RemoteOkConnector implements JobSourceConnector {

    private static final Logger log = LoggerFactory.getLogger(RemoteOkConnector.class);
    private static final String SOURCE_ID = "REMOTE_OK";
    private static final String DISPLAY_NAME = "RemoteOK Tech Jobs";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final @NonNull String apiUrl;

    public RemoteOkConnector(
            @Value("${app.discovery.connectors.remote-ok.enabled:true}") boolean enabled,
            @Value("${app.discovery.connectors.remote-ok.api-url:https://remoteok.com/api}") @NonNull String apiUrl,
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
            log.info("RemoteOK connector is disabled; skipping.");
            return List.of();
        }

        List<JobPosting> jobs = new ArrayList<>();
        try {
            log.info("Fetching remote jobs from RemoteOK API: {}", apiUrl);
            String rawJson = restClient.get()
                    .uri(apiUrl)
                    .retrieve()
                    .body(String.class);

            if (rawJson == null || rawJson.isBlank()) {
                log.warn("RemoteOK API returned empty response body.");
                return List.of();
            }

            JsonNode rootNode = objectMapper.readTree(rawJson);
            if (!rootNode.isArray()) {
                log.warn("RemoteOK unexpected root format: expected JSON array.");
                return List.of();
            }

            // In RemoteOK API, index 0 is legal metadata. Jobs start from index 1.
            for (int i = 1; i < rootNode.size(); i++) {
                JsonNode item = rootNode.get(i);
                if (item == null || !item.has("position")) continue;

                String title = item.path("position").asText("");
                String company = item.path("company").asText("Unknown Company");
                String url = item.path("url").asText("");
                String applyUrl = item.path("apply_url").asText(url);
                String location = item.path("location").asText("Worldwide Remote");
                String description = item.path("description").asText("");
                long epoch = item.path("epoch").asLong(0);

                List<String> tags = new ArrayList<>();
                JsonNode tagsNode = item.path("tags");
                if (tagsNode.isArray()) {
                    for (JsonNode t : tagsNode) {
                        tags.add(t.asText());
                    }
                }

                // Filter for tech relevance
                if (!isRelevantJob(title, tags, preferences)) {
                    continue;
                }

                JobPosting posting = new JobPosting();
                posting.setExternalId("remoteok-" + item.path("id").asText(String.valueOf(i)));
                posting.setTitle(title);
                posting.setCompany(company);
                posting.setLocation(location.isBlank() ? "Remote" : location);
                posting.setWorkMode("REMOTE");
                posting.setEmploymentType("FULL_TIME");
                posting.setExperienceLevel("ENTRY_LEVEL_FRESHER");
                posting.setCountry("Worldwide");
                posting.setDescription(cleanDescription(description));
                posting.setRequiredSkills(tags);
                posting.setSalaryCurrency("USD");
                posting.setApplyUrl(applyUrl.isBlank() ? url : applyUrl);
                posting.setSource(SOURCE_ID);
                posting.setPostedAt(epoch > 0 ? Instant.ofEpochSecond(epoch) : Instant.now());
                posting.setDiscoveredAt(Instant.now());
                posting.setStatus("DISCOVERED");

                jobs.add(posting);
                if (jobs.size() >= 30) break; // Limit batch per run
            }

            log.info("RemoteOK connector successfully parsed {} relevant jobs.", jobs.size());
        } catch (Exception ex) {
            log.warn("RemoteOK connector request failed (offline / rate-limited): {}", ex.getMessage());
        }

        return jobs;
    }

    private boolean isRelevantJob(String title, List<String> tags, JobPreferences preferences) {
        String lowerTitle = title.toLowerCase();
        List<String> keywords = List.of("developer", "engineer", "software", "java", "full stack", "frontend", "backend", "python", "intern", "web");

        boolean matchesKeyword = keywords.stream().anyMatch(lowerTitle::contains);
        boolean matchesTag = tags.stream().anyMatch(t -> {
            String lowerTag = t.toLowerCase();
            return lowerTag.contains("java") || lowerTag.contains("python") || lowerTag.contains("react") || lowerTag.contains("backend") || lowerTag.contains("dev");
        });

        return matchesKeyword || matchesTag;
    }

    private String cleanDescription(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("<[^>]*>", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
