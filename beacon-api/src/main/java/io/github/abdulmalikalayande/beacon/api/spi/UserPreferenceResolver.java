package io.github.abdulmalikalayande.beacon.api.spi;

import io.github.abdulmalikalayande.beacon.api.dto.NotificationPreference;

import java.util.List;

/**
 * Resolves a user's contact details and channel preferences.
 *
 * <p><b>Host implements this.</b> It is the bridge between Beacon and the host's
 * user data. Beacon never queries the host's user tables directly — it asks
 * through this interface at delivery time. The host fetches from wherever its
 * user data lives (database, cache, user service) and returns a
 * {@link NotificationPreference}. The host is responsible for decrypting any
 * encrypted contact fields before returning them.
 *
 * <p>This is the single required integration point. A host can wire up Beacon
 * with nothing more than an implementation of this interface plus configuration.
 */
public interface UserPreferenceResolver {

    /**
     * Resolve preferences for one user.
     *
     * @param userId the host's user identifier
     * @return the user's preferences, or {@code null} if the user is unknown
     */
    NotificationPreference resolve(String userId);

    /**
     * Resolve preferences for many users at once. Used on the batch path to
     * avoid a resolve call per user.
     *
     * @param userIds the user identifiers to resolve
     * @return preferences for the resolvable users; unknown users may be omitted
     */
    List<NotificationPreference> resolveAll(List<String> userIds);
}