package io.github.abdulmalikalayande.beacon.core.dedup;

import io.github.abdulmalikalayande.beacon.api.port.DeduplicationStore;

public class JdbcDeduplicationStore implements DeduplicationStore {
	
	@Override
	public boolean isSeen(String idempotencyKey) {
		return false;
	}
	
	public JdbcDeduplicationStore() {
	
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
}
