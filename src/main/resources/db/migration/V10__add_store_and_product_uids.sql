-- Add public store_uid and product_uid UUID columns and relax legacy numeric NOT NULL constraints

-- 1. wallet_transactions
ALTER TABLE wallet_transactions ADD COLUMN IF NOT EXISTS store_uid UUID;
CREATE INDEX IF NOT EXISTS idx_wallet_transactions_store_uid ON wallet_transactions (store_uid);

-- 2. order_items
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS store_uid UUID;
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS product_uid UUID;
ALTER TABLE order_items ALTER COLUMN store_id DROP NOT NULL;
ALTER TABLE order_items ALTER COLUMN product_id DROP NOT NULL;
CREATE INDEX IF NOT EXISTS idx_order_items_store_uid ON order_items (store_uid);
CREATE INDEX IF NOT EXISTS idx_order_items_product_uid ON order_items (product_uid);

-- 3. sales_transactions
ALTER TABLE sales_transactions ADD COLUMN IF NOT EXISTS store_uid UUID;
ALTER TABLE sales_transactions ALTER COLUMN store_id DROP NOT NULL;
CREATE INDEX IF NOT EXISTS idx_sales_transactions_store_uid ON sales_transactions (store_uid);

-- 4. sale_transaction_items
ALTER TABLE sale_transaction_items ADD COLUMN IF NOT EXISTS product_uid UUID;
CREATE INDEX IF NOT EXISTS idx_sale_transaction_items_product_uid ON sale_transaction_items (product_uid);
