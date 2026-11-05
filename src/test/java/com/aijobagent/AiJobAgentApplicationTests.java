package com.aijobagent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Spring Boot Integration Test.
 *
 * Verifies that the Spring application context boots up successfully with all configured beans.
 */
@SpringBootTest
class AiJobAgentApplicationTests {

    @Test
    @DisplayName("Application Context Loads Successfully")
    void contextLoads() {
        assertDoesNotThrow(() -> {
            // If the Spring ApplicationContext has any misconfigured beans or missing properties,
            // this test will fail during context initialization.
        });
    }
}
