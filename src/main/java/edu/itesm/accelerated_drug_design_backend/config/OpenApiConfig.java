package edu.itesm.accelerated_drug_design_backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3 metadata, JWT security scheme, and optional server URL.
 * Only active when {@code app.api-docs.enabled} is true.
 */
@Configuration
@ConditionalOnProperty(name = "app.api-docs.enabled", havingValue = "true")
public class OpenApiConfig {

	@Value("${app.api-docs.title:Accelerated Drug Design API}")
	private String title;

	@Value("${app.api-docs.description:REST API for accelerated drug design: projects, backbones (RFdiffusion), and generation jobs (ProteinMPNN).}")
	private String description;

	@Value("${app.api-docs.version:0.0.1-SNAPSHOT}")
	private String version;

	@Value("${app.api-docs.server-url:}")
	private String serverUrl;

	@Bean
	public OpenAPI customOpenAPI() {
		OpenAPI openAPI = new OpenAPI()
				.info(new Info()
						.title(title)
						.description(description)
						.version(version)
						.contact(new Contact().name("API Support"))
						.license(new License().name("Proprietary")));

		if (serverUrl != null && !serverUrl.isBlank()) {
			openAPI.servers(List.of(new Server().url(serverUrl).description("API server")));
		}

		// JWT Bearer (Spring Security)
		final String securitySchemeName = "bearerAuth";
		openAPI
				.addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
				.components(new Components()
						.addSecuritySchemes(securitySchemeName,
								new SecurityScheme()
										.type(SecurityScheme.Type.HTTP)
										.scheme("bearer")
										.bearerFormat("JWT")
										.description("JWT token from POST /api/auth/login")));

		return openAPI;
	}
}
