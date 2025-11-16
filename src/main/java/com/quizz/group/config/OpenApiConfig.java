package com.quizz.group.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI configuration for Swagger documentation.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Group Service API",
                version = "1.0.0",
                description = "REST API for managing groups, memberships, and invitations in the Quiz Play System",
                contact = @Contact(
                        name = "Quiz Play System Team",
                        email = "support@quizplay.com"
                )
        ),
        servers = {
                @Server(url = "http://localhost:8083", description = "Local Development Server"),
                @Server(url = "http://localhost:8080", description = "API Gateway")
        }
)
@SecurityScheme(
        name = "JWT Cookie Authentication",
        type = SecuritySchemeType.APIKEY,
        in = io.swagger.v3.oas.annotations.enums.SecuritySchemeIn.COOKIE,
        paramName = "jwt"
)
public class OpenApiConfig {
}
