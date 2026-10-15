package edu.itesm.accelerated_drug_design_backend.service;

import org.biojava.nbio.structure.*;
import org.biojava.nbio.structure.io.PDBFileReader;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BioJavaRfdiffusionParamServiceDuplicateChainTest {

	private static final String CORE_SEQUENCE =
			"MKTAYIAKQRQISFVKSHFSRQLEERLGLIEVQAPILSRVGDGTQDNLSGAEKAVQVKVKALPDAQFEVVHSLAKWKRQTLGQHDFSAGEGLYTHMKALRPDEDRLSPLHSVYVDQWDWERVMGDGERQFSTLKSTVEAIWAGIKATEAAVSEEFGLAPFLPDQIHFVHSQELLSRYPDLDAKGRERAIAKDLGVALSGETAEKRLQPR";

	@Test
	void seqSimilarity_positionalComparisonFailsWithNterminalOffset() {
		String longer = "MKT" + CORE_SEQUENCE;
		String shorter = CORE_SEQUENCE;

		double positional = BioJavaRfdiffusionParamService.seqSimilarityPositional(longer, shorter);

		assertTrue(positional < 0.98,
				"Positional comparison should miss biologically identical chains with N-terminal offset; got " + positional);
	}

	@Test
	void seqSimilarity_detectsBiologicallyIdenticalChainsWithOffset() {
		String longer = "MKT" + CORE_SEQUENCE;
		String shorter = CORE_SEQUENCE;

		double sim = BioJavaRfdiffusionParamService.seqSimilarity(longer, shorter);

		assertTrue(sim >= 0.98, "Offset-aware similarity should detect duplicate chains; got " + sim);
	}

	@Test
	void findDuplicateChains_groupsChainsWithResolvedRangeOffset() {
		Structure structure = buildHomomerStructure(
				new ChainSpec("A", CORE_SEQUENCE, 1),
				new ChainSpec("B", CORE_SEQUENCE, 4),
				new ChainSpec("C", "ACDEFGHIKLMNPQRSTVWYACDEFGHIKLMNPQRSTVWYACDEFGHIKLMNPQRSTVWYACDEFG", 1));

		List<Chain> proteinChains = structure.getChains().stream()
				.filter(c -> c.getAtomGroups().size() >= 30)
				.toList();

		Map<String, List<String>> dupGroups = BioJavaRfdiffusionParamService.findDuplicateChains(proteinChains, 0.98);

		List<String> groupWithA = dupGroups.values().stream()
				.filter(m -> m.contains("A"))
				.findFirst()
				.orElseThrow(() -> new AssertionError("No duplicate group for chain A: " + dupGroups));

		assertTrue(groupWithA.contains("B"), "Chain B should be grouped with A; group=" + groupWithA);
		assertFalse(groupWithA.contains("C"), "Unrelated chain C should not be in A's group");
	}

	@Test
	void computeChainsToRemove_keepsTargetAndPartnerRemovesOtherDuplicates() {
		Structure structure = buildHomomerStructure(
				new ChainSpec("A", CORE_SEQUENCE, 1),
				new ChainSpec("B", CORE_SEQUENCE, 1),
				new ChainSpec("C", CORE_SEQUENCE, 1),
				new ChainSpec("D", "ACDEFGHIKLMNPQRSTVWYACDEFGHIKLMNPQRSTVWYACDEFGHIKLMNPQRSTVWYACDEFG", 1));

		List<Chain> proteinChains = structure.getChains().stream()
				.filter(c -> c.getAtomGroups().size() >= 30)
				.toList();

		List<String> toRemove = BioJavaRfdiffusionParamService.computeChainsToRemove(
				proteinChains, "A", "D", 0.98);

		assertEquals(List.of("B", "C"), toRemove);
	}

	@Test
	void computeChainsToRemove_wiringUsesFullProteinChainList() {
		Structure structure = buildHomomerStructure(
				new ChainSpec("A", CORE_SEQUENCE, 1),
				new ChainSpec("B", CORE_SEQUENCE, 2),
				new ChainSpec("C", CORE_SEQUENCE, 3));

		List<Chain> proteinChains = structure.getChains().stream()
				.filter(c -> c.getAtomGroups().size() >= 30)
				.toList();

		assertEquals(3, proteinChains.size(), "All homomer chains must be visible to duplicate detection");

		List<String> toRemove = BioJavaRfdiffusionParamService.computeChainsToRemove(
				proteinChains, "A", null, 0.98);

		assertFalse(toRemove.isEmpty(), "chainsToRemove must not be empty when duplicate chains exist");
		assertTrue(toRemove.contains("B") || toRemove.contains("C"));
	}

	@Test
	void realPdb_1timHomodimer_marksDuplicateChainForRemoval() throws Exception {
		Structure structure = readPdbResource("/pdb/1TIM.pdb");
		List<Chain> proteinChains = structure.getChains().stream()
				.filter(c -> c.getAtomGroups().size() >= 30)
				.toList();

		assertEquals(2, proteinChains.size(), "1TIM asymmetric unit should expose chains A and B");

		List<String> toRemove = BioJavaRfdiffusionParamService.computeChainsToRemove(
				proteinChains, "A", "B", 0.98);

		assertEquals(List.of(), toRemove,
				"Target A and partner B are both kept; no extra duplicate chains in 1TIM ASU");

		toRemove = BioJavaRfdiffusionParamService.computeChainsToRemove(
				proteinChains, "A", null, 0.98);

		assertEquals(List.of("B"), toRemove,
				"Without partner, homodimer copy B should be removed when A is target");
	}

	private static Structure readPdbResource(String resourcePath) throws Exception {
		try (InputStream in = BioJavaRfdiffusionParamServiceDuplicateChainTest.class.getResourceAsStream(resourcePath)) {
			assertNotNull(in, "Missing test resource: " + resourcePath);
			String pdbText = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			PDBFileReader reader = new PDBFileReader();
			try (ByteArrayInputStream bytes = new ByteArrayInputStream(pdbText.getBytes(StandardCharsets.UTF_8))) {
				return reader.getStructure(bytes);
			}
		}
	}

	private record ChainSpec(String id, String sequence, int firstSeqNum) {}

	private static Structure buildHomomerStructure(ChainSpec... specs) {
		Structure structure = new StructureImpl();
		for (ChainSpec spec : specs) {
			structure.addChain(buildChainFromSequence(spec.id(), spec.sequence(), spec.firstSeqNum()));
		}
		return structure;
	}

	private static Chain buildChainFromSequence(String chainId, String sequence, int firstSeqNum) {
		Chain chain = new ChainImpl();
		chain.setName(chainId);
		chain.setId(chainId);
		int seqNum = firstSeqNum;
		double x = firstSeqNum;
		for (char aa : sequence.toCharArray()) {
			AminoAcid residue = new AminoAcidImpl();
			residue.setPDBName(threeLetterCode(aa));
			residue.setResidueNumber(new ResidueNumber(chainId, seqNum++, null));
			Atom n = new AtomImpl();
			n.setName("N");
			n.setElement(Element.N);
			n.setX(x - 1.5);
			n.setY(0);
			n.setZ(0);
			residue.addAtom(n);
			Atom ca = new AtomImpl();
			ca.setName("CA");
			ca.setElement(Element.C);
			ca.setX(x);
			ca.setY(0);
			ca.setZ(0);
			residue.addAtom(ca);
			Atom c = new AtomImpl();
			c.setName("C");
			c.setElement(Element.C);
			c.setX(x + 1.5);
			c.setY(0);
			c.setZ(0);
			residue.addAtom(c);
			chain.addGroup(residue);
			x += 3.8;
		}
		return chain;
	}

	private static String threeLetterCode(char aa) {
		return switch (aa) {
			case 'A' -> "ALA";
			case 'C' -> "CYS";
			case 'D' -> "ASP";
			case 'E' -> "GLU";
			case 'F' -> "PHE";
			case 'G' -> "GLY";
			case 'H' -> "HIS";
			case 'I' -> "ILE";
			case 'K' -> "LYS";
			case 'L' -> "LEU";
			case 'M' -> "MET";
			case 'N' -> "ASN";
			case 'P' -> "PRO";
			case 'Q' -> "GLN";
			case 'R' -> "ARG";
			case 'S' -> "SER";
			case 'T' -> "THR";
			case 'V' -> "VAL";
			case 'W' -> "TRP";
			case 'Y' -> "TYR";
			default -> "ALA";
		};
	}
}
