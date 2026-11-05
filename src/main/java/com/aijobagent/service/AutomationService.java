package com.aijobagent.service;

import com.aijobagent.dto.automation.AutomationRunDto;
import com.aijobagent.dto.automation.AutomationStatusDto;
import com.aijobagent.dto.automation.PortalLinksConfigResponse;

import java.util.List;

public interface AutomationService {

    AutomationStatusDto getStatus();

    AutomationStatusDto start();

    AutomationStatusDto pause();

    AutomationStatusDto stop();

    List<AutomationRunDto> getRecentRuns();

    PortalLinksConfigResponse getPortalLinks();

    PortalLinksConfigResponse savePortalLinks(List<String> portalUrls);

    void runIfDue();
}
