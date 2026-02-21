package edu.itesm.accelerated_drug_design_backend.dto;

import edu.itesm.accelerated_drug_design_backend.entity.GenerationJobRecord;

import java.util.List;

/**
 * Paginated response for generation job detail records.
 * When job is COMPLETED, totalRecords and totalBatches allow the client to know
 * how many records exist and how many batch calls to make.
 */
public class RecordsPageResponse {

	private List<GenerationJobRecord> records;
	private long totalRecords;
	private int totalBatches;

	public RecordsPageResponse() {
	}

	public RecordsPageResponse(List<GenerationJobRecord> records, long totalRecords, int totalBatches) {
		this.records = records;
		this.totalRecords = totalRecords;
		this.totalBatches = totalBatches;
	}

	public List<GenerationJobRecord> getRecords() {
		return records;
	}

	public void setRecords(List<GenerationJobRecord> records) {
		this.records = records;
	}

	public long getTotalRecords() {
		return totalRecords;
	}

	public void setTotalRecords(long totalRecords) {
		this.totalRecords = totalRecords;
	}

	public int getTotalBatches() {
		return totalBatches;
	}

	public void setTotalBatches(int totalBatches) {
		this.totalBatches = totalBatches;
	}
}
