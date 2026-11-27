CREATE TABLE IF NOT EXISTS outbox_event (
  id UUID PRIMARY KEY,
  event_type VARCHAR(120) NOT NULL,
  aggregate_type VARCHAR(60) NOT NULL,
  aggregate_id VARCHAR(60) NOT NULL,
  payload CLOB NOT NULL,
  occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
  published_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS sales_order (
  id UUID PRIMARY KEY, customer_id UUID NOT NULL, status VARCHAR(20) NOT NULL,
  currency VARCHAR(3) NOT NULL, discount_amount DECIMAL(19,2) NOT NULL,
  placed_at TIMESTAMP WITH TIME ZONE, cancel_reason VARCHAR(500), version BIGINT NOT NULL
);
CREATE TABLE IF NOT EXISTS sales_order_line (
  id UUID PRIMARY KEY, order_id UUID NOT NULL, sku VARCHAR(60) NOT NULL,
  product_name VARCHAR(200) NOT NULL, unit_price_amount DECIMAL(19,2) NOT NULL,
  quantity INT NOT NULL
);

CREATE TABLE IF NOT EXISTS inventory_stock_item (
  sku VARCHAR(60) PRIMARY KEY, product_name VARCHAR(200) NOT NULL,
  on_hand INT NOT NULL, version BIGINT NOT NULL
);
CREATE TABLE IF NOT EXISTS inventory_reservation (
  order_id UUID NOT NULL, sku VARCHAR(60) NOT NULL, quantity INT NOT NULL,
  PRIMARY KEY (order_id, sku)
);

CREATE TABLE IF NOT EXISTS billing_invoice (
  id UUID PRIMARY KEY, order_id UUID NOT NULL UNIQUE, customer_id UUID NOT NULL,
  amount DECIMAL(19,2) NOT NULL, currency VARCHAR(3) NOT NULL,
  status VARCHAR(20) NOT NULL, issued_at TIMESTAMP WITH TIME ZONE NOT NULL, version BIGINT NOT NULL
);
CREATE TABLE IF NOT EXISTS billing_payment (
  id UUID PRIMARY KEY, invoice_id UUID NOT NULL, amount DECIMAL(19,2) NOT NULL,
  currency VARCHAR(3) NOT NULL, status VARCHAR(20) NOT NULL,
  gateway_ref VARCHAR(120), failure_reason VARCHAR(500),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL, version BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS fulfillment_process (
  id UUID PRIMARY KEY, order_id UUID NOT NULL UNIQUE, status VARCHAR(30) NOT NULL,
  failure_reason VARCHAR(500), updated_at TIMESTAMP WITH TIME ZONE NOT NULL, version BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS processed_event (
  event_id UUID NOT NULL, consumer VARCHAR(120) NOT NULL,
  processed_at TIMESTAMP WITH TIME ZONE NOT NULL,
  PRIMARY KEY (event_id, consumer)
);

CREATE TABLE IF NOT EXISTS order_summary_view (
  order_id UUID PRIMARY KEY, customer_id UUID NOT NULL, status VARCHAR(20) NOT NULL,
  total_amount DECIMAL(19,2) NOT NULL, currency VARCHAR(3) NOT NULL,
  line_count INT NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
