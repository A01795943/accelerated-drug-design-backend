package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.csv.CsvRecords;
import edu.itesm.accelerated_drug_design_backend.dto.ConsolidatedCsvRow;
import edu.itesm.accelerated_drug_design_backend.repository.GenerationJobRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsolidatedCsvWriterTest {

	@Test
	void headerAndQuoting_roundTripWithCommasSpacesAndNewlines() throws Exception {
		StringBuilder out = new StringBuilder();
		ConsolidatedCsvWriter.writeHeader(out);
		ConsolidatedCsvWriter.writeRow(out, new ConsolidatedCsvRow(
				7L,
				"20-35/0 A1-100",
				"A171,A174,A176",
				null,
				3,
				0.2,
				0.8,
				null,
				"0.1,0.2",
				"plain",
				90.0,
				1.25,
				"mpnn text",
				"AC\"DE\nF",
				99L,
				0));

		String csv = out.toString();
		assertTrue(csv.startsWith(ConsolidatedCsvWriter.HEADER + "\n"));
		assertTrue(csv.contains("\"A171,A174,A176\""));
		assertTrue(csv.contains("\"20-35/0 A1-100\""));

		List<List<String>> rows = CsvRecords.parse(csv);
		assertEquals(2, rows.size());
		assertEquals(List.of(
				"backbone_id", "contigs", "hotspots", "chains_to_remove", "backbone_iterations",
				"temperature", "ptm", "i_ptm", "pae", "i_pae", "plddt", "rmsd", "mpnn", "seq"),
				rows.get(0));
		List<String> data = rows.get(1);
		assertEquals(14, data.size());
		assertEquals("7", data.get(0));
		assertEquals("20-35/0 A1-100", data.get(1));
		assertEquals("A171,A174,A176", data.get(2));
		assertEquals("", data.get(3));
		assertEquals("3", data.get(4));
		assertEquals("0.2", data.get(5));
		assertEquals("0.8", data.get(6));
		assertEquals("", data.get(7));
		assertEquals("0.1,0.2", data.get(8));
		assertEquals("plain", data.get(9));
		assertEquals("90.0", data.get(10));
		assertEquals("1.25", data.get(11));
		assertEquals("mpnn text", data.get(12));
		assertEquals("AC\"DE\nF", data.get(13));
	}

	@Test
	void consolidatedQuery_doesNotReadLongTextColumns() throws Exception {
		Method method = GenerationJobRecordRepository.class.getMethod(
				"findConsolidatedBatch",
				Long.class,
				String.class,
				Collection.class,
				long.class,
				int.class,
				Pageable.class);
		String jpql = method.getAnnotation(Query.class).value().toLowerCase();
		assertFalse(jpql.contains("pdb"));
		assertFalse(jpql.contains("fasta"));
		assertFalse(jpql.contains("bestpdb"));
		assertFalse(jpql.contains("outputcsv"));
		assertFalse(jpql.contains("structure"));
		assertTrue(jpql.contains("b.hotspots"));
		assertTrue(jpql.contains("r.seq"));
	}
}
