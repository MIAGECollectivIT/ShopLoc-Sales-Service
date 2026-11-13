-- Story SLE-21: Add receipt_sent_at timestamp to ensure email idempotency
ALTER TABLE orders ADD COLUMN IF NOT EXISTS receipt_sent_at TIMESTAMP WITH TIME ZONE;
