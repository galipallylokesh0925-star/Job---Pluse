package com.aijobagent.service;

import com.aijobagent.dto.applicationprep.ApplicationSubmissionDto;
import com.aijobagent.dto.applicationprep.ApplicationSubmissionRequest;

public interface ApplicationSubmissionService {

    ApplicationSubmissionDto submit(String jobId, ApplicationSubmissionRequest request);
}
