-- Story SLE-20 / SLE-186: Add public UUID identifier to orders for secure resource addressing

ALTER TABLE orders ADD COLUMN IF NOT EXISTS uid UUID;
UPDATE orders SET uid = gen_random_uuid() WHERE uid IS NULL;
ALTER TABLE orders ALTER COLUMN uid SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_orders_uid ON orders (uid);
CREATE INDEX IF NOT EXISTS idx_orders_uid ON orders (uid);

