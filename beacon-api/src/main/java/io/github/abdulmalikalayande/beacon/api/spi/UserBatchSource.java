package io.github.abdulmalikalayande.beacon.api.spi;

import java.util.List;

/**
 * Supplies user identifiers for batch notifications targeting {@code ALL} or a
 * {@code CUSTOM} audience.
 *
 * <p><b>Host implements this (optional).</b> Required only if the host sends
 * batch notifications to audiences that are not an explicit user-id list. Beacon
 * pages through users in chunks using {@link #getUserIds(int, int)} so it never
 * loads the entire user base into memory at once.
 *
 * <p>If a host only ever sends to explicit user lists ({@code SPECIFIC_USERS}),
 * this interface does not need to be implemented.
 */
public interface UserBatchSource {
	
	/**
	 * Return one page of user identifiers.
	 *
	 * @param offset zero-based index of the first user in this page
	 * @param limit  maximum number of identifiers to return
	 * @return the page of user identifiers, possibly empty when exhausted
	 */
	List<String> getUserIds(int offset, int limit);
	
	/**
	 * @return the total number of users Beacon should expect to page through
	 */
	int getTotalUserCount();
}