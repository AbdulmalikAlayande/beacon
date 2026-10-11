package io.github.abdulmalikalayande.beacon.core.dedup;

import io.github.abdulmalikalayande.beacon.api.exception.DuplicateNotificationException;
import io.github.abdulmalikalayande.beacon.api.port.DeduplicationStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.apache.commons.lang3.StringUtils;

import java.time.Clock;
import java.time.Duration;

/**
 *
 */
public class RedisDeduplicationStore implements DeduplicationStore {
	
	/**
	 * The Default value for how long we remember a key
	 */
	private static final int DEFAULT_RETENTION_WINDOW = 3600; // 1 hour
	private final int retentionWindow;
	private final Clock clock;
	private final StringRedisTemplate redisTemplate;
	private static final String BEACON_DEDUP_NAMESPACE = "beacon:dedup:%s";
	
	public RedisDeduplicationStore(StringRedisTemplate redisTemplate, Clock clock) {
		this(redisTemplate, clock, DEFAULT_RETENTION_WINDOW);
	}
	
	public RedisDeduplicationStore(StringRedisTemplate redisTemplate, Clock clock, int retentionWindow) {
		this.redisTemplate = redisTemplate;
		this.clock = clock;
		this.retentionWindow = retentionWindow;
	}
	
	@Override
	public boolean isSeen(String idempotencyKey) {
		if (StringUtils.isBlank(idempotencyKey)) {
			throw new IllegalArgumentException("idempotencyKey must not be null or blank");
		}
		return redisTemplate.hasKey(String.format(BEACON_DEDUP_NAMESPACE, idempotencyKey));
	}
	
	@Override
	public void markSeen(String idempotencyKey) {
		if (StringUtils.isBlank(idempotencyKey)) {
			throw new IllegalArgumentException("idempotencyKey must not be null or blank");
		}
	
		String redisKey = String.format(BEACON_DEDUP_NAMESPACE, idempotencyKey);
		Boolean isSet = redisTemplate.opsForValue().setIfAbsent(redisKey, clock.instant().toString(), Duration.ofSeconds(retentionWindow));
		if (isSet == null || !isSet) {
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
}
