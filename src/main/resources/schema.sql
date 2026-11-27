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
