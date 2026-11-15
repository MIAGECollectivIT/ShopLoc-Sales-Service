-- ============================================================================
-- Story SLE-15: Idempotency Key on Wallet Reloads
-- Prevents duplicate charges caused by network retries or rapid double-clicks
-- ============================================================================

ALTER TABLE wallet_transactions ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128);

CREATE UNIQUE INDEX IF NOT EXISTS uq_wallet_transactions_user_idempotency
ON wallet_transactions (user_id, idempotency_key);
