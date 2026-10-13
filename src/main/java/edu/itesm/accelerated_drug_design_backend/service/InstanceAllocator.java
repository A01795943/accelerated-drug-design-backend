package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.entity.CoreInstance;
import edu.itesm.accelerated_drug_design_backend.entity.CoreInstanceWindow;
import edu.itesm.accelerated_drug_design_backend.repository.CoreInstanceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Eligibility (windows + margin + health) and atomic claim/release of a core instance slot.
 */
@Component
public class InstanceAllocator {

	private final CoreInstanceRepository coreInstanceRepository;
	private final CoreOccupancyService occupancyService;
	private final ZoneId zone;

	public InstanceAllocator(
			CoreInstanceRepository coreInstanceRepository,
			CoreOccupancyService occupancyService,
			@Value("${app.scheduling.timezone:America/Mexico_City}") String timezone) {
		this.coreInstanceRepository = coreInstanceRepository;
		this.occupancyService = occupancyService;
		this.zone = ZoneId.of(timezone);
	}

	public ZoneId zone() {
		return zone;
	}

	public ZonedDateTime now() {
		return ZonedDateTime.now(zone);
	}

	public boolean isWithinWindow(CoreInstance instance, ZonedDateTime nowZoned) {
		if (instance == null || !instance.isRestrictSchedule()) {
			return true;
		}
		ZonedDateTime now = inScheduleZone(nowZoned);
		return hasActiveWindow(instance, now);
	}

	public boolean fitsMargin(CoreInstance instance, ZonedDateTime nowZoned) {
		if (instance == null || !instance.isRestrictSchedule() || instance.getMaxJobMinutes() == null) {
			return true;
		}
		ZonedDateTime now = inScheduleZone(nowZoned);
		int required = instance.getMaxJobMinutes();
		for (CoreInstanceWindow window : windowsOf(instance)) {
			if (isWindowActive(window, now) && remainingMinutes(window, now) >= required) {
				return true;
			}
		}
		return false;
	}

	public boolean isDispatchEligible(CoreInstance instance, ZonedDateTime nowZoned) {
		if (instance == null || !instance.isEnabled()) {
			return false;
		}
		if (CoreInstance.HEALTH_DOWN.equals(instance.getHealthState())) {
			return false;
		}
		if (instance.getStatus() != null && !CoreInstance.STATUS_AVAILABLE.equals(instance.getStatus())) {
			return false;
		}
		CoreOccupancyService.InstanceOccupancy load = occupancyService.byInstance(List.of(instance)).get(instance.getId());
		if (load != null && load.inFlightTotal() >= load.maxConcurrentRuns()) {
			return false;
		}
		return isWithinWindow(instance, nowZoned) && fitsMargin(instance, nowZoned);
	}

	@Transactional
	public boolean tryClaim(Long instanceId, String runId, String runKind) {
		if (instanceId == null || runId == null || runId.isBlank()) {
			return false;
		}
		return coreInstanceRepository.claimIfFree(instanceId, runId, runKind, Instant.now()) == 1;
	}

	@Transactional
	public void release(Long instanceId, String runId) {
		if (instanceId == null || runId == null || runId.isBlank()) {
			return;
		}
		coreInstanceRepository.releaseIfOwner(instanceId, runId);
	}

	private ZonedDateTime inScheduleZone(ZonedDateTime nowZoned) {
		if (nowZoned == null) {
			return now();
		}
		return nowZoned.withZoneSameInstant(zone);
	}

	private boolean hasActiveWindow(CoreInstance instance, ZonedDateTime now) {
		for (CoreInstanceWindow window : windowsOf(instance)) {
			if (isWindowActive(window, now)) {
				return true;
			}
		}
		return false;
	}

	private static List<CoreInstanceWindow> windowsOf(CoreInstance instance) {
		return instance.getWindows() == null ? List.of() : instance.getWindows();
	}

	static boolean isWindowActive(CoreInstanceWindow window, ZonedDateTime now) {
		if (window == null || window.getDayOfWeek() == null || window.getStartTime() == null || window.getEndTime() == null) {
			return false;
		}
		int nowDow = now.getDayOfWeek().getValue();
		LocalTime nowTime = now.toLocalTime();
		LocalTime start = window.getStartTime();
		LocalTime end = window.getEndTime();
		int baseDow = window.getDayOfWeek();
		if (!start.isAfter(end)) {
			return nowDow == baseDow && !nowTime.isBefore(start) && !nowTime.isAfter(end);
		}
		if (nowDow == baseDow && !nowTime.isBefore(start)) {
			return true;
		}
		return nowDow == nextDayOfWeek(baseDow) && !nowTime.isAfter(end);
	}

	static long remainingMinutes(CoreInstanceWindow window, ZonedDateTime now) {
		LocalTime start = window.getStartTime();
		LocalTime end = window.getEndTime();
		ZonedDateTime endAt;
		if (!start.isAfter(end)) {
			endAt = now.with(end);
		} else if (now.getDayOfWeek().getValue() == window.getDayOfWeek()) {
			endAt = now.plusDays(1).with(end);
		} else {
			endAt = now.with(end);
		}
		return Math.max(0, Duration.between(now, endAt).toMinutes());
	}

	static int nextDayOfWeek(int dayOfWeek) {
		return dayOfWeek == 7 ? 1 : dayOfWeek + 1;
	}
}
