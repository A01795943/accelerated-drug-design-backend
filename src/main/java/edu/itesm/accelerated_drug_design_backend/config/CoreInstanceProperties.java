package edu.itesm.accelerated_drug_design_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Optional extra cores from YAML. The Flyway V4 seed already inserts {@code app.core.base-url}
 * as {@code core-principal} when migrations run; this list adds further instances.
 */
@ConfigurationProperties(prefix = "app.core")
public class CoreInstanceProperties {

	private String baseUrl = "http://localhost:8000";
	private List<DeclaredInstance> instances = new ArrayList<>();

	public String getBaseUrl() {
		return baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public List<DeclaredInstance> getInstances() {
		return instances;
	}

	public void setInstances(List<DeclaredInstance> instances) {
		this.instances = instances != null ? instances : new ArrayList<>();
	}

	public static class DeclaredInstance {
		private String name;
		private String baseUrl;
		private Integer maxConcurrentRuns = 1;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getBaseUrl() {
			return baseUrl;
		}

		public void setBaseUrl(String baseUrl) {
			this.baseUrl = baseUrl;
		}

		public Integer getMaxConcurrentRuns() {
			return maxConcurrentRuns;
		}

		public void setMaxConcurrentRuns(Integer maxConcurrentRuns) {
			this.maxConcurrentRuns = maxConcurrentRuns;
		}
	}
}
