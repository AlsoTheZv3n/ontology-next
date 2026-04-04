-- Dead Letter Queue for failed CDC events
CREATE TABLE cdc_dead_letter_queue (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    stream_key      VARCHAR(255) NOT NULL,
    record_id       VARCHAR(255),
    event_data      JSONB NOT NULL,
    error_message   TEXT,
    retry_count     INT DEFAULT 0,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    resolved_at     TIMESTAMPTZ
);

CREATE INDEX idx_dlq_created ON cdc_dead_letter_queue(created_at DESC);
CREATE INDEX idx_dlq_unresolved ON cdc_dead_letter_queue(resolved_at) WHERE resolved_at IS NULL;
