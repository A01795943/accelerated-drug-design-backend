package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.entity.BackboneBinderKey;
import edu.itesm.accelerated_drug_design_backend.entity.BackboneBinderKeyId;
import edu.itesm.accelerated_drug_design_backend.repository.BackboneBinderKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BinderKeyService {

	private static final Logger log = LoggerFactory.getLogger(BinderKeyService.class);

	private final BackboneBinderKeyRepository repository;

	public BinderKeyService(BackboneBinderKeyRepository repository) {
		this.repository = repository;
	}

	/** Keys this run must not sample again. {@code minibatchId} null means every key of the backbone. */
	@Transactional(readOnly = true)
	public List<String> keysToExclude(Long backboneId, Long minibatchId) {
		if (backboneId == null) {
			return List.of();
		}
		if (minibatchId == null) {
			return repository.findKeys(backboneId);
		}
		return repository.findKeysExcludingMinibatch(backboneId, minibatchId);
	}

	/**
	 * Drops this minibatch's previous keys (a retry) and inserts the new ones.
	 * A key already stored by another minibatch is left in place and counted.
	 * Record rows are not removed when that happens.
	 */
	@Transactional
	public int replaceForMinibatch(Long backboneId, Long minibatchId, List<String> sequences) {
		if (backboneId == null || minibatchId == null) {
			return 0;
		}
		repository.deleteByMinibatchId(minibatchId);
		int alreadyThere = 0;
		if (sequences == null) {
			return 0;
		}
		for (String seq : sequences) {
			String key = MpnnSampling.storedBinderKey(seq);
			if (key.isEmpty()) {
				continue;
			}
			if (insertIfAbsent(backboneId, key, minibatchId) == 0) {
				alreadyThere++;
			}
		}
		if (alreadyThere > 0) {
			log.info("Minibatch {} left {} binder keys already stored by another minibatch",
					minibatchId, alreadyThere);
		}
		return alreadyThere;
	}

	/**
	 * Inserts the key when this backbone does not have it yet. Returns 0 when
	 * another minibatch already stored it. H2 runs {@code INSERT ... WHERE NOT
	 * EXISTS} anyway, so the check is in Java and the primary key is the backstop.
	 */
	@Transactional
	public int insertIfAbsent(Long backboneId, String binderKey, Long minibatchId) {
		if (backboneId == null || binderKey == null || binderKey.isEmpty()) {
			return 0;
		}
		BackboneBinderKeyId id = new BackboneBinderKeyId(backboneId, binderKey);
		if (repository.existsById(id)) {
			return 0;
		}
		BackboneBinderKey row = new BackboneBinderKey();
		row.setBackboneId(backboneId);
		row.setBinderKey(binderKey);
		row.setMinibatchId(minibatchId);
		try {
			repository.saveAndFlush(row);
			return 1;
		} catch (DataIntegrityViolationException ex) {
			return 0;
		}
	}
}
