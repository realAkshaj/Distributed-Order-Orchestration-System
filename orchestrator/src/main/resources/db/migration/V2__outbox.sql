CREATE TABLE orchestrator.outbox_events (
  id UUID PRIMARY KEY,
  topic VARCHAR(255) NOT NULL,
  message_key VARCHAR(255) NOT NULL,
  payload TEXT NOT NULL,
  traceparent VARCHAR(100),
  created_at TIMESTAMPTZ NOT NULL,
  published_at TIMESTAMPTZ
);
CREATE INDEX idx_outbox_unpublished ON orchestrator.outbox_events(created_at) WHERE published_at IS NULL;

CREATE TABLE orchestrator.saga_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  saga_id UUID NOT NULL,
  event_type VARCHAR(50) NOT NULL,
  success BOOLEAN NOT NULL,
  detail VARCHAR(500),
  occurred_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_saga_events_saga ON orchestrator.saga_events(saga_id, occurred_at);
CREATE INDEX idx_saga_updated ON orchestrator.saga_instances(updated_at);
