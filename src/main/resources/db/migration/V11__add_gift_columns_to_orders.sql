-- Story SLE-41 / SLE-291: Support gift line items valued at 0.00 EUR (RG002) and points cost tracking

ALTER TABLE order_items ADD COLUMN IF NOT EXISTS item_type VARCHAR(16) NOT NULL DEFAULT 'PRODUCT';
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS points_cost INTEGER NOT NULL DEFAULT 0;

ALTER TABLE orders ADD COLUMN IF NOT EXISTS total_points_cost INTEGER NOT NULL DEFAULT 0;

ALTER TABLE orders DROP CONSTRAINT IF EXISTS chk_orders_amount;
ALTER TABLE orders ADD CONSTRAINT chk_orders_amount CHECK (total_amount >= 0);

ALTER TABLE order_items DROP CONSTRAINT IF EXISTS chk_order_items_unit_price;
ALTER TABLE order_items ADD CONSTRAINT chk_order_items_unit_price CHECK (unit_price >= 0);

ALTER TABLE order_items DROP CONSTRAINT IF EXISTS chk_order_items_total_price;
ALTER TABLE order_items ADD CONSTRAINT chk_order_items_total_price CHECK (total_price >= 0);
