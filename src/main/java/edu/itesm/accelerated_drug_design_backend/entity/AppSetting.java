package edu.itesm.accelerated_drug_design_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "app_settings")
public class AppSetting {

	@Id
	@Column(name = "setting_key", length = 100)
	private String settingKey;

	@Column(name = "setting_value", nullable = false, length = 255)
	private String settingValue;

	@Column(length = 500)
	private String description;

	@Column(name = "updated_at")
	private Instant updatedAt;

	@Column(name = "updated_by", length = 100)
	private String updatedBy;

	public String getSettingKey() {
		return settingKey;
	}

	public void setSettingKey(String settingKey) {
		this.settingKey = settingKey;
	}

	public String getSettingValue() {
		return settingValue;
	}

	public void setSettingValue(String settingValue) {
		this.settingValue = settingValue;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

	public String getUpdatedBy() {
		return updatedBy;
	}

	public void setUpdatedBy(String updatedBy) {
		this.updatedBy = updatedBy;
	}
}
