package edu.itesm.accelerated_drug_design_backend.dto;

import java.util.List;

/**
 * Paginated response for generation job detail records.
 * When job is COMPLETED, totalRecords and totalBatches allow the client to know
 * how many records exist and how many batch calls to make.
 * Records are list DTOs (no pdb); PDB por registro se obtiene con GET .../records/{n}/pdb.
 */
public class RecordsPageResponse {

	private List<GenerationJobRecordListDto> records;
	private long totalRecords;
	private int totalBatches;

	public RecordsPageResponse() {
	}

	public RecordsPageResponse(List<GenerationJobRecordListDto> records, long totalRecords, int totalBatches) {
		this.records = records;
		this.totalRecords = totalRecords;
		this.totalBatches = totalBatches;
	}

	public List<GenerationJobRecordListDto> getRecords() {
		return records;
	}

	public void setRecords(List<GenerationJobRecordListDto> records) {
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
