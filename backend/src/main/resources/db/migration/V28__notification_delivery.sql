-- V28: Notification delivery state.
-- The original V15 notifications table only tracked is_read; it had no
-- status / attempts / delivery_results to drive an async dispatcher.
-- This migration adds those columns so PendingNotificationProcessor
-- (gap-04) can drain the queue into Slack/Teams/Email channels.

ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','DELIVERED','FAILED')),
    ADD COLUMN IF NOT EXISTS attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS delivery_results JSONB,
    ADD COLUMN IF NOT EXISTS delivered_at TIMESTAMPTZ;

-- Partial index: most rows get DELIVERED quickly, so the index only covers
-- the hot working-set (PENDING rows still in retry budget).
CREATE INDEX IF NOT EXISTS idx_notifications_pending
    ON notifications(created_at)
    WHERE status = 'PENDING';
