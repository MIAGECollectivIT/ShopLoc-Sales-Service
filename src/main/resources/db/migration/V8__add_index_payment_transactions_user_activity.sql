CREATE INDEX IF NOT EXISTS idx_payment_transactions_user_status_created
ON payment_transactions (user_id, status, created_at);
