package com.aijobagent.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "automation_state")
public class AutomationState {

    public static final String ACTIVE_STATE_ID = "active";

    @Id
    private String id = ACTIVE_STATE_ID;

    private AutomationStatus status = AutomationStatus.STOPPED;
    private Instant startedAt;
    private Instant lastRunAt;
    private Instant nextRunAt;
    private String lastMessage;
    private int lastNewJobs;
    private List<String> portalUrls = new ArrayList<>();

    @LastModifiedDate
    private Instant updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public AutomationStatus getStatus() {
        return status;
    }

    public void setStatus(AutomationStatus status) {
        this.status = status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getLastRunAt() {
        return lastRunAt;
    }

    public void setLastRunAt(Instant lastRunAt) {
        this.lastRunAt = lastRunAt;
    }

    public Instant getNextRunAt() {
        return nextRunAt;
    }

    public void setNextRunAt(Instant nextRunAt) {
        this.nextRunAt = nextRunAt;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    public int getLastNewJobs() {
        return lastNewJobs;
    }

    public void setLastNewJobs(int lastNewJobs) {
        this.lastNewJobs = lastNewJobs;
    }

    public List<String> getPortalUrls() {
        return portalUrls == null ? List.of() : List.copyOf(portalUrls);
    }

    public void setPortalUrls(List<String> portalUrls) {
        this.portalUrls = portalUrls == null ? new ArrayList<>() : new ArrayList<>(portalUrls);
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
