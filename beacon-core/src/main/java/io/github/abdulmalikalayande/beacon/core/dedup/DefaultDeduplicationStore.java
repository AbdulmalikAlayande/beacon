package io.github.abdulmalikalayande.beacon.core.dedup;

import io.github.abdulmalikalayande.beacon.api.exception.DuplicateNotificationException;
import io.github.abdulmalikalayande.beacon.api.port.DeduplicationStore;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/*
* TODO: To Implement a scheduled executor service, like a background thread or task that runs at regular intervals
*  (e.g., every minute) to check for expired entries (entries older than the specified time limit) and remove them from
*  the map. This way, we can ensure that the cleanup process is automated and does not rely on user interactions.
*
* TODO: On this implementation above I think we may want to leave it out, the cleanup that relies on user interactions
*  already works fine, but maybe we can consult others on the best ways to build a perfect Rentention Policy for this.
*/
public class DefaultDeduplicationStore implements DeduplicationStore {
	
	private final Clock clock;
	private final int storeSize;
	private final int retentionWindow;
	
	/**
	 * The Default value for how long we remember a key
	 */
	private static final int DEFAULT_RETENTION_WINDOW = 3600; // 1 hour
	
	public static final int DEFAULT_STORE_SIZE = 100_000; // hundred thousand entries by default
	private final ConcurrentHashMap<String, Instant> defaultDedupStore = new ConcurrentHashMap<>();
	
	public DefaultDeduplicationStore(Clock clock) {
		this(clock, DEFAULT_RETENTION_WINDOW, DEFAULT_STORE_SIZE);
	}
	
	//retentionWindow and storeSize Parameters settable by host application via property configurations
	public DefaultDeduplicationStore(Clock clock, int retentionWindow, int storeSize) {
		this.clock = clock;
		this.storeSize = storeSize;
		this.retentionWindow = retentionWindow;
	}
	
	@Override
	public void markSeen(String idempotencyKey) {
		/*TODO: To implement the cleanup process, whenever a new entry is added to the map. Before adding a new entry,
		   we can check if the map has reached its size limit or if there are any expired entries, and perform the
		   cleanup accordingly. This way, we can ensure that the map does not exceed its limits while still allowing for
		   new entries to be added without blocking the main thread.
		   Why this works: We already set a maximum size for the map, and when that limit is reached, we can remove the
		   oldest entries to make room for new ones.
		   Finding the oldest entry is straightforward, we scan the map for the entry with the smallest Instant value.
		   It's an O(n) scan, but it only happens when the map is at capacity AND nothing is expired, which should be rare.
		   */
		if (idempotencyKey == null || idempotencyKey.isBlank())
			throw new IllegalArgumentException("idempotencyKey must not be null or blank");
		
		if (defaultDedupStore.size() >= storeSize) {
			// if the map is full, we will check if any of the entries are older than the retention window (expired)
			// and remove them if there are any.
			// But what if there are no expired entries? In that case how do we know? I think we should do a size difference
			// before and after checks to figure that out. and after that what do we do?
			int initialSize = defaultDedupStore.size();
			defaultDedupStore.entrySet().removeIf(entry -> clock.instant().minusSeconds(retentionWindow).isAfter(entry.getValue()));
			int sizeAfterCleanup = defaultDedupStore.size();
			
			// so now we check if anything was removed
			if (sizeAfterCleanup == initialSize) {
				// nothing was removed, so here is what we do, we remove the entry with the earliest timestamp to make room.
				// Yeah, we lose dedup protection for that one old key, but the new notification goes through.
				// The risk is small, if someone resends that old key, it gets through twice. But the oldest key is the
				// one least likely to be retried, compared to dropping the new one. The purpose of this store is to
				// prevent duplicate notifications. If we reject a brand-new, never-seen-before notification just because
				// the map is full, we have failed at our primary job. It's better to lose protection for one old key
				// (that probably won't be retried) than to block a legitimate new notification
				defaultDedupStore.entrySet().stream()
						.min(Map.Entry.comparingByValue())
						.map(Map.Entry::getKey)
						.ifPresent(defaultDedupStore::remove);
			}
			
			//This cleanup code is still not perfectly atomic because ConcurrentHashMap can change while scanning it.
			// Although that seems okay for a best-effort in-memory store, I think we want strict correctness under
			// heavy concurrency, so we should tighten it a bit. But there is currently a gap in the developer skill set
			// around how to do that, so for now we will just accept the small risk of a duplicate notification.
		}
		Instant existingEntry = defaultDedupStore.putIfAbsent(idempotencyKey, clock.instant());
		if (existingEntry != null) {
			throw new DuplicateNotificationException(idempotencyKey);
		}
	}
	
	@Override
	public boolean acquireDeliveryLock(String notificationId) {
		return false;
	}
	
	@Override
	public void releaseDeliveryLock(String notificationId) {
	
	}
	
	@Override
	public boolean isSeen(String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.isBlank())
			throw new IllegalArgumentException("idempotencyKey must not be null or blank");
		return defaultDedupStore.containsKey(idempotencyKey);
	}
}
