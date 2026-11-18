CREATE TABLE inventory_service.outbox_events (
  id UUID PRIMARY KEY,
  topic VARCHAR(255) NOT NULL,
  message_key VARCHAR(255) NOT NULL,
  payload TEXT NOT NULL,
  traceparent VARCHAR(100),
  created_at TIMESTAMPTZ NOT NULL,
  published_at TIMESTAMPTZ
);
CREATE INDEX idx_outbox_unpublished ON inventory_service.outbox_events(created_at) WHERE published_at IS NULL;
CREATE INDEX idx_reservations_saga ON inventory_service.reservations(saga_id);

CREATE TABLE inventory_service.stock (
  product_id UUID PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  available INT NOT NULL CHECK (available >= 0)
);
INSERT INTO inventory_service.stock(product_id, name, available) VALUES
  ('11111111-1111-1111-1111-111111111111', 'Laptop', 1000000),
  ('22222222-2222-2222-2222-222222222222', 'Headphones', 1000000),
  ('33333333-3333-3333-3333-333333333333', 'Limited Edition Console', 20);
