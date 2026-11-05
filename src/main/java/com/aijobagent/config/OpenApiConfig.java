package com.aijobagent.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 / Swagger Documentation Configuration.
 *
 * Configures the interactive API documentation available at /swagger-ui.html.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI Job Application Agent API")
                        .version("1.0.0")
                        .description("Production-grade RESTful APIs for AI Job Application Agent. " +
                                "Provides multi-source job discovery, resume extraction, AI matching, " +
                                "and authorized application automation.")
                        .contact(new Contact()
                                .name("AI Job Agent Support")
                                .email("developer@aijobagent.local"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")));
    }
}
