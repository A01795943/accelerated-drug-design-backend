package edu.itesm.accelerated_drug_design_backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.itesm.accelerated_drug_design_backend.dto.*;
import edu.itesm.accelerated_drug_design_backend.entity.*;
import edu.itesm.accelerated_drug_design_backend.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DataGenerationCampaignService {

	private static final Logger log = LoggerFactory.getLogger(DataGenerationCampaignService.class);
	private static final int MAX_BACKBONES_PER_CONFIG = 20;

	private final DataGenerationCampaignRepository campaignRepository;
	private final CampaignBackboneRepository campaignBackboneRepository;
	private final CampaignTemperatureJobRepository campaignTemperatureJobRepository;
	private final ProjectService projectService;
	private final BackboneService backboneService;
	private final GenerationJobService generationJobService;
	private final BackboneRepository backboneRepository;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public DataGenerationCampaignService(
			DataGenerationCampaignRepository campaignRepository,
			CampaignBackboneRepository campaignBackboneRepository,
			CampaignTemperatureJobRepository campaignTemperatureJobRepository,
			ProjectService projectService,
			BackboneService backboneService,
			GenerationJobService generationJobService,
			BackboneRepository backboneRepository) {
		this.campaignRepository = campaignRepository;
		this.campaignBackboneRepository = campaignBackboneRepository;
		this.campaignTemperatureJobRepository = campaignTemperatureJobRepository;
		this.projectService = projectService;
		this.backboneService = backboneService;
		this.generationJobService = generationJobService;
		this.backboneRepository = backboneRepository;
	}

	@Transactional
	public CreateCampaignResponse createCampaign(Long projectId, CreateDataGenerationCampaignRequest request) {
		projectService.findEntityById(projectId);
		validateTemperatures(request.getTemperatures());

		Instant now = Instant.now();
		DataGenerationCampaign campaign = new DataGenerationCampaign();
		campaign.setProject(projectService.findEntityById(projectId));
		campaign.setStatus(DataGenerationCampaign.STATUS_RUNNING);
		campaign.setCreatedAt(now);
		campaign.setNumSeqsPerTemperature(request.getNumSeqsPerTemperature());
		campaign.setTemperaturesJson(writeTemperaturesJson(request.getTemperatures()));
		campaign.setBackboneConfigsJson(writeBackboneConfigsJson(request.getBackboneConfigs()));

		int sortOrder = 0;
		int configIndex = 0;
		for (CreateDataGenerationCampaignRequest.BackboneConfigDto cfg : request.getBackboneConfigs()) {
			int numBackbones = resolveNumBackbones(cfg);
			for (int i = 0; i < numBackbones; i++) {
				CampaignBackbone cb = new CampaignBackbone();
				cb.setCampaign(campaign);
				cb.setConfigIndex(configIndex);
				cb.setSortOrder(sortOrder++);
				cb.setContigs(cfg.getContigs());
				cb.setHotspots(cfg.getHotspots());
				cb.setChainsToRemove(cfg.getChainsToRemove());
				cb.setIterations(cfg.getIterations() != null ? cfg.getIterations() : 30);
				cb.setStatus(CampaignBackbone.STATUS_PENDING);
				cb.setCreatedAt(now);
				cb.setUpdatedAt(now);
				campaign.getBackbones().add(cb);

				for (Double temp : request.getTemperatures()) {
					CampaignTemperatureJob tj = new CampaignTemperatureJob();
					tj.setCampaignBackbone(cb);
					tj.setTemperature(temp);
					tj.setStatus(CampaignTemperatureJob.STATUS_PENDING);
					cb.getTemperatureJobs().add(tj);
				}
			}
			configIndex++;
		}

		campaign = campaignRepository.save(campaign);
		return new CreateCampaignResponse(campaign.getId());
	}

	@Transactional(readOnly = true)
	public DataGenerationCampaignDetailDto getCampaignDetail(Long projectId, Long campaignId) {
		DataGenerationCampaign campaign = campaignRepository.findByIdAndProject_Id(campaignId, projectId)
				.orElseThrow(() -> new EntityNotFoundException("Campaign not found: " + campaignId));

		List<CampaignBackbone> backbones = campaignBackboneRepository.findByCampaign_IdOrderBySortOrderAsc(campaignId);
		List<CreateDataGenerationCampaignRequest.BackboneConfigDto> storedConfigs =
				readBackboneConfigsJson(campaign.getBackboneConfigsJson());

		DataGenerationCampaignDetailDto dto = new DataGenerationCampaignDetailDto();
		dto.setId(campaign.getId());
		dto.setStatus(campaign.getStatus());
		dto.setNumSeqsPerTemperature(campaign.getNumSeqsPerTemperature());
		dto.setTemperatures(readTemperaturesJson(campaign.getTemperaturesJson()));
		dto.setBackboneConfigs(buildConfigProgressDtos(backbones, storedConfigs));
		return dto;
	}

	/**
	 * Lists campaign summaries for a project with progress metrics.
	 * <p>
	 * Progress counts are loaded in one JPQL query ({@link DataGenerationCampaignRepository#aggregateProgressByProjectId})
	 * with {@code COUNT} + {@code GROUP BY campaign id}, instead of four separate count queries per campaign (N+1).
	 * Campaign metadata is loaded in a second query ordered by {@code createdAt DESC} and joined in memory.
	 */
	@Transactional(readOnly = true)
	public List<DataGenerationCampaignSummaryDto> listSummariesForProject(Long projectId) {
		projectService.findEntityById(projectId);

		List<DataGenerationCampaign> campaigns = campaignRepository.findByProject_IdOrderByCreatedAtDesc(projectId);
		Map<Long, CampaignProgressAggregate> progressByCampaignId = campaignRepository
				.aggregateProgressByProjectId(projectId, CampaignBackbone.STATUS_COMPLETED)
				.stream()
				.collect(Collectors.toMap(CampaignProgressAggregate::campaignId, Function.identity()));

		return campaigns.stream()
				.map(c -> toSummaryDto(c, progressByCampaignId.get(c.getId())))
				.toList();
	}

	/**
	 * Progress steps count physical rows: each RFdiffusion backbone plus each MPNN+AF
	 * temperature job ({@code count(campaign_backbones) + count(campaign_temperature_jobs)}),
	 * which equals {@code SUM(numBackbones) + SUM(numBackbones × |temperatures|)} for a
	 * newly created campaign.
	 */
	private static DataGenerationCampaignSummaryDto toSummaryDto(
			DataGenerationCampaign campaign,
			CampaignProgressAggregate progress) {
		int totalBackbones = progress != null ? (int) progress.totalBackbones() : 0;
		int completedBackbones = progress != null ? (int) progress.completedBackbones() : 0;
		int totalTemperatureJobs = progress != null ? (int) progress.totalTemperatureJobs() : 0;
		int completedTemperatureJobs = progress != null ? (int) progress.completedTemperatureJobs() : 0;
		int totalSteps = totalBackbones + totalTemperatureJobs;
		int completedSteps = completedBackbones + completedTemperatureJobs;
		Instant updatedAt = campaign.getCompletedAt() != null ? campaign.getCompletedAt() : campaign.getCreatedAt();
		return new DataGenerationCampaignSummaryDto(
				campaign.getId(),
				null,
				campaign.getStatus(),
				campaign.getCreatedAt(),
				updatedAt,
				totalBackbones,
				completedBackbones,
				totalTemperatureJobs,
				completedTemperatureJobs,
				totalSteps,
				completedSteps,
				computeProgressPercent(completedSteps, totalSteps));
	}

	private static double computeProgressPercent(int completedSteps, int totalSteps) {
		if (totalSteps == 0) {
			return 0.0;
		}
		return Math.round(completedSteps * 10000.0 / totalSteps) / 100.0;
	}

	public void planRunningCampaigns() {
		List<DataGenerationCampaign> running = campaignRepository.findByStatus(DataGenerationCampaign.STATUS_RUNNING);
		for (DataGenerationCampaign campaign : running) {
			try {
				planCampaign(campaign.getId());
			} catch (Exception e) {
				log.error("Failed to plan campaign {}: {}", campaign.getId(), e.getMessage(), e);
			}
		}
	}

	@Deprecated
	public void advanceRunningCampaignsOneStep() {
		planRunningCampaigns();
	}

	/**
	 * Campaign planner: sync in-flight units, enqueue every unit that is ready
	 * (respecting backbone → temperature-job dependencies), then recompute
	 * aggregate campaign status. The dispatcher assigns QUEUED runs to instances.
	 */
	public void planCampaign(Long campaignId) {
		DataGenerationCampaign campaign = campaignRepository.findWithProjectById(campaignId)
				.orElseThrow(() -> new EntityNotFoundException("Campaign not found: " + campaignId));
		if (!DataGenerationCampaign.STATUS_RUNNING.equals(campaign.getStatus())) {
			return;
		}

		Long projectId = campaign.getProject().getId();

		for (CampaignBackbone runningBackbone : campaignBackboneRepository
				.findByCampaign_IdAndStatusOrderBySortOrderAsc(campaignId, CampaignBackbone.STATUS_RUNNING)) {
			pollBackbone(projectId, runningBackbone);
		}
		for (CampaignTemperatureJob runningJob : campaignTemperatureJobRepository
				.findAllRunningForCompletedBackbones(campaignId)) {
			pollTemperatureJob(projectId, runningJob);
		}

		for (CampaignTemperatureJob pendingJob : campaignTemperatureJobRepository
				.findAllPendingForCompletedBackbones(campaignId)) {
			startTemperatureJob(projectId, campaign, pendingJob);
		}
		for (CampaignBackbone pendingBackbone : campaignBackboneRepository
				.findByCampaign_IdAndStatusOrderBySortOrderAsc(campaignId, CampaignBackbone.STATUS_PENDING)) {
			try {
				startBackbone(projectId, pendingBackbone);
			} catch (Exception e) {
				log.warn("Failed to enqueue campaign backbone {}: {}", pendingBackbone.getId(), e.getMessage(), e);
				pendingBackbone.setStatus(CampaignBackbone.STATUS_ERROR);
				pendingBackbone.setError(e.getMessage());
				touchBackboneUpdatedAt(pendingBackbone);
				campaignBackboneRepository.save(pendingBackbone);
				skipTemperatureJobsForBackbone(pendingBackbone, "Backbone failed to start: " + e.getMessage());
			}
		}

		refreshCampaignStatus(campaign);
	}

	@Deprecated
	public void advanceCampaignOneStep(Long campaignId) {
		planCampaign(campaignId);
	}

	private void startBackbone(Long projectId, CampaignBackbone cb) {
		CreateBackbonesRequest req = new CreateBackbonesRequest();
		req.setCount(1);
		req.setContigs(cb.getContigs());
		req.setHotspots(cb.getHotspots());
		req.setChainsToRemove(cb.getChainsToRemove());
		req.setIterations(cb.getIterations());

		List<Backbone> created = backboneService.createBackbones(projectId, req);
		Backbone bb = created.get(0);
		cb.setBackboneId(bb.getId());
		cb.setRunId(bb.getRunID());
		cb.setStatus(mapBackboneStatus(bb.getStatus()));
		cb.setError(bb.getError());
		touchBackboneUpdatedAt(cb);
		campaignBackboneRepository.save(cb);
		if (CampaignBackbone.STATUS_ERROR.equals(cb.getStatus())) {
			skipTemperatureJobsForBackbone(cb, "Backbone failed to start: " + (bb.getError() != null ? bb.getError() : "unknown"));
		}
		log.info("Campaign backbone {} started RFdiffusion runId={}", cb.getId(), bb.getRunID());
	}

	private void pollBackbone(Long projectId, CampaignBackbone cb) {
		if (cb.getRunId() == null || cb.getRunId().isBlank()) {
			cb.setStatus(CampaignBackbone.STATUS_ERROR);
			cb.setError("Missing run_id for backbone step");
			touchBackboneUpdatedAt(cb);
			campaignBackboneRepository.save(cb);
			skipTemperatureJobsForBackbone(cb, "Backbone missing run_id");
			return;
		}
		List<Backbone> updated = backboneService.checkRunStatus(projectId, cb.getRunId());
		Backbone bb = updated.stream()
				.filter(b -> cb.getBackboneId() != null && cb.getBackboneId().equals(b.getId()))
				.findFirst()
				.orElse(backboneRepository.findById(cb.getBackboneId()).orElse(null));

		if (bb == null && !updated.isEmpty()) {
			bb = updated.get(0);
		}
		if (bb == null) {
			return;
		}

		cb.setStatus(mapBackboneStatus(bb.getStatus()));
		cb.setError(bb.getError());
		touchBackboneUpdatedAt(cb);
		campaignBackboneRepository.save(cb);

		if (CampaignBackbone.STATUS_ERROR.equals(cb.getStatus())) {
			skipTemperatureJobsForBackbone(cb, "Backbone failed: " + (bb.getError() != null ? bb.getError() : "unknown"));
		}
	}

	private void startTemperatureJob(Long projectId, DataGenerationCampaign campaign, CampaignTemperatureJob tj) {
		CampaignBackbone cb = tj.getCampaignBackbone();
		if (!CampaignBackbone.STATUS_COMPLETED.equals(cb.getStatus())) {
			return;
		}
		if (cb.getBackboneId() == null) {
			tj.setStatus(CampaignTemperatureJob.STATUS_ERROR);
			tj.setError("Backbone id missing");
			campaignTemperatureJobRepository.save(tj);
			return;
		}

		CreateGenerationJobRequest req = new CreateGenerationJobRequest();
		req.setBackboneId(cb.getBackboneId());
		req.setTemperature(tj.getTemperature());
		req.setNumSeqs(campaign.getNumSeqsPerTemperature());

		try {
			GenerationJob job = generationJobService.createJob(projectId, req);
			tj.setGenerationJobId(job.getId());
			tj.setRunId(job.getRunId());
			tj.setStatus(mapJobStatus(job.getStatus()));
			tj.setError(job.getError());
			campaignTemperatureJobRepository.save(tj);
			log.info("Campaign temp job {} started MPNN runId={} temp={}", tj.getId(), job.getRunId(), tj.getTemperature());
		} catch (Exception e) {
			tj.setStatus(CampaignTemperatureJob.STATUS_ERROR);
			tj.setError(e.getMessage());
			campaignTemperatureJobRepository.save(tj);
		}
	}

	private void pollTemperatureJob(Long projectId, CampaignTemperatureJob tj) {
		if (tj.getRunId() == null || tj.getRunId().isBlank()) {
			tj.setStatus(CampaignTemperatureJob.STATUS_ERROR);
			tj.setError("Missing run_id for generation job");
			campaignTemperatureJobRepository.save(tj);
			return;
		}
		GenerationJob job = generationJobService.checkRunStatus(projectId, tj.getRunId());
		if (job == null) {
			return;
		}
		tj.setStatus(mapJobStatus(job.getStatus()));
		tj.setError(job.getError());
		tj.setTotalRecords(job.getTotalRecords());
		campaignTemperatureJobRepository.save(tj);
	}

	private void skipTemperatureJobsForBackbone(CampaignBackbone cb, String reason) {
		for (CampaignTemperatureJob tj : cb.getTemperatureJobs()) {
			if (CampaignTemperatureJob.STATUS_PENDING.equals(tj.getStatus())
					|| CampaignTemperatureJob.STATUS_RUNNING.equals(tj.getStatus())) {
				tj.setStatus(CampaignTemperatureJob.STATUS_SKIPPED);
				tj.setError(reason);
				campaignTemperatureJobRepository.save(tj);
			}
		}
	}

	private void refreshCampaignStatus(DataGenerationCampaign campaign) {
		List<CampaignBackbone> backbones = campaignBackboneRepository.findByCampaign_IdOrderBySortOrderAsc(campaign.getId());
		boolean anyActive = false;
		boolean anyError = false;
		for (CampaignBackbone bb : backbones) {
			if (CampaignBackbone.STATUS_PENDING.equals(bb.getStatus())
					|| CampaignBackbone.STATUS_RUNNING.equals(bb.getStatus())) {
				anyActive = true;
			}
			if (CampaignBackbone.STATUS_ERROR.equals(bb.getStatus())) {
				anyError = true;
			}
			for (CampaignTemperatureJob tj : bb.getTemperatureJobs()) {
				if (CampaignTemperatureJob.STATUS_PENDING.equals(tj.getStatus())
						|| CampaignTemperatureJob.STATUS_RUNNING.equals(tj.getStatus())) {
					anyActive = true;
				}
				if (CampaignTemperatureJob.STATUS_ERROR.equals(tj.getStatus())) {
					anyError = true;
				}
			}
		}
		if (anyActive) {
			if (!DataGenerationCampaign.STATUS_RUNNING.equals(campaign.getStatus())) {
				campaign.setStatus(DataGenerationCampaign.STATUS_RUNNING);
				campaign.setCompletedAt(null);
				campaignRepository.save(campaign);
			}
			return;
		}
		finalizeCampaign(campaign, anyError);
	}

	private void finalizeCampaign(DataGenerationCampaign campaign, boolean anyError) {
		campaign.setStatus(anyError ? DataGenerationCampaign.STATUS_PARTIAL : DataGenerationCampaign.STATUS_COMPLETED);
		campaign.setCompletedAt(Instant.now());
		campaignRepository.save(campaign);
		log.info("Campaign {} finished with status {}", campaign.getId(), campaign.getStatus());
	}

	private static String mapBackboneStatus(String backboneStatus) {
		if (backboneStatus == null) {
			return CampaignBackbone.STATUS_RUNNING;
		}
		return switch (backboneStatus.toUpperCase()) {
			case "COMPLETED" -> CampaignBackbone.STATUS_COMPLETED;
			case "ERROR" -> CampaignBackbone.STATUS_ERROR;
			case "QUEUED", "RUNNING" -> CampaignBackbone.STATUS_RUNNING;
			default -> CampaignBackbone.STATUS_RUNNING;
		};
	}

	private static String mapJobStatus(String jobStatus) {
		if (jobStatus == null) {
			return CampaignTemperatureJob.STATUS_RUNNING;
		}
		return switch (jobStatus.toUpperCase()) {
			case "COMPLETED" -> CampaignTemperatureJob.STATUS_COMPLETED;
			case "ERROR" -> CampaignTemperatureJob.STATUS_ERROR;
			case "QUEUED", "RUNNING" -> CampaignTemperatureJob.STATUS_RUNNING;
			default -> CampaignTemperatureJob.STATUS_RUNNING;
		};
	}

	private void validateTemperatures(List<Double> temperatures) {
		for (Double t : temperatures) {
			if (t == null || t < 0.0 || t > 1.0) {
				throw new IllegalArgumentException("Each temperature must be between 0.0 and 1.0");
			}
		}
	}

	private String writeTemperaturesJson(List<Double> temperatures) {
		try {
			return objectMapper.writeValueAsString(temperatures);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to serialize temperatures", e);
		}
	}

	private List<Double> readTemperaturesJson(String json) {
		if (json == null || json.isBlank()) {
			return List.of();
		}
		try {
			return objectMapper.readValue(json, new TypeReference<>() {});
		} catch (Exception e) {
			throw new IllegalStateException("Failed to parse temperatures JSON", e);
		}
	}

	private String writeBackboneConfigsJson(List<CreateDataGenerationCampaignRequest.BackboneConfigDto> configs) {
		try {
			return objectMapper.writeValueAsString(configs);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to serialize backbone configs", e);
		}
	}

	private List<CreateDataGenerationCampaignRequest.BackboneConfigDto> readBackboneConfigsJson(String json) {
		if (json == null || json.isBlank()) {
			return List.of();
		}
		try {
			return objectMapper.readValue(json, new TypeReference<>() {});
		} catch (Exception e) {
			throw new IllegalStateException("Failed to parse backbone configs JSON", e);
		}
	}

	public static int resolveNumBackbones(CreateDataGenerationCampaignRequest.BackboneConfigDto cfg) {
		Integer n = cfg.getNumBackbones();
		if (n == null) {
			return 1;
		}
		if (n < 1 || n > MAX_BACKBONES_PER_CONFIG) {
			throw new IllegalArgumentException(
					"numBackbones must be between 1 and " + MAX_BACKBONES_PER_CONFIG);
		}
		return n;
	}

	private static void touchBackboneUpdatedAt(CampaignBackbone cb) {
		cb.setUpdatedAt(Instant.now());
	}

	private List<CampaignBackboneConfigProgressDto> buildConfigProgressDtos(
			List<CampaignBackbone> backbones,
			List<CreateDataGenerationCampaignRequest.BackboneConfigDto> storedConfigs) {
		Map<Integer, List<CampaignBackbone>> byConfigIndex = backbones.stream()
				.collect(Collectors.groupingBy(CampaignBackbone::getConfigIndex));

		int maxIndex = byConfigIndex.keySet().stream().max(Integer::compareTo).orElse(-1);
		if (maxIndex < 0) {
			return List.of();
		}

		List<CampaignBackboneConfigProgressDto> result = new ArrayList<>();
		for (int idx = 0; idx <= maxIndex; idx++) {
			List<CampaignBackbone> group = byConfigIndex.getOrDefault(idx, List.of()).stream()
					.sorted(Comparator.comparing(CampaignBackbone::getSortOrder))
					.toList();

			CreateDataGenerationCampaignRequest.BackboneConfigDto stored =
					idx < storedConfigs.size() ? storedConfigs.get(idx) : null;
			CampaignBackbone sample = group.isEmpty() ? null : group.get(0);

			CampaignBackboneConfigProgressDto cfgDto = new CampaignBackboneConfigProgressDto();
			cfgDto.setContigs(stored != null ? stored.getContigs() : sample != null ? sample.getContigs() : null);
			cfgDto.setHotspots(stored != null ? stored.getHotspots() : sample != null ? sample.getHotspots() : null);
			cfgDto.setChainsToRemove(stored != null ? stored.getChainsToRemove()
					: sample != null ? sample.getChainsToRemove() : null);
			cfgDto.setNumBackbones(stored != null ? resolveNumBackbones(stored) : group.size());
			cfgDto.setCompletedBackbones((int) group.stream()
					.filter(bb -> CampaignBackbone.STATUS_COMPLETED.equals(bb.getStatus()))
					.count());
			cfgDto.setBackbones(group.stream().map(this::toPhysicalBackboneDto).toList());
			result.add(cfgDto);
		}
		return result;
	}

	private CampaignPhysicalBackboneDto toPhysicalBackboneDto(CampaignBackbone bb) {
		CampaignPhysicalBackboneDto dto = new CampaignPhysicalBackboneDto();
		dto.setId(bb.getId());
		dto.setRunId(bb.getRunId());
		dto.setStatus(bb.getStatus());
		dto.setCreatedAt(bb.getCreatedAt());
		dto.setUpdatedAt(bb.getUpdatedAt());
		dto.setBackboneId(bb.getBackboneId());
		dto.setError(bb.getError());
		dto.setTemperatureJobs(bb.getTemperatureJobs().stream()
				.sorted(Comparator.comparing(CampaignTemperatureJob::getTemperature))
				.map(tj -> {
					CampaignTemperatureJobProgressDto j = new CampaignTemperatureJobProgressDto();
					j.setTemperature(tj.getTemperature());
					j.setStatus(tj.getStatus());
					j.setJobId(tj.getGenerationJobId());
					j.setTotalRecords(tj.getTotalRecords());
					j.setError(tj.getError());
					return j;
				})
				.collect(Collectors.toList()));
		return dto;
	}
}
