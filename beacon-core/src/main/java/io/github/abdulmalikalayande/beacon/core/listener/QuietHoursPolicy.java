package io.github.abdulmalikalayande.beacon.core.listener;

import io.github.abdulmalikalayande.beacon.api.dto.NotificationPreference;
import io.github.abdulmalikalayande.beacon.api.enums.NotificationPriority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * Decides when a delivery task may first be picked up, given the user's quiet hours.
 *
 * <p>Only LOW priority respects quiet hours. The window is read in the user's own
 * timezone, includes its start and excludes its end, and may wrap past midnight
 * (for example 22:00 to 07:00). A task requested inside the window is held until
 * the window ends; anything else is left at the requested time.
 */
public class QuietHoursPolicy {

	private static final Logger log = LoggerFactory.getLogger(QuietHoursPolicy.class);

	public Instant availableAt(NotificationPriority priority, NotificationPreference preference, Instant requestedAt) {
		Objects.requireNonNull(requestedAt, "requestedAt must not be null");

		if (priority != NotificationPriority.LOW || preference == null || !preference.hasQuietHours()) {
			return requestedAt;
		}

		LocalTime start = preference.quietHoursStart();
		LocalTime end = preference.quietHoursEnd();
		if (start.equals(end)) {
			return requestedAt; // an empty window means no quiet hours
		}

		ZoneId zone = zoneOf(preference);
		ZonedDateTime local = requestedAt.atZone(zone);
		LocalTime time = local.toLocalTime();

		boolean wrapsMidnight = start.isAfter(end);
		boolean inside = wrapsMidnight
				? !time.isBefore(start) || time.isBefore(end)
				: !time.isBefore(start) && time.isBefore(end);

		if (!inside) {
			return requestedAt;
		}

		// Before midnight in a wrapping window, the window ends tomorrow; otherwise today.
		LocalDate endDate = (wrapsMidnight && !time.isBefore(start))
				? local.toLocalDate().plusDays(1)
				: local.toLocalDate();

		return ZonedDateTime.of(endDate, end, zone).toInstant();
	}

	private ZoneId zoneOf(NotificationPreference preference) {
		String timezone = preference.timezone();
		if (timezone == null || timezone.isBlank()) {
			return ZoneOffset.UTC;
		}
		try {
			return ZoneId.of(timezone);
		} catch (DateTimeException e) {
			log.warn("Unknown timezone '{}' for user {}; treating quiet hours as UTC", timezone, preference.userId());
			return ZoneOffset.UTC;
		}
	}
}