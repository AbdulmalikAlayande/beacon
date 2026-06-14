package io.github.abdulmalikalayande.beacon.core.dedup;

import io.github.abdulmalikalayande.beacon.api.exception.DuplicateNotificationException;
import io.github.abdulmalikalayande.beacon.api.exception.NotificationException;
import io.github.abdulmalikalayande.beacon.api.port.DeduplicationStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;


public class JdbcDeduplicationStore implements DeduplicationStore {
	
	private final JdbcTemplate jdbcTemplate;
	public static final Logger logger = LoggerFactory.getLogger(JdbcDeduplicationStore.class);
	
	public JdbcDeduplicationStore(JdbcTemplate jdbcTemplate){
		this.jdbcTemplate = jdbcTemplate;
		if (jdbcTemplate.getDataSource() == null) {
			throw new IllegalArgumentException("JdbcTemplate must have a DataSource configured");
		}
	}
	
	@Override
	public boolean isSeen(String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.isBlank())
			throw new IllegalArgumentException("idempotencyKey must not be null or blank");
		String sqlQuery = """
				SELECT EXISTS(SELECT 1 FROM deduplication_store WHERE idempotency_key = ?)
				""";
		Boolean queryResult = jdbcTemplate.queryForObject(sqlQuery, Boolean.class, idempotencyKey);
		return queryResult != null && queryResult;
	}
	
	@Override
	public void markSeen(String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.isBlank())
			throw new IllegalArgumentException("idempotencyKey must not be null or blank");
		try {
			String sqlQuery = """
				INSERT INTO deduplication_store (idempotency_key) VALUES (?)
				""";
			jdbcTemplate.update(sqlQuery, idempotencyKey);
		} catch (DuplicateKeyException e) {
			logger.error("Duplicate idempotency key detected: {}", idempotencyKey, e);
			throw new DuplicateNotificationException(idempotencyKey);
		} catch (DataAccessException e) {
			logger.error("{}\n", e.getMessage(), e);
			throw new NotificationException(e.getMessage());
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
