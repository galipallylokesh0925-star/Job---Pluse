package com.aijobagent.service;

import com.aijobagent.dto.applicationprep.ApplicationPreparationDto;
import com.aijobagent.dto.applicationprep.ProfileReviewDto;
import com.aijobagent.dto.applicationprep.TruthfulnessValidationDto;

public interface ApplicationPreparationService {

    ProfileReviewDto reviewActiveProfile();

    ApplicationPreparationDto prepareForJob(String jobId);

    TruthfulnessValidationDto validateCoverLetter(String jobId, String coverLetter);
}
