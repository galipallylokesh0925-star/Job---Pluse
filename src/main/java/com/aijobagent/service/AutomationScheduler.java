package com.aijobagent.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AutomationScheduler {

    private final AutomationService automationService;

    public AutomationScheduler(AutomationService automationService) {
        this.automationService = automationService;
    }

    @Scheduled(fixedDelayString = "${app.automation.check-interval-ms:60000}")
    public void runScheduledCycleWhenDue() {
        automationService.runIfDue();
    }
}
