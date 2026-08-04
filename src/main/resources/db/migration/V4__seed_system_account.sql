-- System account represents funds entering/leaving the exchange from
-- outside (deposits, withdrawals). Every deposit credits the user's
-- wallet and debits this account for the same asset/amount, and vice
-- versa for withdrawals — keeping the ledger balanced end to end.

INSERT INTO accounts (id, email) VALUES
    ('00000000-0000-0000-0000-000000000000', 'system@openex.internal');
