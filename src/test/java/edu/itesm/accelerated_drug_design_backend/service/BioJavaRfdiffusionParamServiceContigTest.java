package edu.itesm.accelerated_drug_design_backend.service;

import org.biojava.nbio.structure.*;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class BioJavaRfdiffusionParamServiceContigTest {

	private static final Pattern CONTIG_RANGE = Pattern.compile("/0 ([A-Za-z])(\\d+[A-Za-z]?)-(\\d+[A-Za-z]?)");
	private static final Pattern HOTSPOT = Pattern.compile("[A-Za-z]?(\\d+[A-Za-z]?)");

	@Test
	void contigFromHotspots_snapsToValidResiduesWhenBufferFallsInNumberingGap() {
		Chain chain = buildChainWithResidueNumbers("A", 10, 11, 20, 21);
		List<Group> hotspots = List.of(chain.getAtomGroups().get(1));

		String contig = BioJavaRfdiffusionParamService.contigFromHotspots(
				"A", chain, hotspots, 12, 15, 15);

		assertEquals("15-15/0 A10-21", contig);
	}

	@Test
	void contigFromHotspots_throwsWhenRangeDegenerateAfterSnapping() {
		Chain chain = buildChainWithResidueNumbers("A", 100, 200);
		List<Group> hotspots = List.of(chain.getAtomGroups().get(0));

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
				BioJavaRfdiffusionParamService.contigFromHotspots("A", chain, hotspots, 500, 12, 12));
		assertTrue(ex.getMessage().contains("Invalid contig range"));
	}

	@Test
	void chainId_prefersAuthAsymIdOverInternalAsymId() {
		Chain chain = new ChainImpl();
		chain.setName("R");
		chain.setId("AA_0");
		addResidue(chain, "R", 309, null);
		addResidue(chain, "R", 310, null);
		addResidue(chain, "R", 311, null);
		addResidue(chain, "R", 312, null);

		assertEquals("R", BioJavaRfdiffusionParamService.chainId(chain));

		List<Group> hotspots = List.of(BioJavaRfdiffusionParamService.chainProteinResidues(chain).get(2));
		String contig = BioJavaRfdiffusionParamService.contigFromHotspots(
				"R", chain, hotspots, 1, 12, 15);

		assertEquals("12-15/0 R310-312", contig);
	}

	@Test
	void formatHotspots_includesInsertionCode() {
		AminoAcid withIns = new AminoAcidImpl();
		withIns.setPDBName("ALA");
		withIns.setResidueNumber(new ResidueNumber("R", 100, 'A'));
		addProteinBackbone(withIns, 100, 0, 0);

		String hotspots = BioJavaRfdiffusionParamService.formatHotspots("R", List.of(withIns));

		assertEquals("R100A", hotspots);
	}

	@Test
	void contigFromHotspots_preservesInsertionCodeOnRangeBoundaries() {
		Chain chain = new ChainImpl();
		chain.setName("R");
		chain.setId("R");
		addResidue(chain, "R", 10, null);
		addResidue(chain, "R", 100, null);
		addResidue(chain, "R", 100, 'A');
		addResidue(chain, "R", 100, 'B');
		addResidue(chain, "R", 105, null);

		List<Group> hotspots = List.of(
				chain.getAtomGroups().get(1),
				chain.getAtomGroups().get(3));

		String contig = BioJavaRfdiffusionParamService.contigFromHotspots(
				"R", chain, hotspots, 0, 12, 15);

		assertTrue(contig.endsWith("-100B"), contig);
		assertFalse(contig.endsWith("-100"), contig);
	}

	@Test
	void snapDownAndUp_extendBufferAsymmetricallyAtEquidistantTargets() {
		List<BioJavaRfdiffusionParamService.ResidueId> residues = BioJavaRfdiffusionParamService.chainResidueIds(
				buildChainWithResidueNumbers("A", 100, 110, 120));

		BioJavaRfdiffusionParamService.ResidueId start = BioJavaRfdiffusionParamService.snapDownToValidResidue(105, residues);
		BioJavaRfdiffusionParamService.ResidueId end = BioJavaRfdiffusionParamService.snapUpToValidResidue(115, residues);

		assertEquals(100, start.seqNum());
		assertEquals(120, end.seqNum());
	}

	@Test
	void snapToNearestValidResidue_prefersLowerSeqNumOnTie() {
		List<Integer> nums = List.of(10, 11, 20, 21);
		assertEquals(11, BioJavaRfdiffusionParamService.snapToNearestValidResidue(14, nums));
	}

	@Test
	void contigFromHotspots_extendsRangeToIncludeHotspotNearChainEndDespiteGaps() {
		Chain chain = buildChainWithResidueNumbers("A", 10, 20, 300, 305, 310);
		List<Group> hotspots = List.of(chain.getAtomGroups().get(3));

		String contig = BioJavaRfdiffusionParamService.contigFromHotspots(
				"A", chain, hotspots, 12, 12, 15);

		assertEquals("12-15/0 A300-310", contig);
	}

	@Test
	void distributeBinderLength_preservesRequestedRangeForAllSuggestions() {
		for (int i = 0; i < 5; i++) {
			int[] range = BioJavaRfdiffusionParamService.distributeBinderLength(i, 5, 12, 15);
			assertArrayEquals(new int[]{12, 15}, range, "index " + i);
		}
	}

	@Test
	void readPdbFromString_4Z18_firstProteinResidueIs19NotMissingPlaceholder() throws Exception {
		Structure structure = loadPdbResource("/4Z18.pdb");
		Chain chainA = structure.getChains().stream()
				.filter(c -> "A".equals(BioJavaRfdiffusionParamService.chainId(c)))
				.findFirst()
				.orElseThrow();

		List<Group> residues = BioJavaRfdiffusionParamService.chainProteinResidues(chainA);
		assertFalse(residues.isEmpty());
		assertEquals(19, residues.get(0).getResidueNumber().getSeqNum());
		assertEquals("PHE", ((AminoAcid) residues.get(0)).getPDBName());
	}

	@Test
	void isProteinResidue_rejectsIncompleteBackboneOrSyntheticOrigin() {
		AminoAcid caOnly = new AminoAcidImpl();
		caOnly.setPDBName("ALA");
		caOnly.setResidueNumber(new ResidueNumber("A", 10, null));
		caOnly.addAtom(backboneAtom("CA", 1, 2, 3));
		assertFalse(BioJavaRfdiffusionParamService.isProteinResidue(caOnly));

		AminoAcid synthetic = new AminoAcidImpl();
		synthetic.setPDBName("ALA");
		synthetic.setResidueNumber(new ResidueNumber("A", 18, null));
		addProteinBackbone(synthetic, 0, 0, 0);
		assertFalse(BioJavaRfdiffusionParamService.isProteinResidue(synthetic));

		AminoAcid valid = new AminoAcidImpl();
		valid.setPDBName("ALA");
		valid.setResidueNumber(new ResidueNumber("A", 19, null));
		addProteinBackbone(valid, 19, 0, 0);
		assertTrue(BioJavaRfdiffusionParamService.isProteinResidue(valid));
	}

	@Test
	void chainProteinResidues_keepsOnlyGroupsBelongingToRequestedChain() {
		Structure structure = new StructureImpl();

		Chain proteinChain = new ChainImpl();
		proteinChain.setName("A");
		proteinChain.setId("A");
		addResidue(proteinChain, "A", 10, null);
		addResidue(proteinChain, "A", 11, null);
		structure.addChain(proteinChain);

		Chain hetChain = new ChainImpl();
		hetChain.setName("A");
		hetChain.setId("A");
		AminoAcid het = new AminoAcidImpl();
		het.setPDBName("HOH");
		het.setResidueNumber(new ResidueNumber("B", 1, null));
		addProteinBackbone(het, 50, 0, 0);
		hetChain.addGroup(het);
		structure.addChain(hetChain);

		List<Group> proteinOnly = BioJavaRfdiffusionParamService.chainProteinResidues(proteinChain);

		assertEquals(2, proteinOnly.size());
		assertTrue(proteinOnly.stream().allMatch(g -> "A".equals(g.getChainId()) || "A".equals(g.getResidueNumber().getChainName())));
	}

	static Structure loadPdbResource(String resourcePath) throws Exception {
		try (InputStream in = BioJavaRfdiffusionParamServiceContigTest.class.getResourceAsStream(resourcePath)) {
			assertNotNull(in, "Missing test resource: " + resourcePath);
			String pdbText = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			return BioJavaRfdiffusionParamService.readPdbFromString(pdbText);
		}
	}

	static int parseContigStartSeq(String contig) {
		Matcher m = CONTIG_RANGE.matcher(contig);
		assertTrue(m.find(), contig);
		return parseResidueNum(m.group(2));
	}

	static int parseContigEndSeq(String contig) {
		Matcher m = CONTIG_RANGE.matcher(contig);
		assertTrue(m.find(), contig);
		return parseResidueNum(m.group(3));
	}

	static int parseHotspotSeq(String hotspotToken) {
		Matcher m = HOTSPOT.matcher(hotspotToken.trim());
		assertTrue(m.find(), hotspotToken);
		return parseResidueNum(m.group(1));
	}

	private static int parseResidueNum(String token) {
		return Integer.parseInt(token.replaceAll("[A-Za-z]", ""));
	}

	private static Chain buildChainWithResidueNumbers(String chainId, int... seqNums) {
		Chain chain = new ChainImpl();
		chain.setName(chainId);
		chain.setId(chainId);
		for (int seq : seqNums) {
			addResidue(chain, chainId, seq, null);
		}
		return chain;
	}

	private static void addResidue(Chain chain, String chainId, int seqNum, Character insCode) {
		AminoAcid aa = new AminoAcidImpl();
		aa.setPDBName("ALA");
		aa.setResidueNumber(new ResidueNumber(chainId, seqNum, insCode));
		addProteinBackbone(aa, seqNum, 0, 0);
		chain.addGroup(aa);
	}

	private static void addProteinBackbone(AminoAcid aa, double x, double y, double z) {
		aa.addAtom(backboneAtom("N", x - 1.5, y, z));
		aa.addAtom(backboneAtom("CA", x, y, z));
		aa.addAtom(backboneAtom("C", x + 1.5, y, z));
	}

	private static Atom backboneAtom(String name, double x, double y, double z) {
		Atom atom = new AtomImpl();
		atom.setName(name);
		atom.setElement(Element.C);
		atom.setX(x);
		atom.setY(y);
		atom.setZ(z);
		return atom;
	}
}
