ALTER TABLE orders
    ADD COLUMN trading_account_id BIGINT;

UPDATE orders
SET trading_account_id = account_id
WHERE trading_account_id IS NULL;

ALTER TABLE orders
    ALTER COLUMN trading_account_id SET NOT NULL;

ALTER TABLE orders
    ADD CONSTRAINT FK_trading_account_orders
        FOREIGN KEY (trading_account_id)
        REFERENCES trading_accounts (id);

ALTER TABLE account_ledgers
    ADD CONSTRAINT FK_order_account_ledgers
        FOREIGN KEY (order_id)
        REFERENCES orders (id);
