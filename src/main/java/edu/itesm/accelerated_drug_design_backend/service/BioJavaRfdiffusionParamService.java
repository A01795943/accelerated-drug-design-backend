package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.cache.PdbCacheService;
import edu.itesm.accelerated_drug_design_backend.dto.GenerateResult;
import edu.itesm.accelerated_drug_design_backend.entity.Project;
import edu.itesm.accelerated_drug_design_backend.repository.ProjectRepository;
import org.biojava.nbio.structure.*;
import org.biojava.nbio.structure.io.PDBFileReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BioJavaRfdiffusionParamService implements RfdiffusionParamService {

    private final ProjectRepository projectRepository;
    private final PdbCacheService pdbCache;

    public BioJavaRfdiffusionParamService(ProjectRepository projectRepository, PdbCacheService pdbCache) {
        this.projectRepository = projectRepository;
        this.pdbCache = pdbCache;
    }

    // Defaults (igual a tu script)
    private final int binderMin = 12;
    private final int binderMax = 30;
    private final int maxHotspots = 7;
    private final int bufferRes = 12;
    private final double duplicateIdentityThreshold = 0.98;

    private final double interfaceCutoff = 4.5;

    private final double exposureRadius = 10.0;
    private final double patchLinkRadius = 8.0;
    private final double topExposedFraction = 0.25;

    private static final Map<String, Character> AA3_TO_1 = Map.ofEntries(
            Map.entry("ALA", 'A'), Map.entry("CYS", 'C'), Map.entry("ASP", 'D'), Map.entry("GLU", 'E'),
            Map.entry("PHE", 'F'), Map.entry("GLY", 'G'), Map.entry("HIS", 'H'), Map.entry("ILE", 'I'),
            Map.entry("LYS", 'K'), Map.entry("LEU", 'L'), Map.entry("MET", 'M'), Map.entry("ASN", 'N'),
            Map.entry("PRO", 'P'), Map.entry("GLN", 'Q'), Map.entry("ARG", 'R'), Map.entry("SER", 'S'),
            Map.entry("THR", 'T'), Map.entry("VAL", 'V'), Map.entry("TRP", 'W'), Map.entry("TYR", 'Y')
    );

    private static final Set<Character> FAVORABLE_HOTSPOT_AA =
            new HashSet<>(Arrays.asList('F','W','Y','R','K','D','E','H'));

    private static class Suggestion {
        String contig;
        String hotspots;
        List<String> chainsToRemove;
        Suggestion(String contig, String hotspots, List<String> chainsToRemove) {
            this.contig = contig;
            this.hotspots = hotspots;
            this.chainsToRemove = chainsToRemove;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public GenerateResult generateForProject(Long projectId) {
        String target = getTarget(projectId);
        String complex = getComplex(projectId);
        boolean hasComplex = StringUtils.hasText(complex);
        boolean hasTarget = StringUtils.hasText(target);

        if (!hasComplex && !hasTarget) {
            throw new IllegalStateException("Project has no PDB content in target/complex fields.");
        }

        try {
            Suggestion s;
            if (hasComplex) {
                Structure complexStruct = readPdbFromString(complex);
                s = suggestFromComplex(complexStruct, null, null);
            } else {
                Structure targetStruct = readPdbFromString(target);
                s = suggestFromTargetOnly(targetStruct, null);
            }
            return new GenerateResult(s.contig, s.hotspots, s.chainsToRemove);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate params for project " + projectId + ": " + e.getMessage(), e);
        }
    }

    private String getTarget(Long projectId) {
        return pdbCache.getTarget(projectId)
                .orElseGet(() -> {
                    String value = projectRepository.findTargetById(projectId)
                            .orElseThrow(() ->
                                    new IllegalArgumentException("Project not found: " + projectId));

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
                            .orElseThrow(() ->
                                    new IllegalArgumentException("Project not found: " + projectId));

                    if (value == null) {
                        throw new IllegalStateException("Project complex is null for id: " + projectId);
                    }

                    pdbCache.putComplex(projectId, value);
                    return value;
                });
    }


    // ---------------- Parsing ----------------

    private static Structure readPdbFromString(String pdbText) throws Exception {
        PDBFileReader r = new PDBFileReader();
        try (ByteArrayInputStream in = new ByteArrayInputStream(pdbText.getBytes(StandardCharsets.UTF_8))) {
            return r.getStructure(in);
        }
    }

    // ---------------- Complex mode ----------------

    private Suggestion suggestFromComplex(Structure structure, String targetChainId, String partnerChainId) {
        List<Chain> chains = structure.getChains();
        List<Chain> proteinChains = chains.stream()
                .filter(c -> chainProteinResidues(c).size() >= 30)
                .collect(Collectors.toList());

        if (proteinChains.isEmpty()) {
            throw new IllegalArgumentException("No protein chains (>=30 residues) found in complex PDB.");
        }

        // Auto-select: 2 longest
        if (!StringUtils.hasText(targetChainId) || !StringUtils.hasText(partnerChainId)) {
            List<Chain> sorted = new ArrayList<>(proteinChains);
            sorted.sort((a, b) -> Integer.compare(chainProteinResidues(b).size(), chainProteinResidues(a).size()));
            if (!StringUtils.hasText(targetChainId)) targetChainId = chainId(sorted.get(0));
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

        // Duplicates
        Map<String, List<String>> dupGroups = findDuplicateChains(proteinChains, duplicateIdentityThreshold);
        Set<String> chainsToRemove = new HashSet<>();
        for (List<String> members : dupGroups.values()) {
            if (members.size() <= 1) continue;
            String keep;
            if (members.contains(targetChainId)) keep = targetChainId;
            else if (members.contains(partnerChainId)) keep = partnerChainId;
            else keep = members.get(0);
            for (String m : members) if (!m.equals(keep)) chainsToRemove.add(m);
        }

        // Interface scoring
        List<Group> targetRes = chainProteinResidues(targetChain);
        List<Atom> partnerAtoms = heavyAtomsOfChain(partnerChain);

        List<Map.Entry<Group, Double>> scored = new ArrayList<>();
        for (Group r : targetRes) {
            List<Atom> atoms = heavyAtomsOfGroup(r);
            int contactCount = 0;
            for (Atom a : atoms) {
                for (Atom pb : partnerAtoms) {
                    if (distance(a, pb) <= interfaceCutoff) contactCount++;
                }
            }
            char aa1 = AA3_TO_1.getOrDefault(((AminoAcid) r).getPDBName(), 'X');
            double bonus = FAVORABLE_HOTSPOT_AA.contains(aa1) ? 0.15 : 0.0;
            scored.add(new AbstractMap.SimpleEntry<>(r, contactCount + bonus));
        }
        scored.sort((x, y) -> Double.compare(y.getValue(), x.getValue()));

        List<Group> hotspotsRes = pickHotspotsWithSpacing(scored, maxHotspots, 6.0);
        String hotspots = formatHotspots(targetChainId, hotspotsRes);
        String contig = contigFromHotspots(targetChainId, hotspotsRes, bufferRes, binderMin, binderMax);

        return new Suggestion(contig, hotspots, chainsToRemove.stream().sorted().collect(Collectors.toList()));
    }

    // ---------------- Target-only mode ----------------

    private Suggestion suggestFromTargetOnly(Structure structure, String targetChainId) {
        List<Chain> chains = structure.getChains();
        List<Chain> proteinChains = chains.stream()
                .filter(c -> chainProteinResidues(c).size() >= 30)
                .collect(Collectors.toList());

        if (proteinChains.isEmpty()) {
            throw new IllegalArgumentException("No protein chains (>=30 residues) found in target PDB.");
        }

        if (!StringUtils.hasText(targetChainId)) {
            targetChainId = chainId(chooseBestChainByCompleteness(proteinChains));
        }

        Chain targetChain = getChainById(structure, targetChainId);

        // Duplicates
        Map<String, List<String>> dupGroups = findDuplicateChains(proteinChains, duplicateIdentityThreshold);
        Set<String> chainsToRemove = new HashSet<>();
        for (List<String> members : dupGroups.values()) {
            if (members.size() <= 1) continue;
            String keep = members.contains(targetChainId) ? targetChainId : members.get(0);
            for (String m : members) if (!m.equals(keep)) chainsToRemove.add(m);
        }

        List<Group> targetRes = chainProteinResidues(targetChain);
        List<Atom> caAtoms = targetRes.stream()
                .map(g -> ((AminoAcid) g).getCA())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // Exposure scoring
        List<Map.Entry<Group, Double>> exposureScores = new ArrayList<>();
        for (Group r : targetRes) {
            Atom ca = ((AminoAcid) r).getCA();
            if (ca == null) continue;

            int neighborCount = 0;
            for (Atom other : caAtoms) if (distance(ca, other) <= exposureRadius) neighborCount++;

            char aa1 = AA3_TO_1.getOrDefault(((AminoAcid) r).getPDBName(), 'X');
            double aaBonus = FAVORABLE_HOTSPOT_AA.contains(aa1) ? 0.2 : 0.0;

            double score = (-neighborCount) + aaBonus;
            exposureScores.add(new AbstractMap.SimpleEntry<>(r, score));
        }
        exposureScores.sort((x, y) -> Double.compare(y.getValue(), x.getValue()));

        int k = Math.max(10, (int) (exposureScores.size() * topExposedFraction));
        k = Math.min(k, exposureScores.size());

        List<Group> exposed = exposureScores.subList(0, k).stream()
                .map(Map.Entry::getKey).collect(Collectors.toList());

        List<List<Group>> patches = clusterPatches(exposed, patchLinkRadius);

        Map<Group, Double> expMapTopK = new HashMap<>();
        for (int i = 0; i < k; i++) expMapTopK.put(exposureScores.get(i).getKey(), exposureScores.get(i).getValue());

        patches.sort((p1, p2) -> Double.compare(patchScore(p2, expMapTopK), patchScore(p1, expMapTopK)));
        List<Group> bestPatch = !patches.isEmpty() ? patches.get(0) : exposed;

        Map<Group, Double> expMapAll = new HashMap<>();
        for (Map.Entry<Group, Double> e : exposureScores) expMapAll.put(e.getKey(), e.getValue());

        List<Map.Entry<Group, Double>> scoredPatch = new ArrayList<>();
        for (Group r : bestPatch) scoredPatch.add(new AbstractMap.SimpleEntry<>(r, expMapAll.getOrDefault(r, 0.0)));
        scoredPatch.sort((x, y) -> Double.compare(y.getValue(), x.getValue()));

        List<Group> hotspotsRes = pickHotspotsWithSpacing(scoredPatch, maxHotspots, 6.0);
        String hotspots = formatHotspots(targetChainId, hotspotsRes);
        String contig = contigFromHotspots(targetChainId, hotspotsRes, bufferRes, binderMin, binderMax);

        return new Suggestion(contig, hotspots, chainsToRemove.stream().sorted().collect(Collectors.toList()));
    }

    // ---------------- Patch clustering helpers ----------------

    private List<List<Group>> clusterPatches(List<Group> exposed, double linkRadius) {
        Set<Group> visited = new HashSet<>();
        List<List<Group>> patches = new ArrayList<>();

        for (Group start : exposed) {
            if (visited.contains(start)) continue;
            List<Group> patch = new ArrayList<>();
            Deque<Group> q = new ArrayDeque<>();
            q.add(start);
            visited.add(start);

            while (!q.isEmpty()) {
                Group cur = q.removeFirst();
                patch.add(cur);
                Atom curCA = ((AminoAcid) cur).getCA();
                if (curCA == null) continue;

                for (Group rr : exposed) {
                    if (visited.contains(rr)) continue;
                    Atom rrCA = ((AminoAcid) rr).getCA();
                    if (rrCA == null) continue;
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
            if (FAVORABLE_HOTSPOT_AA.contains(aa1)) fav++;
            expSum += expMapTopK.getOrDefault(r, 0.0);
        }
        double avgExp = expSum / Math.max(1, size);
        return (size * 1.0) + (fav * 0.6) + (avgExp * 0.8);
    }

    // ---------------- General helpers ----------------

    private static String chainId(Chain c) {
        String id = c.getId();
        if (id != null && !id.isBlank()) return id.trim();
        return c.getName() != null ? c.getName().trim() : "?";
    }

    private static Chain getChainById(Structure s, String id) {
        for (Chain c : s.getChains()) {
            if (chainId(c).equals(id)) return c;
        }
        throw new IllegalArgumentException("Chain not found: " + id);
    }

    private static List<Group> chainProteinResidues(Chain chain) {
        List<Group> out = new ArrayList<>();
        for (Group g : chain.getAtomGroups()) if (isProteinResidue(g)) out.add(g);
        return out;
    }

    private static boolean isProteinResidue(Group g) {
        if (!(g instanceof AminoAcid)) return false;
        AminoAcid aa = (AminoAcid) g;
        String resName = aa.getPDBName();
        if (!AA3_TO_1.containsKey(resName)) return false;
        return aa.getCA() != null;
    }

    private static Chain chooseBestChainByCompleteness(List<Chain> chains) {
        Chain best = null;
        int bestN = -1;
        for (Chain c : chains) {
            int n = chainProteinResidues(c).size();
            if (n > bestN) { bestN = n; best = c; }
        }
        if (best == null) throw new IllegalStateException("No chains to choose from.");
        return best;
    }

    private static String chainSequence(Chain chain) {
        StringBuilder sb = new StringBuilder();
        for (Group g : chain.getAtomGroups()) {
            if (isProteinResidue(g)) sb.append(AA3_TO_1.get(((AminoAcid) g).getPDBName()));
        }
        return sb.toString();
    }

    private static double seqSimilarity(String a, String b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return 0.0;
        int n = Math.min(a.length(), b.length());
        int matches = 0;
        for (int i = 0; i < n; i++) if (a.charAt(i) == b.charAt(i)) matches++;
        return (double) matches / (double) n;
    }

    private static Map<String, List<String>> findDuplicateChains(List<Chain> chains, double identityThreshold) {
        Map<String, String> seqs = new HashMap<>();
        for (Chain c : chains) seqs.put(chainId(c), chainSequence(c));

        Set<String> used = new HashSet<>();
        Map<String, List<String>> groups = new LinkedHashMap<>();

        for (Chain c : chains) {
            String id = chainId(c);
            if (used.contains(id)) continue;
            used.add(id);

            List<String> group = new ArrayList<>();
            group.add(id);

            for (Chain other : chains) {
                String oid = chainId(other);
                if (used.contains(oid)) continue;

                double sim = seqSimilarity(seqs.get(id), seqs.get(oid));
                int lenDiff = Math.abs(seqs.get(id).length() - seqs.get(oid).length());

                if (sim >= identityThreshold && lenDiff <= 5) {
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
        if (e != null) return e != Element.H;
        String name = a.getName();
        return name == null || !name.startsWith("H");
    }

    private static List<Atom> heavyAtomsOfGroup(Group g) {
        List<Atom> atoms = new ArrayList<>();
        for (Atom a : g.getAtoms()) if (isHeavyAtom(a)) atoms.add(a);
        return atoms;
    }

    private static List<Atom> heavyAtomsOfChain(Chain c) {
        List<Atom> atoms = new ArrayList<>();
        for (Group g : c.getAtomGroups()) atoms.addAll(heavyAtomsOfGroup(g));
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
            double minCaSeparation
    ) {
        List<Group> chosen = new ArrayList<>();
        for (Map.Entry<Group, Double> e : residuesScored) {
            if (chosen.size() >= maxHotspots) break;
            Group r = e.getKey();
            if (!isProteinResidue(r)) continue;

            boolean ok = true;
            for (Group c : chosen) {
                if (caDistance(r, c) < minCaSeparation) { ok = false; break; }
            }
            if (ok) chosen.add(r);
        }
        return chosen;
    }

    private static String formatHotspots(String chainId, List<Group> residues) {
        TreeSet<Integer> nums = new TreeSet<>();
        for (Group g : residues) {
            ResidueNumber rn = g.getResidueNumber();
            int n = rn != null ? rn.getSeqNum() : -1;
            if (n > 0) nums.add(n);
        }
        return nums.stream().map(n -> chainId + n).collect(Collectors.joining(","));
    }

    private static String contigFromHotspots(String chainId, List<Group> hotspots,
                                             int bufferRes, int binderMin, int binderMax) {
        List<Integer> nums = hotspots.stream()
                .map(g -> g.getResidueNumber() != null ? g.getResidueNumber().getSeqNum() : -1)
                .filter(n -> n > 0)
                .sorted()
                .collect(Collectors.toList());

        int start, end;
        if (nums.isEmpty()) {
            start = 20; end = 130;
        } else {
            start = Math.max(1, nums.get(0) - bufferRes);
            end = nums.get(nums.size() - 1) + bufferRes;
        }
        return String.format("%d-%d/0 %s%d-%d", binderMin, binderMax, chainId, start, end);
    }
}