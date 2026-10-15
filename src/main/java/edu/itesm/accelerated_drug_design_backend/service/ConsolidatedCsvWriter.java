package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.dto.ConsolidatedCsvRow;

import java.io.IOException;

/**
 * RFC 4180 writer for the consolidated generation CSV.
 * Nulls become empty fields. Text that contains a comma, quote, CR, LF, space or '/' is quoted
 * so values such as hotspots ({@code A171,A174}) and contigs ({@code 20-35/0 A1-100}) stay in one column.
 */
public final class ConsolidatedCsvWriter {

	public static final String HEADER =
			"backbone_id,contigs,hotspots,chains_to_remove,backbone_iterations,temperature,ptm,i_ptm,pae,i_pae,plddt,rmsd,mpnn,seq";

	private ConsolidatedCsvWriter() {
	}

	public static void writeHeader(Appendable out) throws IOException {
		out.append(HEADER);
		out.append('\n');
	}

	public static void writeRow(Appendable out, ConsolidatedCsvRow row) throws IOException {
		append(out, row.backboneId() == null ? "" : Long.toString(row.backboneId()));
		out.append(',');
		append(out, text(row.contigs()));
		out.append(',');
		append(out, text(row.hotspots()));
		out.append(',');
		append(out, text(row.chainsToRemove()));
		out.append(',');
		append(out, row.backboneIterations() == null ? "" : Integer.toString(row.backboneIterations()));
		out.append(',');
		append(out, number(row.temperature()));
		out.append(',');
		append(out, number(row.ptm()));
		out.append(',');
		append(out, number(row.iPtm()));
		out.append(',');
		append(out, text(row.pae()));
		out.append(',');
		append(out, text(row.iPae()));
		out.append(',');
		append(out, number(row.plddt()));
		out.append(',');
		append(out, number(row.rmsd()));
		out.append(',');
		append(out, text(row.mpnn()));
		out.append(',');
		append(out, text(row.seq()));
		out.append('\n');
	}

	/** Quotes when required by RFC 4180, and also when the value contains a space or '/'. */
	static String text(String value) {
		if (value == null) {
			return "";
		}
		boolean quote = false;
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c == ',' || c == '"' || c == '\n' || c == '\r' || c == ' ' || c == '/') {
				quote = true;
				break;
			}
		}
		if (!quote) {
			return value;
		}
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}

	private static String number(Double value) {
		return value == null ? "" : Double.toString(value);
	}

	private static void append(Appendable out, String value) throws IOException {
		out.append(value);
	}
}
