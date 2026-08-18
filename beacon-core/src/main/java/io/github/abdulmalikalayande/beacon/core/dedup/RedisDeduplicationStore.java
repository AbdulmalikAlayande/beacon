package io.github.abdulmalikalayande.beacon.core.dedup;

import io.github.abdulmalikalayande.beacon.api.exception.DuplicateNotificationException;
import io.github.abdulmalikalayande.beacon.api.port.DeduplicationStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/**
 * Redis-backed {@link DeduplicationStore} for higher-concurrency deployments where
 * multiple app instances share dedup state. Unlike {@link DefaultDeduplicationStore}
 * this survives restarts; unlike {@link JdbcDeduplicationStore} it is not durable,
 * trading that for lower latency and built-in key expiry.
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

	public RedisDeduplicationStore(StringRedisTemplate redisTemplate, Clock clock, Duration ttl, String keyPrefix) {
		this.redisTemplate = Objects.requireNonNull(redisTemplate, "StringRedisTemplate must not be null");
		this.clock = Objects.requireNonNull(clock, "Clock must not be null");
		Objects.requireNonNull(ttl, "ttl must not be null");
		if (ttl.isZero() || ttl.isNegative()) {
			throw new IllegalArgumentException("ttl must be strictly positive");
		}
		if (ttl.getSeconds() > Integer.MAX_VALUE) {
			throw new IllegalArgumentException("ttl seconds cannot exceed Integer.MAX_VALUE");
		}
		this.ttl = ttl;
		this.keyPrefix = keyPrefix == null || keyPrefix.isBlank() ? DEFAULT_KEY_PREFIX : keyPrefix;
	}

	@Override
	public boolean isSeen(String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.isBlank())
			throw new IllegalArgumentException("idempotencyKey must not be null or blank");
		return Boolean.TRUE.equals(redisTemplate.hasKey(redisKey(idempotencyKey)));
	}

	@Override
	public void markSeen(String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.isBlank())
			throw new IllegalArgumentException("idempotencyKey must not be null or blank");

		// The SET is the check: SETNX (via ValueOperations#setIfAbsent with a TTL) atomically
		// claims the key. If it returns false, someone else already claimed it -- there is no
		// separate EXISTS-then-SET step, which would be a race condition.
		ValueOperations<String, String> valueOperations = redisTemplate.opsForValue();
		Boolean wasAbsent = valueOperations.setIfAbsent(redisKey(idempotencyKey), String.valueOf(clock.instant().toEpochMilli()), ttl);
		if (!Boolean.TRUE.equals(wasAbsent)) {
			throw new DuplicateNotificationException(idempotencyKey);
		}
	}

	@Override
	public boolean acquireDeliveryLock(String notificationId) {
		// TODO: worker-phase concern. Will use SET NX EX with a lock TTL, same atomic
		// claim pattern as markSeen, so a crashed worker doesn't hold the lock forever.
		return false;
	}

	@Override
	public void releaseDeliveryLock(String notificationId) {
		// TODO: worker-phase concern. Will DEL the lock key acquired by acquireDeliveryLock.
	}

	private String redisKey(String idempotencyKey) {
		return keyPrefix + idempotencyKey;
	}
}
