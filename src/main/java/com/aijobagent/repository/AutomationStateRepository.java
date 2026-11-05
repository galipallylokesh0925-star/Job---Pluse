package com.aijobagent.repository;

import com.aijobagent.model.AutomationState;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AutomationStateRepository extends MongoRepository<AutomationState, String> {
}
