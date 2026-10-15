package edu.itesm.accelerated_drug_design_backend.dto;

import java.util.List;

public class GenerateSuggestionsResponse {

	private final int requestedCount;
	private final int generatedCount;
	private final List<GenerateResult> suggestions;

	public GenerateSuggestionsResponse(int requestedCount, int generatedCount, List<GenerateResult> suggestions) {
		this.requestedCount = requestedCount;
		this.generatedCount = generatedCount;
		this.suggestions = suggestions;
	}

	public int getRequestedCount() {
		return requestedCount;
	}

	public int getGeneratedCount() {
		return generatedCount;
	}

	public List<GenerateResult> getSuggestions() {
		return suggestions;
	}
}
