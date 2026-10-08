package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.entity.CoreInstance;
import edu.itesm.accelerated_drug_design_backend.entity.CoreInstanceWindow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class InstanceAllocatorWindowTest {

	private static final ZoneId ZONE = ZoneId.of("America/Mexico_City");

	@Mock
	private edu.itesm.accelerated_drug_design_backend.repository.CoreInstanceRepository repository;

	private InstanceAllocator allocator;

	@BeforeEach
	void setUp() {
		allocator = new InstanceAllocator(repository, ZONE.getId());
	}

	@Test
	void restrictScheduleFalse_isAlwaysWithinWindowAndEligible() {
		CoreInstance instance = freeEnabled("UNKNOWN");
		instance.setRestrictSchedule(false);
		instance.getWindows().add(window(1, "08:00", "10:00"));
		ZonedDateTime sundayNight = zoned(2026, 8, 9, 23, 0);

		assertTrue(allocator.isWithinWindow(instance, sundayNight));
		assertTrue(allocator.fitsMargin(instance, sundayNight));
		assertTrue(allocator.isDispatchEligible(instance, sundayNight));
	}

	@Test
	void normalWindow_inclusiveBoundsOnMatchingDay() {
		CoreInstance instance = restricted();
		instance.getWindows().add(window(1, "08:00", "20:00"));

		assertTrue(allocator.isWithinWindow(instance, zoned(2026, 8, 10, 8, 0)));
		assertTrue(allocator.isWithinWindow(instance, zoned(2026, 8, 10, 12, 0)));
		assertTrue(allocator.isWithinWindow(instance, zoned(2026, 8, 10, 20, 0)));
		assertFalse(allocator.isWithinWindow(instance, zoned(2026, 8, 10, 7, 59)));
		assertFalse(allocator.isWithinWindow(instance, zoned(2026, 8, 10, 20, 1)));
		assertFalse(allocator.isWithinWindow(instance, zoned(2026, 8, 11, 12, 0)));
	}

	@Test
	void overnightWindow_coversBaseEveningAndNextMorning() {
		CoreInstance instance = restricted();
		instance.getWindows().add(window(5, "20:00", "08:00"));

		assertTrue(allocator.isWithinWindow(instance, zoned(2026, 8, 7, 20, 0)));
		assertTrue(allocator.isWithinWindow(instance, zoned(2026, 8, 7, 23, 30)));
		assertTrue(allocator.isWithinWindow(instance, zoned(2026, 8, 8, 0, 0)));
		assertTrue(allocator.isWithinWindow(instance, zoned(2026, 8, 8, 8, 0)));
		assertFalse(allocator.isWithinWindow(instance, zoned(2026, 8, 7, 19, 59)));
		assertFalse(allocator.isWithinWindow(instance, zoned(2026, 8, 8, 8, 1)));
		assertFalse(allocator.isWithinWindow(instance, zoned(2026, 8, 7, 12, 0)));
	}

	@Test
	void overnightWindow_wrapsSundayToMonday() {
		CoreInstance instance = restricted();
		instance.getWindows().add(window(7, "20:00", "08:00"));

		assertTrue(allocator.isWithinWindow(instance, zoned(2026, 8, 9, 21, 0)));
		assertTrue(allocator.isWithinWindow(instance, zoned(2026, 8, 10, 7, 0)));
		assertFalse(allocator.isWithinWindow(instance, zoned(2026, 8, 9, 19, 0)));
		assertFalse(allocator.isWithinWindow(instance, zoned(2026, 8, 10, 9, 0)));
		assertFalse(allocator.isWithinWindow(instance, zoned(2026, 8, 8, 21, 0)));
	}

	@Test
	void weekendWindows_saturdayAndSunday() {
		CoreInstance instance = restricted();
		instance.getWindows().add(window(6, "00:00", "23:59"));
		instance.getWindows().add(window(7, "00:00", "23:59"));

		assertTrue(allocator.isWithinWindow(instance, zoned(2026, 8, 8, 12, 0)));
		assertTrue(allocator.isWithinWindow(instance, zoned(2026, 8, 9, 18, 0)));
		assertFalse(allocator.isWithinWindow(instance, zoned(2026, 8, 10, 12, 0)));
		assertFalse(allocator.isWithinWindow(instance, zoned(2026, 8, 7, 12, 0)));
	}

	@Test
	void weekdayWindows_excludeWeekend() {
		CoreInstance instance = restricted();
		for (int dow = 1; dow <= 5; dow++) {
			instance.getWindows().add(window(dow, "08:00", "20:00"));
		}

		assertTrue(allocator.isWithinWindow(instance, zoned(2026, 8, 10, 9, 0)));
		assertFalse(allocator.isWithinWindow(instance, zoned(2026, 8, 8, 9, 0)));
		assertFalse(allocator.isWithinWindow(instance, zoned(2026, 8, 9, 9, 0)));
	}

	@Test
	void fitsMargin_allowsWhenEnoughSlackAndBlocksWhenNot() {
		CoreInstance instance = restricted();
		instance.setMaxJobMinutes(60);
		instance.getWindows().add(window(1, "08:00", "10:00"));

		assertTrue(allocator.fitsMargin(instance, zoned(2026, 8, 10, 9, 0)));
		assertTrue(allocator.isDispatchEligible(instance, zoned(2026, 8, 10, 9, 0)));
		assertFalse(allocator.fitsMargin(instance, zoned(2026, 8, 10, 9, 1)));
		assertFalse(allocator.isDispatchEligible(instance, zoned(2026, 8, 10, 9, 1)));
	}

	@Test
	void fitsMargin_overnightUsesEndOnFollowingMorning() {
		CoreInstance instance = restricted();
		instance.setMaxJobMinutes(600);
		instance.getWindows().add(window(5, "20:00", "08:00"));

		assertTrue(allocator.fitsMargin(instance, zoned(2026, 8, 7, 22, 0)));
		assertFalse(allocator.fitsMargin(instance, zoned(2026, 8, 8, 7, 0)));
	}

	@Test
	void fitsMargin_nullMaxJobMinutesAlwaysTrue() {
		CoreInstance instance = restricted();
		instance.setMaxJobMinutes(null);
		instance.getWindows().add(window(1, "08:00", "08:05"));

		assertTrue(allocator.fitsMargin(instance, zoned(2026, 8, 10, 8, 4)));
	}

	@Test
	void isDispatchEligible_rejectsDisabledBusyOrDown() {
		ZonedDateTime now = zoned(2026, 8, 10, 12, 0);

		CoreInstance disabled = freeEnabled("UP");
		disabled.setEnabled(false);
		assertFalse(allocator.isDispatchEligible(disabled, now));

		CoreInstance busy = freeEnabled("UP");
		busy.setCurrentRunId("run-1");
		assertFalse(allocator.isDispatchEligible(busy, now));

		CoreInstance down = freeEnabled(CoreInstance.HEALTH_DOWN);
		assertFalse(allocator.isDispatchEligible(down, now));

		CoreInstance unknown = freeEnabled(CoreInstance.HEALTH_UNKNOWN);
		assertTrue(allocator.isDispatchEligible(unknown, now));

		CoreInstance overloaded = freeEnabled("UP");
		overloaded.setStatus(CoreInstance.STATUS_OVERLOADED);
		assertFalse(allocator.isDispatchEligible(overloaded, now));

		CoreInstance unavailable = freeEnabled("UP");
		unavailable.setStatus(CoreInstance.STATUS_UNAVAILABLE);
		assertFalse(allocator.isDispatchEligible(unavailable, now));
	}

	private static CoreInstance restricted() {
		CoreInstance instance = freeEnabled("UP");
		instance.setRestrictSchedule(true);
		return instance;
	}

	private static CoreInstance freeEnabled(String health) {
		CoreInstance instance = new CoreInstance();
		instance.setEnabled(true);
		instance.setRestrictSchedule(false);
		instance.setHealthState(health);
		instance.setCurrentRunId(null);
		return instance;
	}

	private static CoreInstanceWindow window(int dayOfWeek, String start, String end) {
		CoreInstanceWindow w = new CoreInstanceWindow();
		w.setDayOfWeek(dayOfWeek);
		w.setStartTime(LocalTime.parse(start));
		w.setEndTime(LocalTime.parse(end));
		return w;
	}

	private static ZonedDateTime zoned(int year, int month, int day, int hour, int minute) {
		return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZONE);
	}
}
