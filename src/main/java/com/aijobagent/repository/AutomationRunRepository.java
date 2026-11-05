package com.aijobagent.repository;

import com.aijobagent.model.AutomationRun;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AutomationRunRepository extends MongoRepository<AutomationRun, String> {

    List<AutomationRun> findTop20ByOrderByStartedAtDesc();
}
