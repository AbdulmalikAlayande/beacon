package io.github.abdulmalikalayande.beacon.core.dedup;

import io.github.abdulmalikalayande.beacon.api.port.DeduplicationStore;

public class JdbcDeduplicationStore implements DeduplicationStore {
	
	public JdbcDeduplicationStore() {
	
	}
	
	@Override
	public boolean isDuplicate(String deduplicationKey) {
		return false;
	}
	
	@Override
	public void markSeen(String idempotencyKey) {
	
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
		return false;
	}
}
