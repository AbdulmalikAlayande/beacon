package io.github.abdulmalikalayande.beacon.core.dedup;

import io.github.abdulmalikalayande.beacon.api.exception.DuplicateNotificationException;
import io.github.abdulmalikalayande.beacon.api.port.DeduplicationStore;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/**
 * Redis-backed {@link DeduplicationStore} for distributed, high-concurrency deployments.
 *
 * <p>Uses atomic {@code SETNX} with TTL ({@code SET key value NX EX ttl}) to guarantee that duplicate
 * detection and key registration happen in a single operation without race conditions.
 *
 * <p>All keys are registered with an expiration window (default 24 hours) to prevent unbounded memory growth.
 */
public class RedisDeduplicationStore implements DeduplicationStore {

	public static final Duration DEFAULT_TTL = Duration.ofHours(24);
	public static final String DEFAULT_KEY_PREFIX = "beacon:dedup:";

	private final StringRedisTemplate redisTemplate;
	private final Clock clock;
	private final Duration ttl;
	private final String keyPrefix;

	public RedisDeduplicationStore(StringRedisTemplate redisTemplate, Clock clock) {
		this(redisTemplate, clock, DEFAULT_TTL, DEFAULT_KEY_PREFIX);
	}

	public RedisDeduplicationStore(StringRedisTemplate redisTemplate, Clock clock, Duration ttl) {
		this(redisTemplate, clock, ttl, DEFAULT_KEY_PREFIX);
	}

	public RedisDeduplicationStore(StringRedisTemplate redisTemplate, Clock clock, int retentionWindowSeconds) {
		this(redisTemplate, clock, Duration.ofSeconds(retentionWindowSeconds), DEFAULT_KEY_PREFIX);
	}

	public RedisDeduplicationStore(StringRedisTemplate redisTemplate, Clock clock, Duration ttl, String keyPrefix) {
		this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
		this.clock = Objects.requireNonNull(clock, "clock must not be null");
		this.ttl = Objects.requireNonNull(ttl, "ttl must not be null");
		this.keyPrefix = StringUtils.isNotBlank(keyPrefix) ? keyPrefix : DEFAULT_KEY_PREFIX;
	}

	@Override
	public boolean isSeen(String idempotencyKey) {
		if (StringUtils.isBlank(idempotencyKey)) {
			throw new IllegalArgumentException("idempotencyKey must not be null or blank");
		}
		Boolean exists = redisTemplate.hasKey(formatKey(idempotencyKey));
		return Boolean.TRUE.equals(exists);
	}

	@Override
	public void markSeen(String idempotencyKey) {
		if (StringUtils.isBlank(idempotencyKey)) {
			throw new IllegalArgumentException("idempotencyKey must not be null or blank");
		}

		String redisKey = formatKey(idempotencyKey);
		Boolean isSet = redisTemplate.opsForValue().setIfAbsent(redisKey, clock.instant().toString(), ttl);
		if (!Boolean.TRUE.equals(isSet)) {
			throw new DuplicateNotificationException(idempotencyKey);
		}
	}

	@Override
	public boolean acquireDeliveryLock(String notificationId) {
		// TODO: Implement delivery locking using SET NX EX with a lock TTL in worker phase
		return false;
	}

	@Override
	public void releaseDeliveryLock(String notificationId) {
		// TODO: Implement delivery lock release in worker phase
	}

	private String formatKey(String idempotencyKey) {
		return keyPrefix + idempotencyKey;
	}
}
