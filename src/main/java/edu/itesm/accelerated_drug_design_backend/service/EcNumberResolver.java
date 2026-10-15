package edu.itesm.accelerated_drug_design_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Resolves an enzyme commission number for a PDB target.
 * Data API first, COMPND text as fallback. Failures become {@link Optional#empty()}.
 */
@Service
public class EcNumberResolver {

	private static final Logger log = LoggerFactory.getLogger(EcNumberResolver.class);

	private static final int HEADER_ID_START = 62;
	private static final int HEADER_ID_END = 66;
	private static final int EC_LINEAGE_MAX_LENGTH = 255;
	private static final String EMPTY_EC = "-.-.-.-";
	private static final Pattern PDB_ID = Pattern.compile("[A-Za-z0-9]{4}");
	private static final Pattern SAFE_PDB_ID = Pattern.compile("[A-Za-z0-9]+");
	private static final Pattern FOUR_LEVEL_EC = Pattern.compile("^(?:\\d+|-)\\.(?:\\d+|-)\\.(?:\\d+|-)\\.(?:\\d+|-)$");

	private final RestTemplate rcsbDataRestTemplate;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public EcNumberResolver(@Qualifier("rcsbDataRestTemplate") RestTemplate rcsbDataRestTemplate) {
		this.rcsbDataRestTemplate = rcsbDataRestTemplate;
	}

	public record EcInfo(String ecNumber, String ecLineage) {}

	public Optional<EcInfo> resolve(String pdbId, String pdbContent) {
		try {
			if (pdbId != null && !pdbId.isBlank()) {
				Optional<EcInfo> fromApi = queryDataApi(pdbId.strip());
				if (fromApi.isPresent()) {
					return fromApi;
				}
			}
			if (pdbContent != null) {
				return fromCompnd(pdbContent);
			}
			return Optional.empty();
		} catch (Exception e) {
			log.info("EC resolution failed for pdbId {}: {}", pdbId, e.toString());
			return Optional.empty();
		}
	}

	public Optional<String> extractPdbIdFromHeader(String pdbContent) {
		try {
			if (pdbContent == null || pdbContent.isBlank()) {
				return Optional.empty();
			}
			for (String line : pdbContent.split("\\R")) {
				if (!line.startsWith("HEADER")) {
					continue;
				}
				if (line.length() < HEADER_ID_END) {
					return Optional.empty();
				}
				String id = line.substring(HEADER_ID_START, HEADER_ID_END);
				if (!PDB_ID.matcher(id).matches()) {
					return Optional.empty();
				}
				return Optional.of(id.toUpperCase(Locale.ROOT));
			}
			return Optional.empty();
		} catch (Exception e) {
			log.info("Could not read PDB ID from HEADER: {}", e.toString());
			return Optional.empty();
		}
	}

	private Optional<EcInfo> queryDataApi(String pdbId) {
		if (!SAFE_PDB_ID.matcher(pdbId).matches()) {
			log.debug("Skipping RCSB Data API for unsafe PDB ID: {}", pdbId);
			return Optional.empty();
		}
		String url = ProjectService.RCSB_POLYMER_ENTITY_URL.formatted(pdbId);
		try {
			String body = rcsbDataRestTemplate.getForObject(url, String.class);
			if (body == null || body.isBlank()) {
				log.debug("RCSB Data API returned an empty body for {}", pdbId);
				return Optional.empty();
			}
			Optional<EcInfo> parsed = parsePolymerEntity(body);
			if (parsed.isEmpty()) {
				log.debug("RCSB Data API response for {} has no EC number", pdbId);
			}
			return parsed;
		} catch (HttpStatusCodeException e) {
			HttpStatusCode status = e.getStatusCode();
			log.info("RCSB Data API returned HTTP {} for {}: {}", status.value(), pdbId, e.getStatusText());
			return Optional.empty();
		} catch (ResourceAccessException e) {
			log.info("RCSB Data API timed out or was unreachable for {}: {}", pdbId, e.getMessage());
			return Optional.empty();
		} catch (RestClientException e) {
			log.info("RCSB Data API call failed for {}: {}", pdbId, e.getMessage());
			return Optional.empty();
		} catch (Exception e) {
			log.info("Could not parse RCSB Data API response for {}: {}", pdbId, e.toString());
			return Optional.empty();
		}
	}

	private Optional<EcInfo> parsePolymerEntity(String json) throws Exception {
		JsonNode entity = objectMapper.readTree(json).path("rcsb_polymer_entity");
		if (entity.isMissingNode() || entity.isNull()) {
			return Optional.empty();
		}
		List<String> numbers = new ArrayList<>();
		addEcValues(numbers, entity.get("rcsb_ec_number"));
		if (numbers.isEmpty()) {
			addEcValues(numbers, entity.get("pdbx_ec"));
		}
		if (numbers.isEmpty()) {
			addLineageEcNumbers(numbers, entity.path("rcsb_ec_lineage"));
		}
		if (numbers.isEmpty()) {
			JsonNode combined = entity.path("rcsb_enzyme_class_combined");
			if (combined.isArray()) {
				for (JsonNode item : combined) {
					addEcValues(numbers, item.get("ec"));
				}
			}
		}
		if (numbers.isEmpty()) {
			return Optional.empty();
		}
		if (numbers.size() > 1) {
			log.info("RCSB Data API returned {} EC numbers; using the first ({})", numbers.size(), numbers.get(0));
		}
		return Optional.of(new EcInfo(numbers.get(0), formatLineage(entity.path("rcsb_ec_lineage"))));
	}

	private void addLineageEcNumbers(List<String> out, JsonNode lineage) {
		if (!lineage.isArray()) {
			return;
		}
		List<JsonNode> nodes = new ArrayList<>();
		lineage.forEach(nodes::add);
		nodes.sort(Comparator.comparingInt((JsonNode n) -> n.path("depth").asInt(0)).reversed());
		for (JsonNode node : nodes) {
			addEcValues(out, node.get("rcsb_ec_number"));
			addEcValues(out, node.get("id"));
		}
	}

	private void addEcValues(List<String> out, JsonNode node) {
		if (node == null || node.isNull() || node.isMissingNode()) {
			return;
		}
		if (node.isArray()) {
			for (JsonNode item : node) {
				addEcValues(out, item);
			}
			return;
		}
		addEcToken(out, node.asText(""));
	}

	private void addEcToken(List<String> out, String raw) {
		if (raw == null || raw.isBlank()) {
			return;
		}
		for (String part : raw.split("[,;]")) {
			String token = part.strip();
			if (isAcceptableEc(token) && !out.contains(token)) {
				out.add(token);
			}
		}
	}

	private static String formatLineage(JsonNode lineage) {
		if (!lineage.isArray() || lineage.isEmpty()) {
			return null;
		}
		List<JsonNode> nodes = new ArrayList<>();
		lineage.forEach(nodes::add);
		nodes.sort(Comparator.comparingInt(n -> n.path("depth").asInt(0)));
		String joined = nodes.stream()
				.map(EcNumberResolver::lineageLabel)
				.filter(label -> !label.isBlank())
				.reduce((a, b) -> a + " > " + b)
				.orElse("");
		if (joined.isBlank()) {
			return null;
		}
		if (joined.length() <= EC_LINEAGE_MAX_LENGTH) {
			return joined;
		}
		return joined.substring(0, EC_LINEAGE_MAX_LENGTH);
	}

	private static String lineageLabel(JsonNode node) {
		String name = node.path("name").asText("");
		if (!name.isBlank()) {
			return name;
		}
		return node.path("id").asText("");
	}

	private Optional<EcInfo> fromCompnd(String pdbContent) {
		List<String> found = new ArrayList<>();
		for (String line : pdbContent.split("\\R")) {
			if (!line.startsWith("COMPND")) {
				continue;
			}
			int searchFrom = 0;
			while (searchFrom < line.length()) {
				int token = line.indexOf("EC:", searchFrom);
				if (token < 0) {
					break;
				}
				int valueStart = token + "EC:".length();
				int semicolon = line.indexOf(';', valueStart);
				String segment = semicolon >= 0 ? line.substring(valueStart, semicolon) : line.substring(valueStart);
				addEcToken(found, segment);
				searchFrom = valueStart;
			}
		}
		if (found.isEmpty()) {
			return Optional.empty();
		}
		if (found.size() > 1) {
			log.info("COMPND lists {} EC numbers; using the first ({})", found.size(), found.get(0));
		}
		return Optional.of(new EcInfo(found.get(0), null));
	}

	private static boolean isAcceptableEc(String token) {
		return !EMPTY_EC.equals(token) && FOUR_LEVEL_EC.matcher(token).matches();
	}
}
