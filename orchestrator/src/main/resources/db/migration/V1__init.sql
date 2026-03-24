CREATE TABLE orchestrator.saga_instances (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id UUID,
  status VARCHAR(50) NOT NULL,
  current_step VARCHAR(50),
  payload JSONB,
  failure_reason VARCHAR(500),
  created_at TIMESTAMP DEFAULT now(),
  updated_at TIMESTAMP DEFAULT now()
);
CREATE INDEX idx_saga_status ON orchestrator.saga_instances(status);
