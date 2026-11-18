CREATE TABLE notification_service.outbox_events (
  id UUID PRIMARY KEY,
  topic VARCHAR(255) NOT NULL,
  message_key VARCHAR(255) NOT NULL,
  payload TEXT NOT NULL,
  traceparent VARCHAR(100),
  created_at TIMESTAMPTZ NOT NULL,
  published_at TIMESTAMPTZ
);
CREATE INDEX idx_outbox_unpublished ON notification_service.outbox_events(created_at) WHERE published_at IS NULL;
