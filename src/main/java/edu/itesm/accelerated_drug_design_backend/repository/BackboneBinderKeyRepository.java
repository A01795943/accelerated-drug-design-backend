package edu.itesm.accelerated_drug_design_backend.repository;

import edu.itesm.accelerated_drug_design_backend.entity.BackboneBinderKey;
import edu.itesm.accelerated_drug_design_backend.entity.BackboneBinderKeyId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BackboneBinderKeyRepository extends JpaRepository<BackboneBinderKey, BackboneBinderKeyId> {

	@Query("""
			SELECT k.binderKey FROM BackboneBinderKey k
			WHERE k.backboneId = :backboneId
			""")
	List<String> findKeys(@Param("backboneId") Long backboneId);

	@Query("""
			SELECT k.binderKey FROM BackboneBinderKey k
			WHERE k.backboneId = :backboneId
			  AND (k.minibatchId IS NULL OR k.minibatchId <> :minibatchId)
			""")
	List<String> findKeysExcludingMinibatch(
			@Param("backboneId") Long backboneId,
			@Param("minibatchId") Long minibatchId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("DELETE FROM BackboneBinderKey k WHERE k.minibatchId = :minibatchId")
	int deleteByMinibatchId(@Param("minibatchId") Long minibatchId);
}
