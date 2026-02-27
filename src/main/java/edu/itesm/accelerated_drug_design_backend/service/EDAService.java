package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.dto.BinsRequest;
import edu.itesm.accelerated_drug_design_backend.dto.BinsResponse;
import edu.itesm.accelerated_drug_design_backend.dto.DescriptiveStatsDto;
import edu.itesm.accelerated_drug_design_backend.dto.DistributionResponse;
import edu.itesm.accelerated_drug_design_backend.repository.GenerationJobRecordRepository;
import edu.itesm.accelerated_drug_design_backend.repository.GenerationJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class EDAService {

	public static final List<String> VALID_METRICS = List.of("mpnn", "plddt", "ptm", "i_ptm", "pae", "i_pae", "rmsd");

	private final GenerationJobRepository generationJobRepository;
	private final GenerationJobRecordRepository generationJobRecordRepository;

	public EDAService(GenerationJobRepository generationJobRepository,
					  GenerationJobRecordRepository generationJobRecordRepository) {
		this.generationJobRepository = generationJobRepository;
		this.generationJobRecordRepository = generationJobRecordRepository;
	}

	@Transactional(readOnly = true)
	public DistributionResponse getDistribution(Long projectId, Long jobId, String metric) {
		validateJobBelongsToProject(projectId, jobId);
		List<Double> values = getNumericValues(projectId, jobId, metric);
		if (values.isEmpty()) {
			return new DistributionResponse(metric, List.of(), null, null);
		}
		double min = values.stream().mapToDouble(Double::doubleValue).min().orElseThrow();
		double max = values.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
		return new DistributionResponse(metric, values, min, max);
	}

	@Transactional(readOnly = true)
	public BinsResponse getBins(Long projectId, Long jobId, String metric, BinsRequest request) {
		validateJobBelongsToProject(projectId, jobId);
		List<Double> values = getNumericValues(projectId, jobId, metric);
		List<Double> bins = request.bins();
		List<String> labels = request.labels();
		if (labels.size() != bins.size() - 1) {
			throw new IllegalArgumentException("labels size must be bins.size() - 1");
		}
		int numCategories = labels.size();
		long[] counts = new long[numCategories];
		for (Double v : values) {
			int idx = binIndex(v, bins, numCategories);
			if (idx >= 0 && idx < numCategories) {
				counts[idx]++;
			}
		}
		int total = values.size();
		List<Long> countList = new ArrayList<>(numCategories);
		List<Double> percentages = new ArrayList<>(numCategories);
		for (long c : counts) {
			countList.add(c);
			percentages.add(total > 0 ? (100.0 * c / total) : 0.0);
		}
		return new BinsResponse(metric, labels, countList, percentages);
	}

	/** Orden de métricas para la tabla de estadísticas descriptivas. */
	private static final List<String> STATS_METRIC_ORDER = List.of("ptm", "i_ptm", "pae", "i_pae", "plddt", "rmsd", "mpnn");

	@Transactional(readOnly = true)
	public Map<String, DescriptiveStatsDto> getDescriptiveStats(Long projectId, Long jobId) {
		validateJobBelongsToProject(projectId, jobId);
		Map<String, DescriptiveStatsDto> result = new LinkedHashMap<>();
		for (String metric : STATS_METRIC_ORDER) {
			List<Double> values = getNumericValues(projectId, jobId, metric);
			result.put(metric, computeDescriptiveStats(values, metric)); // <-- ahora calcula quality
		}
		return result;
	}

	@Transactional(readOnly = true)
	public double getDatasetQuality(Long projectId, Long jobId) {
		validateJobBelongsToProject(projectId, jobId);

		double weightedSum = 0.0;
		double totalWeight = 0.0;

		// Pesos globales (puedes ajustar si quieres)
		Map<String, Double> weights = Map.of(
				"ptm", 0.15,
				"plddt", 0.15,
				"pae", 0.15,
				"rmsd", 0.15,
				"i_ptm", 0.20,
				"i_pae", 0.20,
				"mpnn", 0.00
		);

		for (String metric : STATS_METRIC_ORDER) {

			List<Double> values = getNumericValues(projectId, jobId, metric);
			DescriptiveStatsDto stats = computeDescriptiveStats(values, metric);

			Double quality = stats.quality();
			if (quality == null) continue;

			double w = weights.getOrDefault(metric, 0.0);
			if (w <= 0.0) continue;

			weightedSum += w * quality;
			totalWeight += w;
		}

		if (totalWeight == 0.0) return 0.0;

		return (weightedSum / totalWeight) / 100;
	}

	// -------------------- Quality algorithm --------------------

	private enum Direction { HIGHER_BETTER, LOWER_BETTER }

	/**
	 * Ajusta MPNN según el significado real de tu score:
	 * - si es energy/neg loglik => LOWER_BETTER (default aquí)
	 * - si es score donde alto es mejor => HIGHER_BETTER
	 */
	private static Direction directionForMetric(String metric) {
		return switch (metric) {
			case "ptm", "i_ptm", "plddt" -> Direction.HIGHER_BETTER;
			case "pae", "i_pae", "rmsd" -> Direction.LOWER_BETTER;
			case "mpnn" -> Direction.LOWER_BETTER; // <- cambia si aplica
			default -> throw new IllegalArgumentException("Unknown metric: " + metric);
		};
	}

	// Pesos internos del índice por columna
	private static final double W_PERF = 0.60;
	private static final double W_STAB = 0.25;
	private static final double W_SHAPE = 0.15;

	// Parámetros suavizadores
	private static final double TAU_CV = 0.25;  // mayor => castigo CV más suave
	private static final double S0_SKEW = 1.0;  // mayor => castigo skew más suave
	private static final double K0_KURT = 2.0;  // mayor => castigo kurtosis más suave (kurtosis aquí es EXCESS: ideal 0)

	private static double computeQualityIndex(String metric, DescriptiveStatsDto s) {
		if (s == null || s.mean() == null || s.min() == null || s.max() == null) return 0.0;

		Direction dir = directionForMetric(metric);

		double mean = s.mean();
		Double median = s.p50();
		double min = s.min();
		double max = s.max();

		// Centro robusto (si hay mediana)
		double center = (median != null) ? (0.7 * median + 0.3 * mean) : mean;

		// A) desempeño relativo dentro del rango observado
		double sPerf;
		if (Double.compare(max, min) == 0) {
			sPerf = 1.0;
		} else {
			double range = max - min;
			if (Math.abs(range) < 1e-12) range = 1e-12;
			sPerf = (dir == Direction.HIGHER_BETTER)
					? clamp01((center - min) / range)
					: clamp01((max - center) / range);
		}

		// B) estabilidad por CV = std / |mean|
		double cv = computeCv(s.std(), mean);
		double sStab = clamp01(Math.exp(-(cv / TAU_CV)));

		// C) forma: skew ~ 0, excess kurtosis ~ 0
		double skew = (s.skew() != null) ? s.skew() : 0.0;
		double kurtExcess = (s.kurtosis() != null) ? s.kurtosis() : 0.0;

		double sSkew = clamp01(Math.exp(-(Math.abs(skew) / S0_SKEW)));
		double sKurt = clamp01(Math.exp(-(Math.abs(kurtExcess) / K0_KURT)));
		double sShape = clamp01(Math.sqrt(sSkew * sKurt));

		double q = 100.0 * (W_PERF * sPerf + W_STAB * sStab + W_SHAPE * sShape);
		return clamp(q, 0.0, 100.0);
	}

	private static double computeCv(Double std, double mean) {
		if (std == null) return 10.0;
		double absMean = Math.abs(mean);
		if (absMean < 1e-12) return 10.0;
		return std / absMean;
	}

	private static double clamp01(double x) { return clamp(x, 0.0, 1.0); }

	private static double clamp(double x, double lo, double hi) {
		if (x < lo) return lo;
		if (x > hi) return hi;
		return x;
	}

	// -------------------- Stats computation (ahora añade quality) --------------------

	private static DescriptiveStatsDto computeDescriptiveStats(List<Double> values, String metric) {
		if (values == null || values.isEmpty()) {
			return new DescriptiveStatsDto(null, null, null, null, null, null, null, null, null, null);
		}

		List<Double> sorted = new ArrayList<>(values);
		sorted.sort(Double::compareTo);
		int n = sorted.size();

		double mean = sorted.stream().mapToDouble(Double::doubleValue).sum() / n;
		double variance = sorted.stream().mapToDouble(v -> (v - mean) * (v - mean)).sum() / n;
		double std = Math.sqrt(variance);

		double min = sorted.get(0);
		double max = sorted.get(n - 1);

		double p25 = percentile(sorted, 25);
		double p50 = percentile(sorted, 50);
		double p75 = percentile(sorted, 75);

		double skew = std > 0 ? sorted.stream().mapToDouble(v -> Math.pow((v - mean) / std, 3)).sum() / n : Double.NaN;

		// Excess kurtosis (ideal 0.0)
		double kurtosis = std > 0 ? sorted.stream().mapToDouble(v -> Math.pow((v - mean) / std, 4)).sum() / n - 3.0 : Double.NaN;

		DescriptiveStatsDto base = new DescriptiveStatsDto(
				mean,
				std > 0 ? std : null,
				min,
				p25,
				p50,
				p75,
				max,
				!Double.isNaN(skew) ? skew : null,
				!Double.isNaN(kurtosis) ? kurtosis : null,
				null
		);

		double quality = computeQualityIndex(metric, base);

		return new DescriptiveStatsDto(
				base.mean(),
				base.std(),
				base.min(),
				base.p25(),
				base.p50(),
				base.p75(),
				base.max(),
				base.skew(),
				base.kurtosis(),
				quality
		);
	}

	private static double percentile(List<Double> sorted, double p) {
		int n = sorted.size();
		if (n == 1) return sorted.get(0);
		double index = p / 100.0 * (n - 1);
		int lo = (int) Math.floor(index);
		int hi = Math.min(lo + 1, n - 1);
		double w = index - lo;
		return sorted.get(lo) * (1 - w) + sorted.get(hi) * w;
	}

	private void validateJobBelongsToProject(Long projectId, Long jobId) {
		if (generationJobRepository.findDetailByProjectIdAndJobId(projectId, jobId).isEmpty()) {
			throw new jakarta.persistence.EntityNotFoundException("Generation job not found or does not belong to project");
		}
	}

	private List<Double> getNumericValues(Long projectId, Long jobId, String metric) {
		List<Double> raw = switch (metric) {
			case "plddt" -> generationJobRecordRepository.findPlddtByProjectIdAndJobId(projectId, jobId);
			case "ptm" -> generationJobRecordRepository.findPtmByProjectIdAndJobId(projectId, jobId);
			case "i_ptm" -> generationJobRecordRepository.findIPtmByProjectIdAndJobId(projectId, jobId);
			case "rmsd" -> generationJobRecordRepository.findRmsdByProjectIdAndJobId(projectId, jobId);
			case "mpnn" -> parseStringsToDoubles(generationJobRecordRepository.findMpnnByProjectIdAndJobId(projectId, jobId));
			case "pae" -> parseStringsToDoubles(generationJobRecordRepository.findPaeByProjectIdAndJobId(projectId, jobId));
			case "i_pae" -> parseStringsToDoubles(generationJobRecordRepository.findIPaeByProjectIdAndJobId(projectId, jobId));
			default -> throw new IllegalArgumentException("Invalid metric: " + metric + ". Valid: " + VALID_METRICS);
		};
		if (raw == null) return List.of();
		return raw.stream().filter(v -> v != null && !Double.isNaN(v)).toList();
	}

	private List<Double> parseStringsToDoubles(List<String> strings) {
		if (strings == null) return List.of();
		List<Double> result = new ArrayList<>();
		for (String s : strings) {
			if (s == null || s.isBlank()) continue;
			try {
				result.add(Double.parseDouble(s.trim()));
			} catch (NumberFormatException ignored) {
				// skip non-numeric
			}
		}
		return result;
	}

	private int binIndex(double value, List<Double> bins, int numCategories) {
		for (int i = 0; i < numCategories; i++) {
			double low = bins.get(i);
			double high = bins.get(i + 1);
			if (i == numCategories - 1) {
				if (value >= low && value <= high) return i;
			} else {
				if (value >= low && value < high) return i;
			}
		}
		return -1;
	}
}