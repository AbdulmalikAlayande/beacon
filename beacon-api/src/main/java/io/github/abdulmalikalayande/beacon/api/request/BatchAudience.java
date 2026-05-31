package io.github.abdulmalikalayande.beacon.api.request;

import io.github.abdulmalikalayande.beacon.api.enums.AudienceType;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Describes the recipients of a batch notification.
 *
 * <p>The meaning of {@code userIds} depends on {@code type}:
 * <ul>
 *   <li>{@code SPECIFIC_USERS} — {@code userIds} holds the explicit recipients
 *       and must not be empty.</li>
 *   <li>{@code ALL} / {@code CUSTOM} — {@code userIds} is ignored; Beacon pages
 *       through the host's {@code UserBatchSource} instead.</li>
 * </ul>
 *
 * @param type    how to interpret this audience (required)
 * @param userIds explicit recipient ids for {@code SPECIFIC_USERS}, else may be {@code null}
 */
public record BatchAudience(

        @NotNull(message = "audience type must not be null")
        AudienceType type,

        List<@NotNull String> userIds
) {
    public BatchAudience {
        if (userIds != null) {
            userIds = List.copyOf(userIds);
        }
    }

    /**
     * @return {@code true} when this audience targets an explicit user list
     */
    public boolean isExplicitList() {
        return type == AudienceType.SPECIFIC_USERS;
    }
}