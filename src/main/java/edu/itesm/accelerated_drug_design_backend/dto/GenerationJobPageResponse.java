package edu.itesm.accelerated_drug_design_backend.dto;

import java.util.List;

/** One page of the synthetic-data generation job list. */
public class GenerationJobPageResponse {

	private List<GenerationJobListItem> jobs;
	private long totalElements;
	private int totalPages;
	private int page;
	private int size;
	/** COMPLETED jobs matching the same backbone filter (selectable / consolidatable). */
	private long consolidatableCount;

	public GenerationJobPageResponse() {
	}

	public GenerationJobPageResponse(List<GenerationJobListItem> jobs, long totalElements, int totalPages,
			int page, int size, long consolidatableCount) {
		this.jobs = jobs;
		this.totalElements = totalElements;
		this.totalPages = totalPages;
		this.page = page;
		this.size = size;
		this.consolidatableCount = consolidatableCount;
	}

	public List<GenerationJobListItem> getJobs() {
		return jobs;
	}

	public void setJobs(List<GenerationJobListItem> jobs) {
		this.jobs = jobs;
	}

	public long getTotalElements() {
		return totalElements;
	}

	public void setTotalElements(long totalElements) {
		this.totalElements = totalElements;
	}

	public int getTotalPages() {
		return totalPages;
	}

	public void setTotalPages(int totalPages) {
		this.totalPages = totalPages;
	}

	public int getPage() {
		return page;
	}

	public void setPage(int page) {
		this.page = page;
	}

	public int getSize() {
		return size;
	}

	public void setSize(int size) {
		this.size = size;
	}

	public long getConsolidatableCount() {
		return consolidatableCount;
	}

	public void setConsolidatableCount(long consolidatableCount) {
		this.consolidatableCount = consolidatableCount;
	}
}
