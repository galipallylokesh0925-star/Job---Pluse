package com.aijobagent.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;

/**
 * Technical project entry for candidate profiles.
 */
@Schema(description = "Technical project portfolio entry")
public class ProjectDetail {

    @Schema(description = "Project name / headline", example = "E-Commerce Microservices Engine")
    private String title;

    @Schema(description = "Overview of project goals and achievements")
    private String description;

    @Schema(description = "Technologies and frameworks utilized", example = "[\"Spring Boot\", \"MongoDB\", \"Kafka\", \"Docker\"]")
    private List<String> technologies = new ArrayList<>();

    @Schema(description = "Source code repository URL", example = "https://github.com/candidate/ecommerce-microservices")
    private String repoUrl;

    @Schema(description = "Live demo or deployment URL", example = "https://demo.example.com")
    private String liveUrl;

    public ProjectDetail() {}

    public ProjectDetail(String title, String description, List<String> technologies, String repoUrl, String liveUrl) {
        this.title = title;
        this.description = description;
        this.technologies = technologies != null ? technologies : new ArrayList<>();
        this.repoUrl = repoUrl;
        this.liveUrl = liveUrl;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getTechnologies() {
        return technologies;
    }

    public void setTechnologies(List<String> technologies) {
        this.technologies = technologies;
    }

    public String getRepoUrl() {
        return repoUrl;
    }

    public void setRepoUrl(String repoUrl) {
        this.repoUrl = repoUrl;
    }

    public String getLiveUrl() {
        return liveUrl;
    }

    public void setLiveUrl(String liveUrl) {
        this.liveUrl = liveUrl;
    }
}
