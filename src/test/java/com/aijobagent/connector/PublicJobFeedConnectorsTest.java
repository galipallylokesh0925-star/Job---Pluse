package com.aijobagent.connector;

import com.aijobagent.model.JobPosting;
import com.aijobagent.model.JobPreferences;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PublicJobFeedConnectorsTest {

    @Test
    @DisplayName("Maps public Greenhouse board postings")
    void shouldReadGreenhouseBoard() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://boards-api.greenhouse.io/v1/boards/example/jobs?content=true"))
                .andRespond(withSuccess("""
                        {"jobs":[{"id":123,"title":"Junior Java Developer",
                        "location":{"name":"Bengaluru, India"},
                        "absolute_url":"https://boards.greenhouse.io/example/jobs/123",
                        "content":"<p>Java and Spring Boot</p>"}]}
                        """, MediaType.APPLICATION_JSON));
        GreenhouseJobBoardConnector connector = new GreenhouseJobBoardConnector(
                true, "example", new ObjectMapper(), builder.build());

        List<JobPosting> jobs = connector.fetchJobs(new JobPreferences());

        assertEquals(1, jobs.size());
        assertEquals("GREENHOUSE", jobs.get(0).getSource());
        assertEquals("example", jobs.get(0).getCompany());
        assertEquals("Junior Java Developer", jobs.get(0).getTitle());
        assertTrue(jobs.get(0).getDescription().contains("Java and Spring Boot"));
        server.verify();
    }

    @Test
    @DisplayName("Scans a supplied Greenhouse link without configured board identifiers")
    void shouldScanSuppliedGreenhouseBoardLink() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://boards-api.greenhouse.io/v1/boards/example/jobs?content=true"))
                .andRespond(withSuccess("""
                        {"jobs":[{"id":123,"title":"Junior Java Developer",
                        "location":{"name":"Bengaluru, India"},
                        "absolute_url":"https://boards.greenhouse.io/example/jobs/123",
                        "content":"Java role"},
                        {"id":456,"title":"Designer",
                        "location":{"name":"Remote"},
                        "absolute_url":"https://boards.greenhouse.io/example/jobs/456",
                        "content":"Design role"}]}
                        """, MediaType.APPLICATION_JSON));
        GreenhouseJobBoardConnector connector = new GreenhouseJobBoardConnector(
                true, "", new ObjectMapper(), builder.build());

        List<JobPosting> jobs = connector.fetchJobsFromPortalUrl(
                "https://boards.greenhouse.io/example/jobs/123", new JobPreferences());

        assertEquals(1, jobs.size());
        assertEquals("greenhouse-123", jobs.getFirst().getExternalId());
        assertTrue(connector.supportsPortalUrl("https://job-boards.greenhouse.io/example"));
        assertFalse(connector.supportsPortalUrl("https://evil.example/example"));
        server.verify();
    }

    @Test
    @DisplayName("Maps public Lever postings")
    void shouldReadLeverSite() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.lever.co/v0/postings/example?mode=json"))
                .andRespond(withSuccess("""
                        [{"id":"abc","text":"Backend Engineer","hostedUrl":"https://jobs.lever.co/example/abc",
                        "categories":{"location":"Remote","commitment":"Full-time"},
                        "descriptionPlain":"Java, APIs","createdAt":1761782400000}]
                        """, MediaType.APPLICATION_JSON));
        LeverPostingsConnector connector = new LeverPostingsConnector(
                true, "example", new ObjectMapper(), builder.build());

        List<JobPosting> jobs = connector.fetchJobs(new JobPreferences());

        assertEquals(1, jobs.size());
        assertEquals("LEVER", jobs.get(0).getSource());
        assertEquals("Backend Engineer", jobs.get(0).getTitle());
        assertEquals("REMOTE", jobs.get(0).getWorkMode());
        assertEquals("Full-time", jobs.get(0).getEmploymentType());
        server.verify();
    }

    @Test
    @DisplayName("Scans a supplied Lever link without configured site identifiers")
    void shouldScanSuppliedLeverJobLink() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.lever.co/v0/postings/example?mode=json"))
                .andRespond(withSuccess("""
                        [{"id":"abc","text":"Backend Engineer","hostedUrl":"https://jobs.lever.co/example/abc",
                        "categories":{"location":"Remote","commitment":"Full-time"},
                        "descriptionPlain":"Java, APIs","createdAt":1761782400000},
                        {"id":"def","text":"Designer","hostedUrl":"https://jobs.lever.co/example/def",
                        "categories":{"location":"Remote","commitment":"Full-time"},
                        "descriptionPlain":"Design","createdAt":1761782400000}]
                        """, MediaType.APPLICATION_JSON));
        LeverPostingsConnector connector = new LeverPostingsConnector(
                true, "", new ObjectMapper(), builder.build());

        List<JobPosting> jobs = connector.fetchJobsFromPortalUrl(
                "https://jobs.lever.co/example/abc", new JobPreferences());

        assertEquals(1, jobs.size());
        assertEquals("lever-abc", jobs.getFirst().getExternalId());
        assertTrue(connector.supportsPortalUrl("https://jobs.lever.co/example"));
        assertFalse(connector.supportsPortalUrl("https://jobs.lever.co.evil.example/example"));
        server.verify();
    }

    @Test
    @DisplayName("Reads RSS job feeds while rejecting insecure feed URLs")
    void shouldReadConfiguredRssFeed() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://careers.example.com/jobs.xml"))
                .andRespond(withSuccess("""
                        <rss version="2.0"><channel><item>
                        <title>Junior Software Engineer</title>
                        <link>https://careers.example.com/jobs/42</link>
                        <description>Java role</description>
                        <pubDate>Fri, 30 Oct 2026 00:00:00 GMT</pubDate>
                        </item></channel></rss>
                        """, MediaType.APPLICATION_XML));
        RssJobFeedConnector connector = new RssJobFeedConnector(
                true, "Example Company=https://careers.example.com/jobs.xml", builder.build());

        List<JobPosting> jobs = connector.fetchJobs(new JobPreferences());

        assertEquals(1, jobs.size());
        assertEquals("RSS_FEED", jobs.get(0).getSource());
        assertEquals("Example Company", jobs.get(0).getCompany());
        assertEquals("Junior Software Engineer", jobs.get(0).getTitle());
        assertEquals("https://careers.example.com/jobs/42", jobs.get(0).getApplyUrl());
        assertNotNull(jobs.get(0).getPostedAt());
        server.verify();
        assertThrows(IllegalArgumentException.class,
                () -> new RssJobFeedConnector(true, "Example=http://careers.example.com/jobs.xml"));
    }

    @Test
    @DisplayName("Disables configured board connectors when no public board IDs are supplied")
    void shouldDisableUnconfiguredAtsFeeds() {
        assertFalse(new GreenhouseJobBoardConnector(true, "", new ObjectMapper()).isEnabled());
        assertFalse(new LeverPostingsConnector(true, "", new ObjectMapper()).isEnabled());
        assertFalse(new RssJobFeedConnector(true, "").isEnabled());
    }
}
