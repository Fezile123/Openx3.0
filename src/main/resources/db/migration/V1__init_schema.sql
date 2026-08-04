CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE accounts (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email       VARCHAR(255) NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE wallets (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id  UUID NOT NULL REFERENCES accounts(id),
    asset       VARCHAR(16) NOT NULL,
    balance     NUMERIC(20, 8) NOT NULL DEFAULT 0,
    reserved    NUMERIC(20, 8) NOT NULL DEFAULT 0,
    version     BIGINT NOT NULL DEFAULT 0,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (account_id, asset),
    CONSTRAINT balance_non_negative CHECK (balance >= 0),
    CONSTRAINT reserved_non_negative CHECK (reserved >= 0)
);

CREATE TABLE orders (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id          UUID NOT NULL REFERENCES accounts(id),
    symbol              VARCHAR(16) NOT NULL,
    side                VARCHAR(4) NOT NULL CHECK (side IN ('BUY', 'SELL')),
    type                VARCHAR(6) NOT NULL CHECK (type IN ('LIMIT', 'MARKET')),
    price               NUMERIC(20, 8),
    quantity            NUMERIC(20, 8) NOT NULL CHECK (quantity > 0),
    remaining_quantity  NUMERIC(20, 8) NOT NULL CHECK (remaining_quantity >= 0),
    status              VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    idempotency_key     VARCHAR(255) NOT NULL UNIQUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT limit_orders_have_price CHECK (type <> 'LIMIT' OR price IS NOT NULL)
);

CREATE INDEX idx_orders_symbol_status ON orders (symbol, status);
CREATE INDEX idx_orders_account ON orders (account_id);

CREATE TABLE trades (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    symbol         VARCHAR(16) NOT NULL,
    buy_order_id   UUID NOT NULL REFERENCES orders(id),
    sell_order_id  UUID NOT NULL REFERENCES orders(id),
    price          NUMERIC(20, 8) NOT NULL CHECK (price > 0),
    quantity       NUMERIC(20, 8) NOT NULL CHECK (quantity > 0),
    executed_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_trades_symbol ON trades (symbol);

CREATE TABLE ledger_entries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id      UUID NOT NULL REFERENCES accounts(id),
    asset           VARCHAR(16) NOT NULL,
    entry_type      VARCHAR(6) NOT NULL CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    amount          NUMERIC(20, 8) NOT NULL CHECK (amount > 0),
    reference_id    UUID NOT NULL,
    reference_type  VARCHAR(20) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ledger_account_asset ON ledger_entries (account_id, asset);
CREATE INDEX idx_ledger_reference ON ledger_entries (reference_id);
