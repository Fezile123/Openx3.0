ALTER TABLE orders
    ADD CONSTRAINT uq_orders_id_side UNIQUE (id, side);

ALTER TABLE trades
    ADD COLUMN buy_side  VARCHAR(4) GENERATED ALWAYS AS ('BUY')  STORED,
    ADD COLUMN sell_side VARCHAR(4) GENERATED ALWAYS AS ('SELL') STORED;

ALTER TABLE trades
    ADD CONSTRAINT fk_trades_buy_order_is_buy
        FOREIGN KEY (buy_order_id, buy_side) REFERENCES orders (id, side);

ALTER TABLE trades
    ADD CONSTRAINT fk_trades_sell_order_is_sell
        FOREIGN KEY (sell_order_id, sell_side) REFERENCES orders (id, side);
