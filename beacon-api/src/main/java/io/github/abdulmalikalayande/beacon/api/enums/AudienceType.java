package io.github.abdulmalikalayande.beacon.api.enums;

/**
 * Describes who the recipients of a batch notification are.
 */
public enum AudienceType {

    /**
     * Every user. Beacon pages through the host's full user set via the
     * host-supplied {@code UserBatchSource}.
     */
    ALL,

    /** An explicit list of user IDs supplied in the batch request. */
    SPECIFIC_USERS,

    /**
     * A host-defined audience. Beacon pages through it via the host's
     * {@code UserBatchSource} implementation.
     */
    CUSTOM
}
