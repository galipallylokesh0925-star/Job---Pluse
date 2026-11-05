package com.aijobagent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/**
 * MongoDB Configuration class.
 *
 * Annotations explained:
 * - @Configuration: Indicates that this class provides Spring Bean configurations.
 * - @EnableMongoAuditing: Activates auditing annotations such as @CreatedDate and @LastModifiedDate
 *   so timestamps are automatically populated when documents are saved to MongoDB.
 */
@Configuration
@EnableMongoAuditing
public class MongoConfig {

    private static final Logger log = LoggerFactory.getLogger(MongoConfig.class);

    public MongoConfig() {
        log.info("MongoDB Auditing initialized successfully.");
    }
}
