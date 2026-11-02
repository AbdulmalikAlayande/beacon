package io.github.abdulmalikalayande.beacon.core.listener;

import io.github.abdulmalikalayande.beacon.api.dto.NotificationPreference;
import io.github.abdulmalikalayande.beacon.api.enums.NotificationChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.util.Set;

import static io.github.abdulmalikalayande.beacon.api.enums.NotificationPriority.HIGH;
import static io.github.abdulmalikalayande.beacon.api.enums.NotificationPriority.LOW;
import static io.github.abdulmalikalayande.beacon.api.enums.NotificationPriority.MEDIUM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("QuietHoursPolicy")
class QuietHoursPolicyTest {

	private static final LocalTime TEN_PM = LocalTime.of(22, 0);
	private static final LocalTime SEVEN_AM = LocalTime.of(7, 0);

	private final QuietHoursPolicy policy = new QuietHoursPolicy();

	// Lagos is UTC+1 all year, so 22:00 to 07:00 Lagos is 21:00Z to 06:00Z.

	@Test
	@DisplayName("LOW, before midnight inside an overnight window: held until the window ends tomorrow")
	void low_beforeMidnight_heldUntilTomorrowMorning() {
		Instant requested = Instant.parse("2026-05-31T22:30:00Z"); // 23:30 Lagos
		assertEquals(Instant.parse("2026-06-01T06:00:00Z"),
				policy.availableAt(LOW, user("Africa/Lagos", TEN_PM, SEVEN_AM), requested));
	}

	@Test
	@DisplayName("LOW, after midnight inside an overnight window: held until the window ends the same morning")
	void low_afterMidnight_heldUntilThisMorning() {
		Instant requested = Instant.parse("2026-06-01T02:00:00Z"); // 03:00 Lagos
		assertEquals(Instant.parse("2026-06-01T06:00:00Z"),
				policy.availableAt(LOW, user("Africa/Lagos", TEN_PM, SEVEN_AM), requested));
	}

	@Test
	@DisplayName("LOW outside the window: unchanged")
	void low_outsideWindow_unchanged() {
		Instant requested = Instant.parse("2026-05-31T11:00:00Z"); // 12:00 Lagos
		assertEquals(requested, policy.availableAt(LOW, user("Africa/Lagos", TEN_PM, SEVEN_AM), requested));
	}

	@Test
	@DisplayName("exactly at the window start counts as inside")
	void low_exactlyAtStart_isHeld() {
		Instant requested = Instant.parse("2026-05-31T21:00:00Z"); // 22:00 Lagos
		assertEquals(Instant.parse("2026-06-01T06:00:00Z"),
				policy.availableAt(LOW, user("Africa/Lagos", TEN_PM, SEVEN_AM), requested));
	}

	@Test
	@DisplayName("exactly at the window end counts as outside")
	void low_exactlyAtEnd_unchanged() {
		Instant requested = Instant.parse("2026-06-01T06:00:00Z"); // 07:00 Lagos
		assertEquals(requested, policy.availableAt(LOW, user("Africa/Lagos", TEN_PM, SEVEN_AM), requested));
	}

	@Test
	@DisplayName("a same-day window (13:00 to 15:00) holds until 15:00 the same day")
	void low_sameDayWindow_heldUntilWindowEnd() {
		Instant requested = Instant.parse("2026-05-31T13:00:00Z"); // 14:00 Lagos
		assertEquals(Instant.parse("2026-05-31T14:00:00Z"),
				policy.availableAt(LOW, user("Africa/Lagos", LocalTime.of(13, 0), LocalTime.of(15, 0)), requested));
	}

	@Test
	@DisplayName("the window is read in the user's timezone, not UTC")
	void windowUsesUsersTimezone() {
		Instant requested = Instant.parse("2026-05-31T21:30:00Z"); // 22:30 Lagos, 21:30 UTC
		assertEquals(Instant.parse("2026-06-01T06:00:00Z"),
				policy.availableAt(LOW, user("Africa/Lagos", TEN_PM, SEVEN_AM), requested));
		assertEquals(requested,
				policy.availableAt(LOW, user("UTC", TEN_PM, SEVEN_AM), requested));
	}

	@Test
	@DisplayName("HIGH ignores quiet hours")
	void high_insideWindow_unchanged() {
		Instant requested = Instant.parse("2026-06-01T02:00:00Z");
		assertEquals(requested, policy.availableAt(HIGH, user("Africa/Lagos", TEN_PM, SEVEN_AM), requested));
	}

	@Test
	@DisplayName("MEDIUM ignores quiet hours")
	void medium_insideWindow_unchanged() {
		Instant requested = Instant.parse("2026-06-01T02:00:00Z");
		assertEquals(requested, policy.availableAt(MEDIUM, user("Africa/Lagos", TEN_PM, SEVEN_AM), requested));
	}

	@Test
	@DisplayName("a user with no quiet hours: unchanged")
	void noQuietHours_unchanged() {
		Instant requested = Instant.parse("2026-06-01T02:00:00Z");
		assertEquals(requested, policy.availableAt(LOW, user("Africa/Lagos", null, null), requested));
	}

	@Test
	@DisplayName("a window whose start equals its end means no quiet hours")
	void emptyWindow_unchanged() {
		Instant requested = Instant.parse("2026-06-01T02:00:00Z");
		assertEquals(requested, policy.availableAt(LOW, user("Africa/Lagos", TEN_PM, TEN_PM), requested));
	}

	@Test
	@DisplayName("a missing timezone is treated as UTC")
	void nullTimezone_treatedAsUtc() {
		Instant requested = Instant.parse("2026-05-31T23:00:00Z");
		assertEquals(Instant.parse("2026-06-01T07:00:00Z"),
				policy.availableAt(LOW, user(null, TEN_PM, SEVEN_AM), requested));
	}

	@Test
	@DisplayName("an unknown timezone is treated as UTC instead of failing the send")
	void unknownTimezone_treatedAsUtc() {
		Instant requested = Instant.parse("2026-05-31T23:00:00Z");
		assertEquals(Instant.parse("2026-06-01T07:00:00Z"),
				policy.availableAt(LOW, user("Not/AZone", TEN_PM, SEVEN_AM), requested));
	}

	@Test
	@DisplayName("a null requested time is rejected")
	void nullRequestedAt_rejected() {
		assertThrows(NullPointerException.class,
				() -> policy.availableAt(LOW, user("Africa/Lagos", TEN_PM, SEVEN_AM), null));
	}

	private NotificationPreference user(String timezone, LocalTime start, LocalTime end) {
		return new NotificationPreference("user-1", "ada@example.com", "+2348012345678", "fcm-token",
				Set.of(NotificationChannel.EMAIL), timezone, start, end);
	}
}