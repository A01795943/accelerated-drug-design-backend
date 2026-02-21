package edu.itesm.accelerated_drug_design_backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "generation_jobs_records")
@IdClass(GenerationJobRecordId.class)
public class GenerationJobRecord {

	@Id
	@Column(name = "generation_job_id")
	private Long generationJobId;

	@Id
	@Column(name = "n")
	private Integer n;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "generation_job_id", insertable = false, updatable = false)
	@JsonIgnore
	private GenerationJob generationJob;

	@Column(columnDefinition = "LONGTEXT")
	private String pdb;

	private String mpnn;

	private Double plddt;

	private Double ptm;

	@Column(name = "i_ptm")
	private Double iPtm;

	@Column(columnDefinition = "TEXT")
	private String pae;

	@Column(name = "i_pae", columnDefinition = "TEXT")
	private String iPae;

	private Double rmsd;

	@Column(columnDefinition = "TEXT")
	private String seq;

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

	public GenerationJob getGenerationJob() {
		return generationJob;
	}

	public void setGenerationJob(GenerationJob generationJob) {
		this.generationJob = generationJob;
	}

	public String getPdb() {
		return pdb;
	}

	public void setPdb(String pdb) {
		this.pdb = pdb;
	}

	public String getMpnn() {
		return mpnn;
	}

	public void setMpnn(String mpnn) {
		this.mpnn = mpnn;
	}

	public Double getPlddt() {
		return plddt;
	}

	public void setPlddt(Double plddt) {
		this.plddt = plddt;
	}

	public Double getPtm() {
		return ptm;
	}

	public void setPtm(Double ptm) {
		this.ptm = ptm;
	}

	public Double getIPtm() {
		return iPtm;
	}

	public void setIPtm(Double iPtm) {
		this.iPtm = iPtm;
	}

	public String getPae() {
		return pae;
	}

	public void setPae(String pae) {
		this.pae = pae;
	}

	public String getIPae() {
		return iPae;
	}

	public void setIPae(String iPae) {
		this.iPae = iPae;
	}

	public Double getRmsd() {
		return rmsd;
	}

	public void setRmsd(Double rmsd) {
		this.rmsd = rmsd;
	}

	public String getSeq() {
		return seq;
	}

	public void setSeq(String seq) {
		this.seq = seq;
	}
}
