package edu.itesm.accelerated_drug_design_backend.dto;

/**
 * Tunable RFdiffusion parameter generation options (per request).
 */
public record RfdiffusionParamOptions(
		int binderMin,
		int binderMax,
		int maxHotspots,
		Double interfaceCutoff,
		Double exposureRadius,
		Double patchLinkRadius,
		Double topExposedFraction,
		Double duplicateIdentityThreshold
) {
	public static final int DEFAULT_BINDER_MIN = 12;
	public static final int DEFAULT_BINDER_MAX = 30;
	public static final int DEFAULT_MAX_HOTSPOTS = 7;

	public RfdiffusionParamOptions {
		if (binderMin < 1) {
			throw new IllegalArgumentException("binderMin must be >= 1");
		}
		if (binderMax < binderMin) {
			throw new IllegalArgumentException("binderMax must be >= binderMin");
		}
		if (maxHotspots < 1) {
			throw new IllegalArgumentException("maxHotspots must be >= 1");
		}
	}

	public static RfdiffusionParamOptions defaults() {
		return new RfdiffusionParamOptions(
				DEFAULT_BINDER_MIN,
				DEFAULT_BINDER_MAX,
				DEFAULT_MAX_HOTSPOTS,
				null, null, null, null, null
		);
	}

	public static RfdiffusionParamOptions forLengthRange(int minLength, int maxLength, int maxHotspots) {
		return new RfdiffusionParamOptions(minLength, maxLength, maxHotspots, null, null, null, null, null);
	}

	public int binderMinEffective() {
		return binderMin;
	}

	public int binderMaxEffective() {
		return binderMax;
	}

	public int maxHotspotsEffective() {
		return maxHotspots;
	}
}
