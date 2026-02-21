package edu.itesm.accelerated_drug_design_backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "backbones")
public class Backbone {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "project_id", nullable = false)
	@JsonIgnore
	private Project project;

	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "run_id", length = 64)
	private String runID;

	@Column(name = "status", length = 32)
	private String status;

	@Column(name = "error", columnDefinition = "TEXT")
	private String error;

	@Column(columnDefinition = "TEXT")
	private String contigs;

	@Column(columnDefinition = "TEXT")
	private String hotspots;

	@Column(name = "chains_to_remove", columnDefinition = "TEXT")
	private String chainsToRemove;

	@Column(name = "iterations")
	private Integer iterations;

	@Column(columnDefinition = "LONGTEXT")
	private String structure;

	@PrePersist
	void prePersist() {
		// Placeholder; actual BB_id set in postPersist
		if (this.name == null) {
			this.name = "BB_PENDING";
		}
	}

	@PostPersist
	void postPersist() {
		if ("BB_PENDING".equals(this.name)) {
			this.name = "BB_" + this.id;
		}
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Project getProject() {
		return project;
	}

	public void setProject(Project project) {
		this.project = project;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getRunID() {
		return runID;
	}

	public void setRunID(String runID) {
		this.runID = runID;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getError() {
		return error;
	}

	public void setError(String error) {
		this.error = error;
	}

	public String getContigs() {
		return contigs;
	}

	public void setContigs(String contigs) {
		this.contigs = contigs;
	}

	public String getHotspots() {
		return hotspots;
	}

	public void setHotspots(String hotspots) {
		this.hotspots = hotspots;
	}

	public String getChainsToRemove() {
		return chainsToRemove;
	}

	public void setChainsToRemove(String chainsToRemove) {
		this.chainsToRemove = chainsToRemove;
	}

	public Integer getIterations() {
		return iterations;
	}

	public void setIterations(Integer iterations) {
		this.iterations = iterations;
	}

	public String getStructure() {
		return structure;
	}

	public void setStructure(String structure) {
		this.structure = structure;
	}
}
