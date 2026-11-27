CREATE TABLE IF NOT EXISTS outbox_event (
  id UUID PRIMARY KEY,
  event_type VARCHAR(120) NOT NULL,
  aggregate_type VARCHAR(60) NOT NULL,
  aggregate_id VARCHAR(60) NOT NULL,
  payload CLOB NOT NULL,
  occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
  published_at TIMESTAMP WITH TIME ZONE
);
