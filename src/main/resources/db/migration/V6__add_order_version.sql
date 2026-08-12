-- Optimistic locking column for orders, mirroring wallets.version.
-- Prevents lost updates when concurrent trades try to modify the same
-- resting order's remaining_quantity/status at the same time.
ALTER TABLE orders ADD COLUMN version BIGINT NOT NULL DEFAULT 0;