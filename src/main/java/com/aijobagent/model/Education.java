package com.aijobagent.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Education history record for candidate profiles.
 */
@Schema(description = "Academic qualification entry")
public class Education {

    @Schema(description = "Degree or qualification title", example = "B.Tech in Computer Science")
    private String degree;

    @Schema(description = "Institution or university name", example = "National Institute of Technology")
    private String institution;

    @Schema(description = "Field of specialization", example = "Computer Science and Engineering")
    private String fieldOfStudy;

    @Schema(description = "Start year / date", example = "2020")
    private String startDate;

    @Schema(description = "Graduation year / expected date", example = "2024")
    private String endDate;

    @Schema(description = "GPA, percentage, or classification", example = "8.6 CGPA")
    private String grade;

    public Education() {}

    public Education(String degree, String institution, String fieldOfStudy, String startDate, String endDate, String grade) {
        this.degree = degree;
        this.institution = institution;
        this.fieldOfStudy = fieldOfStudy;
        this.startDate = startDate;
        this.endDate = endDate;
        this.grade = grade;
    }

    public String getDegree() {
        return degree;
    }

    public void setDegree(String degree) {
        this.degree = degree;
    }

    public String getInstitution() {
        return institution;
    }

    public void setInstitution(String institution) {
        this.institution = institution;
    }

    public String getFieldOfStudy() {
        return fieldOfStudy;
    }

    public void setFieldOfStudy(String fieldOfStudy) {
        this.fieldOfStudy = fieldOfStudy;
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

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }
}
