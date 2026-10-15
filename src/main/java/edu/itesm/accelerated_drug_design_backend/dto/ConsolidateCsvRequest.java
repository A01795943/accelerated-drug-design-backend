package edu.itesm.accelerated_drug_design_backend.dto;

import java.util.List;

/**
 * Consolidate either an explicit job id list, or every COMPLETED job matching the backbone filter.
 * {@code excludedJobIds} applies only when {@code selectAllFiltered} is true.
 */
public class ConsolidateCsvRequest {

	private List<Long> jobIds;
	private boolean selectAllFiltered;
	private List<Long> backboneIds;
	private List<Long> excludedJobIds;

	public List<Long> getJobIds() {
		return jobIds;
	}

	public void setJobIds(List<Long> jobIds) {
		this.jobIds = jobIds;
	}

	public boolean isSelectAllFiltered() {
		return selectAllFiltered;
	}

	public void setSelectAllFiltered(boolean selectAllFiltered) {
		this.selectAllFiltered = selectAllFiltered;
	}

	public List<Long> getBackboneIds() {
		return backboneIds;
	}

	public void setBackboneIds(List<Long> backboneIds) {
		this.backboneIds = backboneIds;
	}

	public List<Long> getExcludedJobIds() {
		return excludedJobIds;
	}

	public void setExcludedJobIds(List<Long> excludedJobIds) {
		this.excludedJobIds = excludedJobIds;
	}
}
