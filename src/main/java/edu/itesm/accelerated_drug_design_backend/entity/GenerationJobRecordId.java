package edu.itesm.accelerated_drug_design_backend.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key for GenerationJobRecord (generation_job_id, n).
 */
public class GenerationJobRecordId implements Serializable {

	private static final long serialVersionUID = 1L;

	private Long generationJobId;
	private Integer n;

	public GenerationJobRecordId() {
	}

	public GenerationJobRecordId(Long generationJobId, Integer n) {
		this.generationJobId = generationJobId;
		this.n = n;
	}

	public Long getGenerationJobId() {
		return generationJobId;
	}

	public void setGenerationJobId(Long generationJobId) {
		this.generationJobId = generationJobId;
	}

	public Integer getN() {
		return n;
	}

	public void setN(Integer n) {
		this.n = n;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		GenerationJobRecordId that = (GenerationJobRecordId) o;
		return Objects.equals(generationJobId, that.generationJobId) && Objects.equals(n, that.n);
	}

	@Override
	public int hashCode() {
		return Objects.hash(generationJobId, n);
	}
}
