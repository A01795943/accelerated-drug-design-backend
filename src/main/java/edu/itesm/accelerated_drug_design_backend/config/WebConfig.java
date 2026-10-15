package edu.itesm.accelerated_drug_design_backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@EnableWebMvc
public class WebConfig {

	/**
	 * Client for files.rcsb.org (project creation). Unqualified {@link RestTemplate}
	 * injection keeps resolving to this bean; the Data API uses {@code rcsbDataRestTemplate}.
	 */
	@Bean
	@Primary
	public RestTemplate restTemplate(
			@Value("${app.core.connect-timeout-ms:10000}") int connectTimeoutMs,
			@Value("${app.core.read-timeout-ms:120000}") int readTimeoutMs) {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(connectTimeoutMs);
		factory.setReadTimeout(readTimeoutMs);
		return new RestTemplate(factory);
	}

	@Bean("rcsbDataRestTemplate")
	public RestTemplate rcsbDataRestTemplate(
			@Value("${app.rcsb.data.connect-timeout-ms:2000}") int connectTimeoutMs,
			@Value("${app.rcsb.data.read-timeout-ms:4000}") int readTimeoutMs) {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(connectTimeoutMs);
		factory.setReadTimeout(readTimeoutMs);
		return new RestTemplate(factory);
	}

	/** Orígenes CORS permitidos (separados por coma). Por defecto: localhost y 127.0.0.1:4200. En docker usar CORS_ORIGINS. */
	@Value("${app.cors.allowed-origins:http://localhost:4200,http://127.0.0.1:4200}")
	private String allowedOriginsConfig;

	@Bean
	public FilterRegistrationBean<CorsFilter> corsFilter() {
		List<String> origins = Arrays.stream(allowedOriginsConfig.split(","))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.collect(Collectors.toList());
		if (origins.isEmpty()) {
			origins = List.of("http://localhost:4200", "http://127.0.0.1:4200");
		}

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowCredentials(true);
		config.setAllowedOrigins(origins);
		config.setAllowedHeaders(Arrays.asList(
				HttpHeaders.AUTHORIZATION,
				HttpHeaders.CONTENT_TYPE,
				HttpHeaders.ACCEPT
		));
		config.setAllowedMethods(Arrays.asList(
				HttpMethod.GET.name(),
				HttpMethod.POST.name(),
				HttpMethod.PUT.name(),
				HttpMethod.PATCH.name(),
				HttpMethod.DELETE.name(),
				HttpMethod.OPTIONS.name()
		));
		config.setMaxAge(3600L);
		source.registerCorsConfiguration("/**", config);

		FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
		bean.setOrder(-102);
		return bean;
	}
}
