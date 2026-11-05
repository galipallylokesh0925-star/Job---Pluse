package com.aijobagent.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;

/**
 * Work experience / internship entry for candidate profiles.
 */
@Schema(description = "Work or internship experience entry")
public class WorkExperience {

    @Schema(description = "Company or organization name", example = "Infosys")
    private String company;

    @Schema(description = "Job title or position held", example = "Software Engineer Intern")
    private String title;

    @Schema(description = "Location of employment", example = "Bengaluru, India")
    private String location;

    @Schema(description = "Start date", example = "Jan 2023")
    private String startDate;

    @Schema(description = "End date or 'Present'", example = "Jul 2023")
    private String endDate;

    @Schema(description = "Whether this is the current active role", example = "false")
    private boolean current;

    @Schema(description = "Summary of achievements, responsibilities, and technologies used")
    private List<String> bulletPoints = new ArrayList<>();

    public WorkExperience() {}

    public WorkExperience(String company, String title, String location, String startDate,
                          String endDate, boolean current, List<String> bulletPoints) {
        this.company = company;
        this.title = title;
        this.location = location;
        this.startDate = startDate;
        this.endDate = endDate;
        this.current = current;
        this.bulletPoints = bulletPoints != null ? bulletPoints : new ArrayList<>();
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public boolean isCurrent() {
        return current;
    }

    public void setCurrent(boolean current) {
        this.current = current;
    }

    public List<String> getBulletPoints() {
        return bulletPoints;
    }

    public void setBulletPoints(List<String> bulletPoints) {
        this.bulletPoints = bulletPoints;
    }
}
