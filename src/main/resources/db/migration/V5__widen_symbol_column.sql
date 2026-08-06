-- VARCHAR(16) was too tight for realistic trading pairs and test symbols.
-- Widen to a safer, still-bounded length.
ALTER TABLE orders ALTER COLUMN symbol TYPE VARCHAR(32);
ALTER TABLE trades ALTER COLUMN symbol TYPE VARCHAR(32);