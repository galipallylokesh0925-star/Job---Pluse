package com.aijobagent.connector;

import com.aijobagent.model.JobPosting;
import com.aijobagent.model.JobPreferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class RssJobFeedConnector implements JobSourceConnector {

    private static final Logger log = LoggerFactory.getLogger(RssJobFeedConnector.class);
    private static final int MAX_JOBS_PER_RUN = 100;
    private final RestClient restClient;
    private final List<ConfiguredFeed> feeds;
    private final boolean enabled;

    @Autowired
    public RssJobFeedConnector(
            @Value("${app.discovery.connectors.rss.enabled:true}") boolean enabled,
            @Value("${app.discovery.connectors.rss.feeds:}") String configuredFeeds) {
        this(enabled, configuredFeeds, RestClient.builder()
                .defaultHeader(HttpHeaders.USER_AGENT, "AIJobAgent/1.0 (Configured public job feed reader)")
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_XML_VALUE + ", application/atom+xml, */*")
                .build());
    }

    RssJobFeedConnector(boolean enabled, String configuredFeeds, RestClient restClient) {
        this.enabled = enabled;
        this.feeds = parseFeeds(configuredFeeds);
        this.restClient = restClient;
    }

    @Override
    public String getSourceId() {
        return "RSS_FEED";
    }

    @Override
    public String getDisplayName() {
        return "Configured Public RSS/Atom Job Feeds";
    }

    @Override
    public boolean isEnabled() {
        return enabled && !feeds.isEmpty();
    }

    @Override
    @SuppressWarnings("null")
    public List<JobPosting> fetchJobs(JobPreferences preferences) {
        List<JobPosting> jobs = new ArrayList<>();
        if (!isEnabled()) {
            return jobs;
        }
        for (ConfiguredFeed feed : feeds) {
            try {
                String xml = restClient.get().uri(feed.url()).retrieve().body(String.class);
                if (xml == null || xml.isBlank()) {
                    continue;
                }
                for (Element entry : readEntries(xml)) {
                    String title = childText(entry, "title");
                    String link = childLink(entry);
                    if (title.isBlank() || link.isBlank()) {
                        continue;
                    }
                    JobPosting job = new JobPosting();
                    job.setExternalId("rss-" + link);
                    job.setTitle(title);
                    job.setCompany(feed.company());
                    job.setLocation("Not specified");
                    job.setWorkMode(title.toLowerCase(Locale.ROOT).contains("remote") ? "REMOTE" : "UNKNOWN");
                    job.setDescription(stripMarkup(firstNonBlank(
                            childText(entry, "description"), childText(entry, "summary"), childText(entry, "content"))));
                    job.setApplyUrl(link);
                    job.setSource(getSourceId());
                    job.setPostedAt(parseDate(firstNonBlank(childText(entry, "pubDate"), childText(entry, "published"))));
                    job.setDiscoveredAt(Instant.now());
                    job.setStatus("DISCOVERED");
                    jobs.add(job);
                    if (jobs.size() >= MAX_JOBS_PER_RUN) {
                        return jobs;
                    }
                }
            } catch (Exception ex) {
                log.warn("Configured RSS feed '{}' could not be fetched: {}", feed.company(), ex.getMessage());
            }
        }
        return jobs;
    }

    private List<ConfiguredFeed> parseFeeds(String configured) {
        if (configured == null || configured.isBlank()) {
            return List.of();
        }
        List<ConfiguredFeed> result = new ArrayList<>();
        for (String item : configured.split(";")) {
            String[] parts = item.trim().split("=", 2);
            if (parts.length != 2 || parts[0].isBlank()) {
                throw new IllegalArgumentException("RSS feeds must use the format CompanyName=https://feed-url.");
            }
            URI uri = URI.create(parts[1].trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException("Configured RSS feeds must be HTTPS URLs without embedded credentials.");
            }
            result.add(new ConfiguredFeed(parts[0].trim(), uri.toASCIIString()));
        }
        return List.copyOf(result);
    }

    private List<Element> readEntries(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        NodeList allNodes = factory.newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml))).getElementsByTagName("*");
        List<Element> entries = new ArrayList<>();
        for (int i = 0; i < allNodes.getLength() && entries.size() < MAX_JOBS_PER_RUN; i++) {
            Node node = allNodes.item(i);
            if (node instanceof Element element) {
                String name = localName(element);
                if ("item".equals(name) || "entry".equals(name)) {
                    entries.add(element);
                }
            }
        }
        return entries;
    }

    private String childText(Element parent, String wantedName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element element && wantedName.equals(localName(element))) {
                return element.getTextContent().trim();
            }
        }
        return "";
    }

    private String childLink(Element entry) {
        NodeList children = entry.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element element && "link".equals(localName(element))) {
                String href = element.getAttribute("href");
                return href.isBlank() ? element.getTextContent().trim() : href.trim();
            }
        }
        return "";
    }

    private String localName(Element element) {
        String localName = element.getLocalName();
        return localName == null ? element.getTagName() : localName;
    }

    private Instant parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            try {
                return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            } catch (DateTimeParseException ignored) {
                log.debug("Ignoring unrecognized posting date in RSS feed: {}", value);
                return null;
            }
        }
    }

    private String stripMarkup(String value) {
        return value.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private record ConfiguredFeed(String company, String url) {}
}
