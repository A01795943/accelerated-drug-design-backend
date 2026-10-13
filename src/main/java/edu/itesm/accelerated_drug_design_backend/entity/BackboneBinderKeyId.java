package edu.itesm.accelerated_drug_design_backend.entity;

import java.io.Serializable;
import java.util.Objects;

public class BackboneBinderKeyId implements Serializable {

	private Long backboneId;
	private String binderKey;

	public BackboneBinderKeyId() {
	}

	public BackboneBinderKeyId(Long backboneId, String binderKey) {
		this.backboneId = backboneId;
		this.binderKey = binderKey;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof BackboneBinderKeyId that)) {
			return false;
		}
		return Objects.equals(backboneId, that.backboneId) && Objects.equals(binderKey, that.binderKey);
	}

	@Override
	public int hashCode() {
		return Objects.hash(backboneId, binderKey);
	}
}
