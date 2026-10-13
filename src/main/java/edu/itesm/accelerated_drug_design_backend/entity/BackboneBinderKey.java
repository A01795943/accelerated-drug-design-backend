package edu.itesm.accelerated_drug_design_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "backbone_binder_keys")
@IdClass(BackboneBinderKeyId.class)
public class BackboneBinderKey {

	@Id
	@Column(name = "backbone_id")
	private Long backboneId;

	@Id
	@Column(name = "binder_key", length = 64)
	private String binderKey;

	@Column(name = "minibatch_id")
	private Long minibatchId;

	public Long getBackboneId() {
		return backboneId;
	}

	public void setBackboneId(Long backboneId) {
		this.backboneId = backboneId;
	}

	public String getBinderKey() {
		return binderKey;
	}

	public void setBinderKey(String binderKey) {
		this.binderKey = binderKey;
	}

	public Long getMinibatchId() {
		return minibatchId;
	}

	public void setMinibatchId(Long minibatchId) {
		this.minibatchId = minibatchId;
	}
}
