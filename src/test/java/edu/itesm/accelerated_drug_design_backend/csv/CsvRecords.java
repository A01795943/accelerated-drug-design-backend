package edu.itesm.accelerated_drug_design_backend.csv;

import java.util.ArrayList;
import java.util.List;

/** Minimal RFC 4180 reader for tests (quoted commas, escaped quotes, multiline fields). */
public final class CsvRecords {

	private CsvRecords() {
	}

	public static List<List<String>> parse(String csv) {
		List<List<String>> rows = new ArrayList<>();
		List<String> row = new ArrayList<>();
		StringBuilder field = new StringBuilder();
		boolean inQuotes = false;
		for (int i = 0; i < csv.length(); i++) {
			char c = csv.charAt(i);
			if (inQuotes) {
				if (c == '"') {
					if (i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
						field.append('"');
						i++;
					} else {
						inQuotes = false;
					}
				} else {
					field.append(c);
				}
			} else if (c == '"') {
				inQuotes = true;
			} else if (c == ',') {
				row.add(field.toString());
				field.setLength(0);
			} else if (c == '\n') {
				row.add(field.toString());
				field.setLength(0);
				if (!(row.size() == 1 && row.get(0).isEmpty())) {
					rows.add(row);
				}
				row = new ArrayList<>();
			} else if (c != '\r') {
				field.append(c);
			}
		}
		if (field.length() > 0 || !row.isEmpty()) {
			row.add(field.toString());
			rows.add(row);
		}
		return rows;
	}
}
