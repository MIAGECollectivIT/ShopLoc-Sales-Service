-- Add public UUID identifiers (uid) to all domain tables lacking one (orders already has uid since V4)

-- 1. wallets
ALTER TABLE wallets ADD COLUMN IF NOT EXISTS uid UUID DEFAULT gen_random_uuid();
UPDATE wallets SET uid = gen_random_uuid() WHERE uid IS NULL;
ALTER TABLE wallets ALTER COLUMN uid SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_wallets_uid ON wallets (uid);

-- 2. wallet_transactions
ALTER TABLE wallet_transactions ADD COLUMN IF NOT EXISTS uid UUID DEFAULT gen_random_uuid();
UPDATE wallet_transactions SET uid = gen_random_uuid() WHERE uid IS NULL;
ALTER TABLE wallet_transactions ALTER COLUMN uid SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_wallet_transactions_uid ON wallet_transactions (uid);

-- 3. order_items
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS uid UUID DEFAULT gen_random_uuid();
UPDATE order_items SET uid = gen_random_uuid() WHERE uid IS NULL;
ALTER TABLE order_items ALTER COLUMN uid SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_order_items_uid ON order_items (uid);

-- 4. payment_transactions
ALTER TABLE payment_transactions ADD COLUMN IF NOT EXISTS uid UUID DEFAULT gen_random_uuid();
UPDATE payment_transactions SET uid = gen_random_uuid() WHERE uid IS NULL;
ALTER TABLE payment_transactions ALTER COLUMN uid SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_transactions_uid ON payment_transactions (uid);

-- 5. sales_transactions
ALTER TABLE sales_transactions ADD COLUMN IF NOT EXISTS uid UUID DEFAULT gen_random_uuid();
UPDATE sales_transactions SET uid = gen_random_uuid() WHERE uid IS NULL;
ALTER TABLE sales_transactions ALTER COLUMN uid SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_sales_transactions_uid ON sales_transactions (uid);

-- 6. sale_transaction_items
ALTER TABLE sale_transaction_items ADD COLUMN IF NOT EXISTS uid UUID DEFAULT gen_random_uuid();
UPDATE sale_transaction_items SET uid = gen_random_uuid() WHERE uid IS NULL;
ALTER TABLE sale_transaction_items ALTER COLUMN uid SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_sale_transaction_items_uid ON sale_transaction_items (uid);
