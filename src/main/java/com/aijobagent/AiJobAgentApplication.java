package com.aijobagent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Main Spring Boot Application entry point for AI Job Application Agent.
 *
 * Annotations explained:
 * - @SpringBootApplication: Combines @Configuration, @EnableAutoConfiguration, and @ComponentScan.
 * - @EnableScheduling: Activates Spring's scheduled background task execution capability.
 * - @EnableSpringDataWebSupport: Configures modern DTO-based serialization for Spring Data Page instances.
 */
@SpringBootApplication
@EnableScheduling
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class AiJobAgentApplication {

    private static final Logger log = LoggerFactory.getLogger(AiJobAgentApplication.class);
    private final Environment environment;

    public AiJobAgentApplication(Environment environment) {
        this.environment = environment;
    }

    public static void main(String[] args) {
        SpringApplication.run(AiJobAgentApplication.class, args);
    }

    /**
     * Event listener triggered once the Spring Application Context is fully initialized and ready.
     * Prints helpful local links for the developer to access the dashboard, Swagger UI, and health endpoint.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        String port = environment.getProperty("server.port", "8080");
        String hostAddress = "localhost";
        try {
            hostAddress = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException ignored) {
            // Fallback to localhost if host address resolution fails
        }

        log.info("""
                ----------------------------------------------------------------------
                🚀 AI Job Application Agent is up and running!
                ----------------------------------------------------------------------
                🌐 Web Dashboard:        http://{}:{}
                📄 Swagger / OpenAPI:    http://{}:{}/swagger-ui.html
                🩺 Health Check API:     http://{}:{}/api/v1/health
                📊 Actuator Metrics:     http://{}:{}/actuator/health
                🗄️  MongoDB URI:          {}
                ----------------------------------------------------------------------
                """, hostAddress, port, hostAddress, port, hostAddress, port, hostAddress, port,
                environment.getProperty("spring.data.mongodb.uri"));
    }
}
