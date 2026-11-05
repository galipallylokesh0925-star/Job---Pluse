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
public class GreenhouseJobBoardConnector implements PortalJobSourceConnector {

    private static final Logger log = LoggerFactory.getLogger(GreenhouseJobBoardConnector.class);
    private static final Pattern BOARD_TOKEN = Pattern.compile("[A-Za-z0-9_-]{1,100}");
    private static final String SOURCE_ID = "GREENHOUSE";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final List<String> boardTokens;
    private final boolean enabled;

    @Autowired
    public GreenhouseJobBoardConnector(
            @Value("${app.discovery.connectors.greenhouse.enabled:true}") boolean enabled,
            @Value("${app.discovery.connectors.greenhouse.boards:}") String configuredBoards,
            ObjectMapper objectMapper) {
        this(enabled, configuredBoards, objectMapper, RestClient.builder()
                .defaultHeader(HttpHeaders.USER_AGENT, "AIJobAgent/1.0 (Authorized public job-board feed)")
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build());
    }

    GreenhouseJobBoardConnector(
            boolean enabled,
            String configuredBoards,
            ObjectMapper objectMapper,
            RestClient restClient) {
        this.enabled = enabled;
        this.objectMapper = objectMapper;
        this.boardTokens = configuredBoards == null || configuredBoards.isBlank()
                ? List.of()
                : List.of(configuredBoards.split(",")).stream()
                        .map(String::trim)
                        .filter(token -> !token.isBlank())
                        .toList();
        if (boardTokens.stream().anyMatch(token -> !BOARD_TOKEN.matcher(token).matches())) {
            throw new IllegalArgumentException("Greenhouse board tokens may contain only letters, numbers, '_' or '-'.");
        }
        this.restClient = restClient;
    }

    @Override
    public String getSourceId() {
        return SOURCE_ID;
    }

    @Override
    public String getDisplayName() {
        return "Greenhouse Public Job Boards";
    }

    @Override
    public boolean isEnabled() {
        return enabled && !boardTokens.isEmpty();
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
        for (String boardToken : boardTokens) {
            try {
                jobs.addAll(fetchBoard(boardToken, null));
                if (jobs.size() >= 100) return jobs.subList(0, 100);
            } catch (Exception ex) {
                log.warn("Greenhouse board '{}' could not be fetched: {}", boardToken, ex.getMessage());
            }
        }
        return jobs;
    }

    @Override
    public List<JobPosting> fetchJobsFromPortalUrl(String portalUrl, JobPreferences preferences) {
        if (!supportsPortalUrl(portalUrl)) {
            throw new IllegalArgumentException("Only HTTPS Greenhouse public board and job links are supported.");
        }
        PortalBoardUrl parsed = PortalBoardUrl.parse(portalUrl).orElseThrow();
        try {
            return fetchBoard(parsed.board(), parsed.postingId());
        } catch (Exception ex) {
            log.warn("Greenhouse board '{}' could not be fetched from a supplied portal link.", parsed.board(), ex);
            throw new IllegalStateException("The Greenhouse public board could not be fetched.", ex);
        }
    }

    private List<JobPosting> fetchBoard(String boardToken, String postingId) throws Exception {
        String response = restClient.get()
                .uri("https://boards-api.greenhouse.io/v1/boards/{board}/jobs?content=true", boardToken)
                .retrieve()
                .body(String.class);
        if (response == null || response.isBlank()) return List.of();
        JsonNode postings = objectMapper.readTree(response).path("jobs");
        if (!postings.isArray()) {
            throw new IllegalStateException("Greenhouse returned no jobs array.");
        }

        List<JobPosting> jobs = new ArrayList<>();
        for (JsonNode item : postings) {
            String title = item.path("title").asText("").trim();
            String url = item.path("absolute_url").asText("").trim();
            if (title.isBlank() || url.isBlank()
                    || (postingId != null && !postingId.equals(lastPathSegment(url)))) {
                continue;
            }
            JsonNode locationNode = item.path("location");
            JobPosting job = new JobPosting();
            job.setExternalId("greenhouse-" + item.path("id").asText());
            job.setTitle(title);
            job.setCompany(boardToken);
            job.setLocation(locationNode.path("name").asText("Not specified"));
            job.setWorkMode(containsRemote(title, locationNode.asText()) ? "REMOTE" : "UNKNOWN");
            job.setDescription(stripHtml(item.path("content").asText("")));
            job.setApplyUrl(url);
            job.setSource(SOURCE_ID);
            job.setPostedAt(parseDate(item.path("updated_at").asText("")));
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

    private boolean containsRemote(String title, String location) {
        return (title + " " + location).toLowerCase().contains("remote");
    }

    private String stripHtml(String value) {
        return value.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private Instant parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (java.time.format.DateTimeParseException ex) {
            log.debug("Ignoring unrecognized Greenhouse posting date: {}", value);
            return null;
        }
    }
}
