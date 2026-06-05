-- V1__init_beacon_schema.sql

-- ARCHITECTURE NOTE: The notification_queue table implements the Transactional Outbox pattern.
-- It is the high-churn work table. Rows are polled via FOR UPDATE SKIP LOCKED and deleted when terminal.
CREATE TABLE notification_queue (
    id UUID PRIMARY KEY,
    notification_id UUID NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    channel VARCHAR(50) NOT NULL,
    type VARCHAR(50) NOT NULL,
    priority VARCHAR(50) NOT NULL,
    encrypted_context TEXT,
    status VARCHAR(50) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

-- Partial index for fast, zero-bloat polling by the Delivery Workers
CREATE INDEX idx_notification_queue_poll ON notification_queue (available_at) WHERE status = 'QUEUED';


-- ARCHITECTURE NOTE: The notification_status table is the append-mostly audit table.
-- It retains the permanent history of the notification lifecycle.
CREATE TABLE notification_status (
     id UUID PRIMARY KEY,
     notification_id UUID NOT NULL,
     idempotency_key VARCHAR(255) NOT NULL,
     user_id VARCHAR(255) NOT NULL,
     channel VARCHAR(50) NOT NULL,
     type VARCHAR(50) NOT NULL,
     provider VARCHAR(50),
     status VARCHAR(50) NOT NULL,
     retry_count INT NOT NULL DEFAULT 0,
     failure_reason TEXT,
     created_at TIMESTAMPTZ NOT NULL,
     updated_at TIMESTAMPTZ NOT NULL,

    -- The critical fan-out constraint:
     CONSTRAINT uq_status_idempotency_channel UNIQUE (idempotency_key, channel)
);

-- Indexes optimized for status lookups and host application dashboards
CREATE INDEX idx_notification_status_user_created ON notification_status (user_id, created_at DESC);
CREATE INDEX idx_notification_status_failed ON notification_status (status) WHERE status = 'FAILED';
CREATE INDEX idx_notification_status_processing ON notification_status (status) WHERE status = 'PROCESSING';
