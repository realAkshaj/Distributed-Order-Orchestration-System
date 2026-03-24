CREATE TABLE notification_service.notifications (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  saga_id UUID NOT NULL,
  order_id UUID NOT NULL,
  type VARCHAR(50) NOT NULL,
  status VARCHAR(50) NOT NULL,
  idempotency_key VARCHAR(255) UNIQUE NOT NULL,
  sent_at TIMESTAMP
);
