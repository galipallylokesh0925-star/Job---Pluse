package com.aijobagent.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "automation_runs")
public class AutomationRun {

    @Id
    private String id;

    @Indexed
    private Instant startedAt;

    private Instant completedAt;
    private boolean success;
    private int newJobs;
    private int totalFetched;
    private int duplicateJobs;
    private int jobsEvaluated;
    private int strongMatches;
    private String message;
    private String emailStatus;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public int getNewJobs() {
        return newJobs;
    }

    public void setNewJobs(int newJobs) {
        this.newJobs = newJobs;
    }

    public int getTotalFetched() {
        return totalFetched;
    }

    public void setTotalFetched(int totalFetched) {
        this.totalFetched = totalFetched;
    }

    public int getDuplicateJobs() {
        return duplicateJobs;
    }

    public void setDuplicateJobs(int duplicateJobs) {
        this.duplicateJobs = duplicateJobs;
    }

    public int getJobsEvaluated() {
        return jobsEvaluated;
    }

    public void setJobsEvaluated(int jobsEvaluated) {
        this.jobsEvaluated = jobsEvaluated;
    }

    public int getStrongMatches() {
        return strongMatches;
    }

    public void setStrongMatches(int strongMatches) {
        this.strongMatches = strongMatches;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getEmailStatus() {
        return emailStatus;
    }

    public void setEmailStatus(String emailStatus) {
        this.emailStatus = emailStatus;
    }
}
