package edu.itesm.accelerated_drug_design_backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
public class WebConfig {

	@Bean
	public RestTemplate restTemplate() {
		return new RestTemplate();
	}

	/** Orígenes CORS permitidos (separados por coma). Por defecto: localhost y 127.0.0.1:4200. En docker usar CORS_ORIGINS. */
	@Value("${app.cors.allowed-origins:http://localhost:4200,http://127.0.0.1:4200}")
	private String allowedOriginsConfig;

	@Bean
	public CorsFilter corsFilter() {
		List<String> origins = Arrays.stream(allowedOriginsConfig.split(","))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.collect(Collectors.toList());
		if (origins.isEmpty()) {
			origins = List.of("http://localhost:4200", "http://127.0.0.1:4200");
		}
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(origins);
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("*"));
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", config);
		return new CorsFilter(source);
	}
}
