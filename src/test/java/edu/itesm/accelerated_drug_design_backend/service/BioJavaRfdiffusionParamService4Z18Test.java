package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.dto.GenerateResult;
import edu.itesm.accelerated_drug_design_backend.dto.RfdiffusionParamOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BioJavaRfdiffusionParamService4Z18Test {

	private BioJavaRfdiffusionParamService service;
	private String pdbText;

	@BeforeEach
	void setUp() throws Exception {
		service = new BioJavaRfdiffusionParamService(null, null);
		try (InputStream in = getClass().getResourceAsStream("/4Z18.pdb")) {
			assertNotNull(in, "Missing test resource /4Z18.pdb");
			pdbText = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	@Test
	void generateTargetSuggestionsFromPdb_4Z18_producesCoherentLocalSuggestionsWithoutMissingResidue18() {
		RfdiffusionParamOptions options = RfdiffusionParamOptions.forLengthRange(12, 15, RfdiffusionParamOptions.DEFAULT_MAX_HOTSPOTS);
		List<GenerateResult> suggestions = service.generateTargetSuggestionsFromPdb(pdbText, 10, options);

		assertFalse(suggestions.isEmpty(), "expected at least one suggestion");

		for (GenerateResult suggestion : suggestions) {
			String contig = suggestion.getContig();
			String hotspots = suggestion.getHotspots();

			assertFalse(contig.matches(".*\\/0 A18-.*"), "contig must not start at missing residue 18: " + contig);
			assertTrue(BioJavaRfdiffusionParamServiceContigTest.parseContigStartSeq(contig) >= 19,
					"contig start must be >= 19: " + contig);

			int minHotspot = Integer.MAX_VALUE;
			int maxHotspot = Integer.MIN_VALUE;
			if (!hotspots.isBlank()) {
				for (String token : hotspots.split(",")) {
					int seq = BioJavaRfdiffusionParamServiceContigTest.parseHotspotSeq(token.trim());
					minHotspot = Math.min(minHotspot, seq);
					maxHotspot = Math.max(maxHotspot, seq);
					assertTrue(seq >= 19, "hotspot seq must be >= 19: " + token);
				}
				assertTrue(maxHotspot - minHotspot <= 30,
						"hotspot span too wide (incoherent patch): " + hotspots);
			}

			int contigStart = BioJavaRfdiffusionParamServiceContigTest.parseContigStartSeq(contig);
			int contigEnd = BioJavaRfdiffusionParamServiceContigTest.parseContigEndSeq(contig);
			if (!hotspots.isBlank()) {
				assertTrue(minHotspot >= contigStart, "hotspots must fall inside contig start");
				assertTrue(maxHotspot <= contigEnd, "hotspots must fall inside contig end");
			}
		}
	}
}
