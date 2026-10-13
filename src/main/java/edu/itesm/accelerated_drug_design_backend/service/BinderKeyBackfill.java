package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.repository.AppSettingRepository;
import edu.itesm.accelerated_drug_design_backend.repository.BinderKeyBackfillRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Copies binder tails out of existing generation records once. The query
 * selects backbone id, sequence, job id and n — not the pdb column.
 */
@Service
public class BinderKeyBackfill {

	private static final Logger log = LoggerFactory.getLogger(BinderKeyBackfill.class);
	private static final int PAGE = 500;

	private final BinderKeyBackfillRepository recordRepository;
	private final BinderKeyService binderKeyService;
	private final AppSettingRepository settings;
	private ObjectProvider<BinderKeyBackfill> self;

	public BinderKeyBackfill(
			BinderKeyBackfillRepository recordRepository,
			BinderKeyService binderKeyService,
			AppSettingRepository settings) {
		this.recordRepository = recordRepository;
		this.binderKeyService = binderKeyService;
		this.settings = settings;
	}

	@Autowired
	void setSelf(ObjectProvider<BinderKeyBackfill> self) {
		this.self = self;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void onReady() {
		fillIfNeeded();
	}

	public void fillIfNeeded() {
		if (settings.findById(SettingsService.BACKFILL_DONE)
				.map(row -> "1".equals(row.getSettingValue()))
				.orElse(false)) {
			return;
		}
		long jobId = 0;
		int n = -1;
		int copied = 0;
		while (true) {
			PageCursor page = self.getObject().fillPage(jobId, n);
			if (page.count == 0) {
				break;
			}
			jobId = page.jobId;
			n = page.n;
			copied += page.count;
		}
		self.getObject().markDone();
		log.info("Binder key backfill stored keys from {} record pages-worth of rows", copied);
	}

	@Transactional
	public PageCursor fillPage(long jobId, int n) {
		List<Object[]> page = recordRepository.findSeqPageForBinderBackfill(jobId, n, PageRequest.of(0, PAGE));
		if (page.isEmpty()) {
			return new PageCursor(jobId, n, 0);
		}
		long lastJob = jobId;
		int lastN = n;
		for (Object[] row : page) {
			Long backboneId = row[0] == null ? null : ((Number) row[0]).longValue();
			String seq = row[1] == null ? null : String.valueOf(row[1]);
			lastJob = ((Number) row[2]).longValue();
			lastN = ((Number) row[3]).intValue();
			String key = MpnnSampling.storedBinderKey(seq);
			if (backboneId != null && !key.isEmpty()) {
				binderKeyService.insertIfAbsent(backboneId, key, null);
			}
		}
		return new PageCursor(lastJob, lastN, page.size());
	}

	@Transactional
	public void markDone() {
		var row = settings.findById(SettingsService.BACKFILL_DONE).orElseGet(
				edu.itesm.accelerated_drug_design_backend.entity.AppSetting::new);
		row.setSettingKey(SettingsService.BACKFILL_DONE);
		row.setSettingValue("1");
		row.setDescription("Historical binder keys copied from generation_jobs_records");
		row.setUpdatedAt(java.time.Instant.now());
		row.setUpdatedBy("system");
		settings.save(row);
	}

	public record PageCursor(long jobId, int n, int count) {
	}
}
