package edu.itesm.accelerated_drug_design_backend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "projects")
public class Project {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Column(columnDefinition = "LONGTEXT")
	private String target;

	@Column(columnDefinition = "LONGTEXT")
	private String complex;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column(name = "target_pdb_id", length = 16)
	private String targetPdbId;

	@Column(name = "ec_number", length = 16)
	private String ecNumber;

	@Column(name = "ec_lineage", length = 255)
	private String ecLineage;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getTarget() {
		return target;
	}

	public void setTarget(String target) {
		this.target = target;
	}

	public String getComplex() {
		return complex;
	}

	public void setComplex(String complex) {
		this.complex = complex;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getTargetPdbId() {
		return targetPdbId;
	}

	public void setTargetPdbId(String targetPdbId) {
		this.targetPdbId = targetPdbId;
	}

	public String getEcNumber() {
		return ecNumber;
	}

	public void setEcNumber(String ecNumber) {
		this.ecNumber = ecNumber;
	}

	public String getEcLineage() {
		return ecLineage;
	}

	public void setEcLineage(String ecLineage) {
		this.ecLineage = ecLineage;
	}
}
