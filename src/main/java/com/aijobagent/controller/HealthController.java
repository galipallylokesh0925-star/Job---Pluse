package com.aijobagent.controller;

import com.aijobagent.dto.HealthResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health Controller providing diagnostic health check endpoints for the application and database.
 *
 * Annotations explained:
 * - @RestController: Marks this class as a Spring MVC controller where every method returns a domain object
 *   serialized directly into the HTTP response body as JSON.
 * - @RequestMapping: Configures the base URI path for all handler methods in this controller.
 * - @Tag: Grouping metadata for OpenAPI / Swagger documentation.
 */
@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health & Diagnostics", description = "Endpoints to verify application and database readiness")
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final MongoTemplate mongoTemplate;
    private final String appName;
    private final String appVersion;

    /**
     * Constructor injection: The recommended Spring best practice for injecting dependencies.
     * Guarantees immutability and simplifies unit testing.
     */
    public HealthController(
            MongoTemplate mongoTemplate,
            @Value("${spring.application.name:ai-job-agent}") String appName,
            @Value("${app.version:1.0.0}") String appVersion) {
        this.mongoTemplate = mongoTemplate;
        this.appName = appName;
        this.appVersion = appVersion;
    }

    /**
     * Performs a comprehensive health verification of the Spring Boot application and MongoDB.
     *
     * @return 200 OK with HealthResponse if all systems are UP, or 503 SERVICE_UNAVAILABLE if DB is down.
     */
    @GetMapping
    @Operation(summary = "Check System Health", description = "Checks Spring Boot runtime and verifies active MongoDB connection via ping")
    public ResponseEntity<HealthResponse> checkHealth() {
        String dbStatus;
        boolean dbHealthy;

        try {
            // Actively ping MongoDB database with { ping: 1 } command
            Document pingResult = mongoTemplate.executeCommand("{ ping: 1 }");
            Number ok = pingResult.get("ok", Number.class);
            if (ok != null && ok.doubleValue() == 1.0) {
                dbStatus = "UP (MongoDB connected)";
                dbHealthy = true;
            } else {
                dbStatus = "DEGRADED (Unexpected ping result: " + pingResult.toJson() + ")";
                dbHealthy = false;
            }
        } catch (Exception ex) {
            log.error("MongoDB health check failed: {}", ex.getMessage());
            dbStatus = "DOWN (Cannot reach MongoDB: " + ex.getMessage() + ")";
            dbHealthy = false;
        }

        Map<String, String> systemInfo = new LinkedHashMap<>();
        systemInfo.put("javaVersion", System.getProperty("java.version"));
        systemInfo.put("javaVendor", System.getProperty("java.vendor"));
        systemInfo.put("osName", System.getProperty("os.name"));
        systemInfo.put("osArch", System.getProperty("os.arch"));
        systemInfo.put("availableProcessors", String.valueOf(Runtime.getRuntime().availableProcessors()));
        systemInfo.put("maxMemoryMb", String.valueOf(Runtime.getRuntime().maxMemory() / (1024 * 1024)));

        String overallStatus = dbHealthy ? "UP" : "DEGRADED";
        String message = dbHealthy
                ? "AI Job Application Agent is healthy and operational."
                : "Application is running but MongoDB connection is currently unavailable.";

        HealthResponse response = new HealthResponse(
                overallStatus,
                message,
                Instant.now(),
                appName,
                appVersion,
                dbStatus,
                systemInfo
        );

        HttpStatus httpStatus = dbHealthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(httpStatus).body(response);
    }
}
