-- V2__create_deduplication_store.sql
CREATE TABLE IF NOT EXISTS deduplication_store (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    idempotency_key VARCHAR(255) NOT NULL,
    notification_id UUID NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    metadata JSONB NULL,
    CONSTRAINT ux_beacon_deduplication_key UNIQUE (idempotency_key)
);

CREATE INDEX idx_deduplication_store_created_at ON deduplication_store(created_at);