package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.cache.PdbCacheService;
import edu.itesm.accelerated_drug_design_backend.dto.GenerateResult;
import edu.itesm.accelerated_drug_design_backend.dto.GenerateSuggestionsResponse;
import edu.itesm.accelerated_drug_design_backend.dto.RfdiffusionParamOptions;
import edu.itesm.accelerated_drug_design_backend.repository.ProjectRepository;
import org.biojava.nbio.structure.*;
import org.biojava.nbio.structure.io.FileParsingParameters;
import org.biojava.nbio.structure.io.PDBFileReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BioJavaRfdiffusionParamService implements RfdiffusionParamService {

	private static final Logger log = LoggerFactory.getLogger(BioJavaRfdiffusionParamService.class);

	private static final int BUFFER_RES = 12;
	private static final int MIN_CONTIG_SPAN_RESIDUES = 3;
	private static final double MAX_HOTSPOT_SPAN_FROM_ANCHOR = 15.0;
	private static final int MAX_HOTSPOT_SEQ_SPAN = 30;

	private static final double DEFAULT_INTERFACE_CUTOFF = 4.5;
	private static final double DEFAULT_EXPOSURE_RADIUS = 10.0;
	private static final double DEFAULT_PATCH_LINK_RADIUS = 8.0;
	private static final double DEFAULT_TOP_EXPOSED_FRACTION = 0.25;
	private static final double DEFAULT_DUPLICATE_IDENTITY_THRESHOLD = 0.98;

	private static final Map<String, Character> AA3_TO_1 = Map.ofEntries(
			Map.entry("ALA", 'A'), Map.entry("CYS", 'C'), Map.entry("ASP", 'D'), Map.entry("GLU", 'E'),
			Map.entry("PHE", 'F'), Map.entry("GLY", 'G'), Map.entry("HIS", 'H'), Map.entry("ILE", 'I'),
			Map.entry("LYS", 'K'), Map.entry("LEU", 'L'), Map.entry("MET", 'M'), Map.entry("ASN", 'N'),
			Map.entry("PRO", 'P'), Map.entry("GLN", 'Q'), Map.entry("ARG", 'R'), Map.entry("SER", 'S'),
			Map.entry("THR", 'T'), Map.entry("VAL", 'V'), Map.entry("TRP", 'W'), Map.entry("TYR", 'Y')
	);

	private static final Set<Character> FAVORABLE_HOTSPOT_AA =
			new HashSet<>(Arrays.asList('F', 'W', 'Y', 'R', 'K', 'D', 'E', 'H'));

	private final ProjectRepository projectRepository;
	private final PdbCacheService pdbCache;

	public BioJavaRfdiffusionParamService(ProjectRepository projectRepository, PdbCacheService pdbCache) {
		this.projectRepository = projectRepository;
		this.pdbCache = pdbCache;
	}

	private static class Suggestion {
		final String contig;
		final String hotspots;
		final List<String> chainsToRemove;

		Suggestion(String contig, String hotspots, List<String> chainsToRemove) {
			this.contig = contig;
			this.hotspots = hotspots;
			this.chainsToRemove = chainsToRemove;
		}
	}

	private static class ResolvedOptions {
		final int binderMin;
		final int binderMax;
		final int maxHotspots;
		final double interfaceCutoff;
		final double exposureRadius;
		final double patchLinkRadius;
		final double topExposedFraction;
		final double duplicateIdentityThreshold;

		ResolvedOptions(RfdiffusionParamOptions options) {
			this.binderMin = options.binderMin();
			this.binderMax = options.binderMax();
			this.maxHotspots = options.maxHotspots();
			this.interfaceCutoff = options.interfaceCutoff() != null ? options.interfaceCutoff() : DEFAULT_INTERFACE_CUTOFF;
			this.exposureRadius = options.exposureRadius() != null ? options.exposureRadius() : DEFAULT_EXPOSURE_RADIUS;
			this.patchLinkRadius = options.patchLinkRadius() != null ? options.patchLinkRadius() : DEFAULT_PATCH_LINK_RADIUS;
			this.topExposedFraction = options.topExposedFraction() != null ? options.topExposedFraction() : DEFAULT_TOP_EXPOSED_FRACTION;
			this.duplicateIdentityThreshold = options.duplicateIdentityThreshold() != null
					? options.duplicateIdentityThreshold() : DEFAULT_DUPLICATE_IDENTITY_THRESHOLD;
		}
	}

	@Override
	@Transactional(readOnly = true)
	public GenerateResult generateForProject(Long projectId) {
		List<GenerateResult> one = generateSuggestionsInternal(projectId, 1, RfdiffusionParamOptions.defaults());
		if (one.isEmpty()) {
			throw new IllegalStateException("Could not generate RFdiffusion parameters for project " + projectId);
		}
		return one.get(0);
	}

	@Override
	@Transactional(readOnly = true)
	public GenerateSuggestionsResponse generateNForProject(Long projectId, int count, int minLength, int maxLength) {
		if (count < 1) {
			throw new IllegalArgumentException("count must be at least 1");
		}
		if (minLength < 1) {
			throw new IllegalArgumentException("minLength must be at least 1");
		}
		if (maxLength < minLength) {
			throw new IllegalArgumentException("maxLength must be >= minLength");
		}
		RfdiffusionParamOptions options = RfdiffusionParamOptions.forLengthRange(minLength, maxLength, RfdiffusionParamOptions.DEFAULT_MAX_HOTSPOTS);
		List<GenerateResult> suggestions = generateSuggestionsInternal(projectId, count, options);
		if (suggestions.size() < count) {
			log.info("Project {}: requested {} suggestions, generated {} (insufficient distinct patches/hotspots)",
					projectId, count, suggestions.size());
		}
		return new GenerateSuggestionsResponse(count, suggestions.size(), suggestions);
	}

	List<GenerateResult> generateTargetSuggestionsFromPdb(String pdbText, int count, RfdiffusionParamOptions options) {
		try {
			ResolvedOptions resolved = new ResolvedOptions(options);
			Structure targetStruct = readPdbFromString(pdbText);
			List<Suggestion> raw = suggestMultipleFromTargetOnly(targetStruct, null, resolved, count);
			return toDistinctGenerateResults(raw, count);
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException("Failed to generate params from PDB text: " + e.getMessage(), e);
		}
	}

	private static List<GenerateResult> toDistinctGenerateResults(List<Suggestion> raw, int count) {
		List<GenerateResult> results = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (Suggestion s : raw) {
			String key = s.contig + "|" + s.hotspots + "|" + String.join(",", s.chainsToRemove);
			if (seen.add(key)) {
				results.add(new GenerateResult(s.contig, s.hotspots, s.chainsToRemove));
			}
			if (results.size() >= count) {
				break;
			}
		}
		return results;
	}

	private List<GenerateResult> generateSuggestionsInternal(Long projectId, int count, RfdiffusionParamOptions options) {
		String target = getTargetOrNull(projectId);
		String complex = getComplexOrNull(projectId);
		boolean hasComplex = StringUtils.hasText(complex);
		boolean hasTarget = StringUtils.hasText(target);
		if (!hasComplex && !hasTarget) {
			throw new IllegalStateException("Project has no PDB content in target/complex fields.");
		}

		try {
			ResolvedOptions resolved = new ResolvedOptions(options);
			List<Suggestion> raw;
			if (hasComplex) {
				Structure complexStruct = readPdbFromString(complex);
				raw = suggestMultipleFromComplex(complexStruct, null, null, resolved, count);
			} else {
				Structure targetStruct = readPdbFromString(target);
				raw = suggestMultipleFromTargetOnly(targetStruct, null, resolved, count);
			}

			return toDistinctGenerateResults(raw, count);
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException("Failed to generate params for project " + projectId + ": " + e.getMessage(), e);
		}
	}

	private String getTargetOrNull(Long projectId) {
		try {
			return getTarget(projectId);
		} catch (IllegalStateException e) {
			return null;
		}
	}

	private String getComplexOrNull(Long projectId) {
		try {
			return getComplex(projectId);
		} catch (IllegalStateException e) {
			return null;
		}
	}

	private String getTarget(Long projectId) {
		return pdbCache.getTarget(projectId)
				.orElseGet(() -> {
					String value = projectRepository.findTargetById(projectId)
							.orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
					if (value == null) {
						throw new IllegalStateException("Project target is null for id: " + projectId);
					}
					pdbCache.putTarget(projectId, value);
					return value;
				});
	}

	private String getComplex(Long projectId) {
		return pdbCache.getComplex(projectId)
				.orElseGet(() -> {
					String value = projectRepository.findComplexById(projectId)
							.orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
					if (value == null) {
						throw new IllegalStateException("Project complex is null for id: " + projectId);
					}
					pdbCache.putComplex(projectId, value);
					return value;
				});
	}

	static Structure readPdbFromString(String pdbText) throws Exception {
		PDBFileReader reader = new PDBFileReader();
		FileParsingParameters params = new FileParsingParameters();
		params.setAlignSeqRes(false);
		params.setParseSecStruc(false);
		params.setCreateAtomBonds(false);
		reader.setFileParsingParameters(params);
		try (ByteArrayInputStream in = new ByteArrayInputStream(pdbText.getBytes(StandardCharsets.UTF_8))) {
			return reader.getStructure(in);
		}
	}

	private List<Suggestion> suggestMultipleFromComplex(Structure structure, String targetChainId, String partnerChainId,
			ResolvedOptions options, int maxSuggestions) {
		List<Chain> proteinChains = structure.getChains().stream()
				.filter(c -> chainProteinResidues(c).size() >= 30)
				.collect(Collectors.toList());
		if (proteinChains.isEmpty()) {
			throw new IllegalArgumentException("No protein chains (>=30 residues) found in complex PDB.");
		}

		if (!StringUtils.hasText(targetChainId) || !StringUtils.hasText(partnerChainId)) {
			List<Chain> sorted = new ArrayList<>(proteinChains);
			sorted.sort((a, b) -> Integer.compare(chainProteinResidues(b).size(), chainProteinResidues(a).size()));
			if (!StringUtils.hasText(targetChainId)) {
				targetChainId = chainId(sorted.get(0));
			}
			if (!StringUtils.hasText(partnerChainId)) {
				String t = targetChainId;
				partnerChainId = sorted.stream().map(BioJavaRfdiffusionParamService::chainId)
						.filter(id -> !id.equals(t)).findFirst().orElse(null);
			}
		}
		if (!StringUtils.hasText(partnerChainId)) {
			throw new IllegalArgumentException("Could not infer partner chain in complex PDB.");
		}

		Chain targetChain = getChainById(structure, targetChainId);
		Chain partnerChain = getChainById(structure, partnerChainId);
		List<String> chainsToRemove = computeChainsToRemove(proteinChains, targetChainId, partnerChainId, options.duplicateIdentityThreshold);

		List<Map.Entry<Group, Double>> scored = scoreInterfaceResidues(targetChain, partnerChain, options.interfaceCutoff);
		List<List<Group>> patches = buildRankedPatchesFromScored(scored, options.patchLinkRadius, options.topExposedFraction);

		return buildSuggestionsFromPatches(targetChainId, targetChain, patches, scored, chainsToRemove, options, maxSuggestions);
	}

	private List<Suggestion> suggestMultipleFromTargetOnly(Structure structure, String targetChainId,
			ResolvedOptions options, int maxSuggestions) {
		List<Chain> proteinChains = structure.getChains().stream()
				.filter(c -> chainProteinResidues(c).size() >= 30)
				.collect(Collectors.toList());
		if (proteinChains.isEmpty()) {
			throw new IllegalArgumentException("No protein chains (>=30 residues) found in target PDB.");
		}
		if (!StringUtils.hasText(targetChainId)) {
			targetChainId = chainId(chooseBestChainByCompleteness(proteinChains));
		}
		Chain targetChain = getChainById(structure, targetChainId);
		List<String> chainsToRemove = computeChainsToRemove(proteinChains, targetChainId, null, options.duplicateIdentityThreshold);

		List<Map.Entry<Group, Double>> exposureScores = scoreExposedResidues(targetChain, options.exposureRadius);
		List<List<Group>> patches = buildRankedPatchesFromScored(exposureScores, options.patchLinkRadius, options.topExposedFraction);

		return buildSuggestionsFromPatches(targetChainId, targetChain, patches, exposureScores, chainsToRemove, options, maxSuggestions);
	}

	private List<Suggestion> buildSuggestionsFromPatches(String chainId, Chain targetChain, List<List<Group>> patches,
			List<Map.Entry<Group, Double>> scoredResidues, List<String> chainsToRemove,
			ResolvedOptions options, int maxSuggestions) {
		List<Suggestion> out = new ArrayList<>();
		Map<Group, Double> scoreMap = new HashMap<>();
		for (Map.Entry<Group, Double> e : scoredResidues) {
			scoreMap.put(e.getKey(), e.getValue());
		}

		int patchAttempts = Math.max(patches.size(), maxSuggestions * 2);
		for (int i = 0; i < patchAttempts && out.size() < maxSuggestions; i++) {
			List<Group> patch;
			if (i < patches.size()) {
				patch = patches.get(i);
			} else {
				int offset = (i - patches.size() + 1) * 2;
				patch = buildOffsetPatch(scoredResidues, offset, options.patchLinkRadius);
				if (patch.isEmpty()) {
					continue;
				}
			}

			int maxHotspots = Math.max(3, options.maxHotspots - (i % 3));
			List<Map.Entry<Group, Double>> scoredPatch = new ArrayList<>();
			for (Group r : patch) {
				scoredPatch.add(new AbstractMap.SimpleEntry<>(r, scoreMap.getOrDefault(r, 0.0)));
			}
			scoredPatch.sort((x, y) -> Double.compare(y.getValue(), x.getValue()));
			scoredPatch = filterPatchBySequentialSpan(scoredPatch, MAX_HOTSPOT_SEQ_SPAN);
			if (scoredPatch.size() < MIN_CONTIG_SPAN_RESIDUES) {
				continue;
			}

			List<Group> hotspotsRes = pickHotspotsWithSpacing(
					scoredPatch, maxHotspots, 6.0, MAX_HOTSPOT_SPAN_FROM_ANCHOR);
			if (hotspotsRes.isEmpty()) {
				continue;
			}

			int[] binderRange = distributeBinderLength(i, maxSuggestions, options.binderMin, options.binderMax);
			try {
				String hotspots = formatHotspots(chainId, hotspotsRes);
				String contig = contigFromHotspots(chainId, targetChain, hotspotsRes, BUFFER_RES, binderRange[0], binderRange[1]);
				out.add(new Suggestion(contig, hotspots, chainsToRemove));
			} catch (IllegalArgumentException e) {
				log.debug("Skipping suggestion index {} for chain {}: {}", i, chainId, e.getMessage());
			}
		}
		return out;
	}

	static int[] distributeBinderLength(int index, int total, int minLength, int maxLength) {
		// Preserve the requested binder length range; diversity comes from distinct contigs/hotspots.
		return new int[]{minLength, maxLength};
	}

	private static List<Map.Entry<Group, Double>> filterPatchBySequentialSpan(
			List<Map.Entry<Group, Double>> scoredPatch, int maxSeqSpan) {
		if (scoredPatch.isEmpty()) {
			return scoredPatch;
		}
		int anchorSeq = residueSeqNum(scoredPatch.get(0).getKey());
		return scoredPatch.stream()
				.filter(e -> Math.abs(residueSeqNum(e.getKey()) - anchorSeq) <= maxSeqSpan)
				.collect(Collectors.toList());
	}

	private static int residueSeqNum(Group g) {
		ResidueNumber rn = g.getResidueNumber();
		return rn != null ? rn.getSeqNum() : -1;
	}

	private static List<Group> buildOffsetPatch(List<Map.Entry<Group, Double>> scored, int skip, double patchLinkRadius) {
		if (scored.isEmpty() || skip >= scored.size()) {
			return List.of();
		}
		Group seed = scored.get(skip).getKey();
		List<Group> candidates = scored.stream().map(Map.Entry::getKey).collect(Collectors.toList());
		Set<Group> visited = new HashSet<>();
		List<Group> patch = new ArrayList<>();
		Deque<Group> queue = new ArrayDeque<>();
		queue.add(seed);
		visited.add(seed);

		while (!queue.isEmpty()) {
			Group current = queue.removeFirst();
			patch.add(current);
			Atom currentCa = ((AminoAcid) current).getCA();
			if (currentCa == null) {
				continue;
			}
			for (Group other : candidates) {
				if (visited.contains(other)) {
					continue;
				}
				Atom otherCa = ((AminoAcid) other).getCA();
				if (otherCa == null) {
					continue;
				}
				if (distance(currentCa, otherCa) <= patchLinkRadius) {
					visited.add(other);
					queue.addLast(other);
				}
			}
		}
		if (patch.size() < MIN_CONTIG_SPAN_RESIDUES) {
			return List.of();
		}
		return patch;
	}

	private List<Map.Entry<Group, Double>> scoreInterfaceResidues(Chain targetChain, Chain partnerChain, double interfaceCutoff) {
		List<Group> targetRes = chainProteinResidues(targetChain);
		List<Atom> partnerAtoms = heavyAtomsOfChain(partnerChain);
		List<Map.Entry<Group, Double>> scored = new ArrayList<>();
		for (Group r : targetRes) {
			List<Atom> atoms = heavyAtomsOfGroup(r);
			int contactCount = 0;
			for (Atom a : atoms) {
				for (Atom pb : partnerAtoms) {
					if (distance(a, pb) <= interfaceCutoff) {
						contactCount++;
					}
				}
			}
			char aa1 = AA3_TO_1.getOrDefault(((AminoAcid) r).getPDBName(), 'X');
			double bonus = FAVORABLE_HOTSPOT_AA.contains(aa1) ? 0.15 : 0.0;
			scored.add(new AbstractMap.SimpleEntry<>(r, contactCount + bonus));
		}
		scored.sort((x, y) -> Double.compare(y.getValue(), x.getValue()));
		return scored;
	}

	private List<Map.Entry<Group, Double>> scoreExposedResidues(Chain targetChain, double exposureRadius) {
		List<Group> targetRes = chainProteinResidues(targetChain);
		List<Atom> caAtoms = targetRes.stream()
				.map(g -> ((AminoAcid) g).getCA())
				.filter(Objects::nonNull)
				.collect(Collectors.toList());

		List<Map.Entry<Group, Double>> exposureScores = new ArrayList<>();
		for (Group r : targetRes) {
			Atom ca = ((AminoAcid) r).getCA();
			if (ca == null) {
				continue;
			}
			int neighborCount = 0;
			for (Atom other : caAtoms) {
				if (distance(ca, other) <= exposureRadius) {
					neighborCount++;
				}
			}
			char aa1 = AA3_TO_1.getOrDefault(((AminoAcid) r).getPDBName(), 'X');
			double aaBonus = FAVORABLE_HOTSPOT_AA.contains(aa1) ? 0.2 : 0.0;
			exposureScores.add(new AbstractMap.SimpleEntry<>(r, (-neighborCount) + aaBonus));
		}
		exposureScores.sort((x, y) -> Double.compare(y.getValue(), x.getValue()));
		return exposureScores;
	}

	private List<List<Group>> buildRankedPatchesFromScored(List<Map.Entry<Group, Double>> scoredResidues,
			double patchLinkRadius, double topExposedFraction) {
		int k = Math.max(10, (int) (scoredResidues.size() * topExposedFraction));
		k = Math.min(k, scoredResidues.size());
		List<Group> exposed = scoredResidues.subList(0, k).stream()
				.map(Map.Entry::getKey).collect(Collectors.toList());

		List<List<Group>> patches = clusterPatches(exposed, patchLinkRadius);
		Map<Group, Double> expMapTopK = new HashMap<>();
		for (int i = 0; i < k; i++) {
			expMapTopK.put(scoredResidues.get(i).getKey(), scoredResidues.get(i).getValue());
		}
		patches.sort((p1, p2) -> Double.compare(patchScore(p2, expMapTopK), patchScore(p1, expMapTopK)));
		return patches;
	}

	static List<String> computeChainsToRemove(List<Chain> proteinChains, String targetChainId,
			String partnerChainId, double duplicateIdentityThreshold) {
		Map<String, List<String>> dupGroups = findDuplicateChains(proteinChains, duplicateIdentityThreshold);
		Set<String> chainsToRemove = new HashSet<>();
		for (List<String> members : dupGroups.values()) {
			if (members.size() <= 1) {
				continue;
			}
			Set<String> keep = new HashSet<>();
			if (members.contains(targetChainId)) {
				keep.add(targetChainId);
			}
			if (partnerChainId != null && members.contains(partnerChainId)) {
				keep.add(partnerChainId);
			}
			if (keep.isEmpty()) {
				keep.add(members.get(0));
			}
			for (String m : members) {
				if (!keep.contains(m)) {
					chainsToRemove.add(m);
				}
			}
		}
		return chainsToRemove.stream().sorted().collect(Collectors.toList());
	}

	private List<List<Group>> clusterPatches(List<Group> exposed, double linkRadius) {
		Set<Group> visited = new HashSet<>();
		List<List<Group>> patches = new ArrayList<>();

		for (Group start : exposed) {
			if (visited.contains(start)) {
				continue;
			}
			List<Group> patch = new ArrayList<>();
			Deque<Group> q = new ArrayDeque<>();
			q.add(start);
			visited.add(start);

			while (!q.isEmpty()) {
				Group cur = q.removeFirst();
				patch.add(cur);
				Atom curCA = ((AminoAcid) cur).getCA();
				if (curCA == null) {
					continue;
				}
				for (Group rr : exposed) {
					if (visited.contains(rr)) {
						continue;
					}
					Atom rrCA = ((AminoAcid) rr).getCA();
					if (rrCA == null) {
						continue;
					}
					if (distance(curCA, rrCA) <= linkRadius) {
						visited.add(rr);
						q.addLast(rr);
					}
				}
			}
			patches.add(patch);
		}
		return patches;
	}

	private double patchScore(List<Group> patch, Map<Group, Double> expMapTopK) {
		int size = patch.size();
		int fav = 0;
		double expSum = 0.0;
		for (Group r : patch) {
			char aa1 = AA3_TO_1.getOrDefault(((AminoAcid) r).getPDBName(), 'X');
			if (FAVORABLE_HOTSPOT_AA.contains(aa1)) {
				fav++;
			}
			expSum += expMapTopK.getOrDefault(r, 0.0);
		}
		double avgExp = expSum / Math.max(1, size);
		return (size * 1.0) + (fav * 0.6) + (avgExp * 0.8);
	}

	static final class ResidueId implements Comparable<ResidueId> {
		private final int seqNum;
		private final Character insCode;

		private ResidueId(int seqNum, Character insCode) {
			this.seqNum = seqNum;
			this.insCode = normalizeInsCode(insCode);
		}

		static Character normalizeInsCode(Character insCode) {
			if (insCode == null || insCode == ' ') {
				return null;
			}
			return insCode;
		}

		static ResidueId fromGroup(Group g) {
			ResidueNumber rn = g.getResidueNumber();
			if (rn == null || rn.getSeqNum() <= 0) {
				return null;
			}
			return new ResidueId(rn.getSeqNum(), rn.getInsCode());
		}

		int seqNum() {
			return seqNum;
		}

		Character insCode() {
			return insCode;
		}

		String formatSuffix() {
			if (insCode != null) {
				return seqNum + insCode.toString();
			}
			return Integer.toString(seqNum);
		}

		@Override
		public int compareTo(ResidueId other) {
			int bySeq = Integer.compare(this.seqNum, other.seqNum);
			if (bySeq != 0) {
				return bySeq;
			}
			return Integer.compare(insCodeOrder(this.insCode), insCodeOrder(other.insCode));
		}

		private static int insCodeOrder(Character insCode) {
			if (insCode == null) {
				return 0;
			}
			return insCode;
		}
	}

	static List<ResidueId> chainResidueIds(Chain chain) {
		return chainProteinResidues(chain).stream()
				.map(ResidueId::fromGroup)
				.filter(Objects::nonNull)
				.sorted()
				.collect(Collectors.toList());
	}

	static ResidueId snapDownToValidResidue(int targetSeqNum, List<ResidueId> sortedResidues) {
		if (sortedResidues.isEmpty()) {
			throw new IllegalArgumentException("Chain has no protein residues");
		}
		ResidueId result = sortedResidues.get(0);
		for (ResidueId residue : sortedResidues) {
			if (residue.seqNum() <= targetSeqNum) {
				result = residue;
			} else {
				break;
			}
		}
		return result;
	}

	static ResidueId snapUpToValidResidue(int targetSeqNum, List<ResidueId> sortedResidues) {
		if (sortedResidues.isEmpty()) {
			throw new IllegalArgumentException("Chain has no protein residues");
		}
		for (ResidueId residue : sortedResidues) {
			if (residue.seqNum() >= targetSeqNum) {
				return residue;
			}
		}
		return sortedResidues.get(sortedResidues.size() - 1);
	}

	static int snapToNearestValidResidue(int value, List<Integer> sortedResidues) {
		if (sortedResidues.isEmpty()) {
			throw new IllegalArgumentException("Chain has no protein residues");
		}
		int best = sortedResidues.get(0);
		int bestDist = Math.abs(value - best);
		for (int n : sortedResidues) {
			int d = Math.abs(value - n);
			if (d < bestDist) {
				bestDist = d;
				best = n;
			}
		}
		return best;
	}

	static void validateContigResidueRange(ResidueId start, ResidueId end, List<ResidueId> sortedResidues,
			int minSpanResidues) {
		if (sortedResidues.isEmpty()) {
			throw new IllegalArgumentException("Cannot build contig: chain has no protein residues");
		}
		if (start.compareTo(end) >= 0) {
			throw new IllegalArgumentException(
					"Invalid contig range: start (" + start.formatSuffix() + ") >= end (" + end.formatSuffix() + ")");
		}
		long count = sortedResidues.stream()
				.filter(r -> r.compareTo(start) >= 0 && r.compareTo(end) <= 0)
				.count();
		if (count < minSpanResidues) {
			throw new IllegalArgumentException(
					"Invalid contig range: only " + count + " residues between " + start.formatSuffix()
							+ " and " + end.formatSuffix() + " (minimum " + minSpanResidues + ")");
		}
	}

	static String contigFromHotspots(String chainId, Chain targetChain, List<Group> hotspots,
			int bufferRes, int binderMin, int binderMax) {
		List<ResidueId> chainIds = chainResidueIds(targetChain);
		List<ResidueId> hotspotIds = hotspots.stream()
				.map(ResidueId::fromGroup)
				.filter(Objects::nonNull)
				.sorted()
				.collect(Collectors.toList());

		int rawStartSeq;
		int rawEndSeq;
		if (hotspotIds.isEmpty()) {
			rawStartSeq = chainIds.get(0).seqNum();
			rawEndSeq = chainIds.get(chainIds.size() - 1).seqNum();
		} else {
			rawStartSeq = hotspotIds.get(0).seqNum() - bufferRes;
			rawEndSeq = hotspotIds.get(hotspotIds.size() - 1).seqNum() + bufferRes;
		}

		ResidueId first = chainIds.get(0);
		ResidueId last = chainIds.get(chainIds.size() - 1);

		int clampedStartSeq = Math.max(first.seqNum(), rawStartSeq);
		int clampedEndSeq = Math.min(last.seqNum(), rawEndSeq);

		// Start: largest residue <= target (extends buffer left). End: smallest residue >= target (extends right).
		ResidueId start = snapDownToValidResidue(clampedStartSeq, chainIds);
		ResidueId end = snapUpToValidResidue(clampedEndSeq, chainIds);

		// Gaps in PDB numbering can shrink the snapped range below the hotspot span; extend to include all hotspots.
		if (!hotspotIds.isEmpty()) {
			ResidueId minHotspot = hotspotIds.get(0);
			ResidueId maxHotspot = hotspotIds.get(hotspotIds.size() - 1);

			if (start.compareTo(minHotspot) < 0) {
				ResidueId startFromAbove = snapUpToValidResidue(clampedStartSeq, chainIds);
				if (startFromAbove.compareTo(minHotspot) <= 0
						&& startFromAbove.compareTo(start) > 0
						&& Math.abs(startFromAbove.seqNum() - clampedStartSeq)
						< Math.abs(start.seqNum() - clampedStartSeq)) {
					start = startFromAbove;
				}
			}

			if (minHotspot.compareTo(start) < 0) {
				start = minHotspot;
			}
			if (maxHotspot.compareTo(end) > 0) {
				end = maxHotspot;
			}
			for (ResidueId hotspot : hotspotIds) {
				if (hotspot.compareTo(start) < 0 || hotspot.compareTo(end) > 0) {
					throw new IllegalArgumentException(
							"Hotspot " + chainId + hotspot.formatSuffix() + " falls outside contig range "
									+ chainId + start.formatSuffix() + "-" + end.formatSuffix());
				}
			}
		}

		validateContigResidueRange(start, end, chainIds, MIN_CONTIG_SPAN_RESIDUES);

		return String.format("%d-%d/0 %s%s-%s", binderMin, binderMax, chainId, start.formatSuffix(), end.formatSuffix());
	}

	static String chainId(Chain c) {
		String name = c.getName();
		if (name != null && !name.isBlank()) {
			return name.trim();
		}
		String id = c.getId();
		if (id != null && !id.isBlank()) {
			return id.trim();
		}
		return "?";
	}

	private static Chain getChainById(Structure s, String id) {
		for (Chain c : s.getChains()) {
			if (chainId(c).equals(id)) {
				return c;
			}
		}
		throw new IllegalArgumentException("Chain not found: " + id);
	}

	static List<Group> chainProteinResidues(Chain chain) {
		String expectedChainId = chainId(chain);
		List<Group> out = new ArrayList<>();
		for (Group g : chain.getAtomGroups()) {
			if (!belongsToChain(g, expectedChainId)) {
				continue;
			}
			if (isProteinResidue(g)) {
				out.add(g);
			}
		}
		if (!out.isEmpty()) {
			ResidueId first = ResidueId.fromGroup(out.get(0));
			ResidueId last = ResidueId.fromGroup(out.get(out.size() - 1));
			log.debug("Chain {} protein residues: {}..{} ({} residues)",
					expectedChainId,
					first != null ? first.formatSuffix() : "?",
					last != null ? last.formatSuffix() : "?",
					out.size());
		}
		return out;
	}

	private static boolean belongsToChain(Group g, String expectedChainId) {
		ResidueNumber rn = g.getResidueNumber();
		if (rn != null && rn.getChainName() != null && !rn.getChainName().isBlank()) {
			return expectedChainId.equals(rn.getChainName().trim());
		}
		String groupChainId = g.getChainId();
		if (groupChainId != null && !groupChainId.isBlank()) {
			return expectedChainId.equals(groupChainId.trim());
		}
		return true;
	}

	static boolean isProteinResidue(Group g) {
		if (!(g instanceof AminoAcid aa)) {
			return false;
		}
		String resName = aa.getPDBName();
		if (!AA3_TO_1.containsKey(resName)) {
			return false;
		}
		Atom n = aa.getAtom("N");
		Atom ca = aa.getAtom("CA");
		Atom c = aa.getAtom("C");
		if (n == null || ca == null || c == null) {
			return false;
		}
		return !isSyntheticOrigin(ca);
	}

	private static boolean isSyntheticOrigin(Atom ca) {
		return ca.getX() == 0.0 && ca.getY() == 0.0 && ca.getZ() == 0.0;
	}

	private static Chain chooseBestChainByCompleteness(List<Chain> chains) {
		Chain best = null;
		int bestN = -1;
		for (Chain c : chains) {
			int n = chainProteinResidues(c).size();
			if (n > bestN) {
				bestN = n;
				best = c;
			}
		}
		if (best == null) {
			throw new IllegalStateException("No chains to choose from.");
		}
		return best;
	}

	private static final int MAX_SEQUENCE_ALIGNMENT_OFFSET = 15;

	private static String chainSequence(Chain chain) {
		StringBuilder sb = new StringBuilder();
		for (Group g : chain.getAtomGroups()) {
			if (isProteinResidue(g)) {
				sb.append(AA3_TO_1.get(((AminoAcid) g).getPDBName()));
			}
		}
		return sb.toString();
	}

	/** Positional comparison kept for regression tests documenting the original bug. */
	static double seqSimilarityPositional(String a, String b) {
		if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
			return 0.0;
		}
		int n = Math.min(a.length(), b.length());
		int matches = 0;
		for (int i = 0; i < n; i++) {
			if (a.charAt(i) == b.charAt(i)) {
				matches++;
			}
		}
		return (double) matches / (double) n;
	}

	static double seqSimilarity(String a, String b) {
		if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
			return 0.0;
		}
		if (a.equals(b)) {
			return 1.0;
		}
		return Math.max(bestOffsetSimilarity(a, b), bestOffsetSimilarity(b, a));
	}

	private static double bestOffsetSimilarity(String anchor, String sliding) {
		double best = 0.0;
		int minOffset = -Math.min(MAX_SEQUENCE_ALIGNMENT_OFFSET, sliding.length() - 1);
		int maxOffset = Math.min(MAX_SEQUENCE_ALIGNMENT_OFFSET, anchor.length() - 1);
		for (int offset = minOffset; offset <= maxOffset; offset++) {
			int startAnchor = Math.max(0, offset);
			int startSliding = Math.max(0, -offset);
			int overlap = Math.min(anchor.length() - startAnchor, sliding.length() - startSliding);
			if (overlap <= 0) {
				continue;
			}
			int matches = 0;
			for (int i = 0; i < overlap; i++) {
				if (anchor.charAt(startAnchor + i) == sliding.charAt(startSliding + i)) {
					matches++;
				}
			}
			int refLen = Math.min(anchor.length(), sliding.length());
			best = Math.max(best, (double) matches / refLen);
		}
		return best;
	}

	static Map<String, List<String>> findDuplicateChains(List<Chain> chains, double identityThreshold) {
		Map<String, String> seqs = new HashMap<>();
		for (Chain c : chains) {
			seqs.put(chainId(c), chainSequence(c));
		}

		Set<String> used = new HashSet<>();
		Map<String, List<String>> groups = new LinkedHashMap<>();

		for (Chain c : chains) {
			String id = chainId(c);
			if (used.contains(id)) {
				continue;
			}
			used.add(id);
			List<String> group = new ArrayList<>();
			group.add(id);

			for (Chain other : chains) {
				String oid = chainId(other);
				if (used.contains(oid)) {
					continue;
				}
				double sim = seqSimilarity(seqs.get(id), seqs.get(oid));
				if (sim >= identityThreshold) {
					used.add(oid);
					group.add(oid);
				}
			}
			groups.put(id, group);
		}
		return groups;
	}

	private static boolean isHeavyAtom(Atom a) {
		Element e = a.getElement();
		if (e != null) {
			return e != Element.H;
		}
		String name = a.getName();
		return name == null || !name.startsWith("H");
	}

	private static List<Atom> heavyAtomsOfGroup(Group g) {
		List<Atom> atoms = new ArrayList<>();
		for (Atom a : g.getAtoms()) {
			if (isHeavyAtom(a)) {
				atoms.add(a);
			}
		}
		return atoms;
	}

	private static List<Atom> heavyAtomsOfChain(Chain c) {
		List<Atom> atoms = new ArrayList<>();
		for (Group g : c.getAtomGroups()) {
			atoms.addAll(heavyAtomsOfGroup(g));
		}
		return atoms;
	}

	private static double distance(Atom a, Atom b) {
		double dx = a.getX() - b.getX();
		double dy = a.getY() - b.getY();
		double dz = a.getZ() - b.getZ();
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	private static double caDistance(Group r1, Group r2) {
		Atom a = ((AminoAcid) r1).getCA();
		Atom b = ((AminoAcid) r2).getCA();
		return distance(a, b);
	}

	private static List<Group> pickHotspotsWithSpacing(
			List<Map.Entry<Group, Double>> residuesScored,
			int maxHotspots,
			double minCaSeparation,
			double maxSpanFromAnchor
	) {
		if (residuesScored.isEmpty()) {
			return List.of();
		}
		Atom anchorCa = ((AminoAcid) residuesScored.get(0).getKey()).getCA();
		if (anchorCa == null) {
			return List.of();
		}

		List<Group> chosen = new ArrayList<>();
		for (Map.Entry<Group, Double> e : residuesScored) {
			if (chosen.size() >= maxHotspots) {
				break;
			}
			Group r = e.getKey();
			if (!isProteinResidue(r)) {
				continue;
			}
			Atom candidateCa = ((AminoAcid) r).getCA();
			if (candidateCa == null || distance(anchorCa, candidateCa) > maxSpanFromAnchor) {
				continue;
			}
			int candidateSeq = residueSeqNum(r);
			boolean ok = true;
			for (Group c : chosen) {
				if (caDistance(r, c) < minCaSeparation) {
					ok = false;
					break;
				}
				int chosenSeq = residueSeqNum(c);
				if (Math.abs(candidateSeq - chosenSeq) > MAX_HOTSPOT_SEQ_SPAN) {
					ok = false;
					break;
				}
			}
			if (ok) {
				chosen.add(r);
			}
		}
		return chosen;
	}

	static String formatHotspots(String chainId, List<Group> residues) {
		TreeSet<ResidueId> ids = new TreeSet<>();
		for (Group g : residues) {
			ResidueId id = ResidueId.fromGroup(g);
			if (id != null) {
				ids.add(id);
			}
		}
		return ids.stream()
				.map(r -> chainId + r.formatSuffix())
				.collect(Collectors.joining(","));
	}
}
